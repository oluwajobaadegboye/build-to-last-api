package com.btl.transport.unit;

import com.btl.transport.admin.*;
import com.btl.transport.notification.SendGridService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Forgot password?" for admins: who gets a link, what the link can do, and what it can't. */
class PasswordResetTest {

    AdminUserRepository users;
    PasswordResetRepository resets;
    SendGridService mail;
    AdminAuthController auth;
    PasswordResetController c;
    AdminUser ada;

    @BeforeEach
    void setUp() {
        users = mock(AdminUserRepository.class);
        resets = mock(PasswordResetRepository.class);
        mail = mock(SendGridService.class);
        auth = new AdminAuthController(new AdminProperties(), mock(JwtService.class), users,
            mock(ProgramAdminRepository.class), mock(AdminAccess.class));
        c = new PasswordResetController(users, resets, auth, mail);
        ReflectionTestUtils.setField(c, "frontendBaseUrl", "https://app.example.org/");
        ada = AdminUser.builder().id(7).username("ada").email("ada@example.org").emailVerified(true)
            .passwordHash(new BCryptPasswordEncoder().encode("old-password")).build();
        when(users.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(users.findByUsernameIgnoreCase("ada")).thenReturn(Optional.of(ada));
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(List.of());
        when(users.findById(7)).thenReturn(Optional.of(ada));
        when(resets.findFirstByAdminUserIdOrderByCreatedAtDesc(any())).thenReturn(Optional.empty());
    }

    /** Requests a link for "ada" and returns the raw token from the email. */
    private String requestLink() {
        c.forgot(new PasswordResetController.ForgotRequest("ada"));
        ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
        verify(mail, timeout(2000)).sendHtmlEmail(eq("ada@example.org"), any(), eq("Reset your ProgramHub password"), anyString(), plain.capture());
        Matcher m = Pattern.compile("https://app\\.example\\.org/admin/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(plain.getValue());
        assertThat(m.find()).as("email contains the reset link").isTrue();
        return m.group(1);
    }

    private PasswordReset stored() {
        ArgumentCaptor<PasswordReset> saved = ArgumentCaptor.forClass(PasswordReset.class);
        verify(resets).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void same_answer_for_known_and_unknown_accounts() {
        var known = c.forgot(new PasswordResetController.ForgotRequest("ada"));
        var unknown = c.forgot(new PasswordResetController.ForgotRequest("nobody"));
        var empty = c.forgot(null);
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unknown.getBody()).isEqualTo(known.getBody());
        assertThat(empty.getBody()).isEqualTo(known.getBody());
    }

    @Test
    void link_is_emailed_and_only_its_hash_is_stored() {
        String token = requestLink();
        PasswordReset r = stored();
        assertThat(r.getAdminUserId()).isEqualTo(7);
        assertThat(r.getTokenHash()).isNotEqualTo(token).hasSize(64);
        assertThat(r.getExpiresAt()).isBefore(OffsetDateTime.now().plusMinutes(31)).isAfter(OffsetDateTime.now().plusMinutes(29));
        verify(resets).retireOpenLinks(eq(7), any());  // older links stop working
    }

    @Test
    void found_by_email_too() {
        when(users.findByEmailIgnoreCase("ADA@example.org")).thenReturn(List.of(ada));
        c.forgot(new PasswordResetController.ForgotRequest("ADA@example.org"));
        verify(mail, timeout(2000)).sendHtmlEmail(eq("ada@example.org"), any(), anyString(), anyString(), anyString());
    }

    @Test
    void no_email_for_unconfirmed_or_missing_address_or_too_soon() {
        ada.setEmailVerified(false);
        c.forgot(new PasswordResetController.ForgotRequest("ada"));
        ada.setEmailVerified(true);
        ada.setEmail(null);
        c.forgot(new PasswordResetController.ForgotRequest("ada"));
        ada.setEmail("ada@example.org");
        when(resets.findFirstByAdminUserIdOrderByCreatedAtDesc(7)).thenReturn(Optional.of(
            PasswordReset.builder().createdAt(OffsetDateTime.now().minusSeconds(30)).build()));
        c.forgot(new PasswordResetController.ForgotRequest("ada"));
        verify(resets, never()).save(any());
        verify(mail, after(300).never()).sendHtmlEmail(any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void valid_link_sets_password_retires_links_and_unlocks_sign_in() {
        String token = requestLink();
        PasswordReset r = stored();
        when(resets.findByTokenHash(r.getTokenHash())).thenReturn(Optional.of(r));

        // Locked out from guessing before the reset.
        for (int i = 0; i < 10; i++) auth.login(new AdminAuthController.LoginRequest("ada", "wrong"));
        assertThat(auth.login(new AdminAuthController.LoginRequest("ada", "wrong")).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        var check = c.check(new PasswordResetController.TokenRequest(token));
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(check.getBody()).containsEntry("username", "ada");

        assertThat(c.reset(new PasswordResetController.ResetRequest(token, "short")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var res = c.reset(new PasswordResetController.ResetRequest(token, "brand-new-pass"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new BCryptPasswordEncoder().matches("brand-new-pass", ada.getPasswordHash())).isTrue();
        verify(resets, times(2)).retireOpenLinks(eq(7), any());  // once when sent, once when used
        assertThat((Map<String, ?>) ReflectionTestUtils.getField(auth, "failures")).doesNotContainKey("ada");
    }

    @Test
    void used_expired_or_unknown_links_are_refused() {
        String token = requestLink();
        PasswordReset r = stored();
        String before = ada.getPasswordHash();

        r.setUsedAt(OffsetDateTime.now());
        when(resets.findByTokenHash(r.getTokenHash())).thenReturn(Optional.of(r));
        assertThat(c.reset(new PasswordResetController.ResetRequest(token, "brand-new-pass")).getStatusCode()).isEqualTo(HttpStatus.GONE);

        r.setUsedAt(null);
        r.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        assertThat(c.reset(new PasswordResetController.ResetRequest(token, "brand-new-pass")).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(c.check(new PasswordResetController.TokenRequest(token)).getStatusCode()).isEqualTo(HttpStatus.GONE);

        assertThat(c.reset(new PasswordResetController.ResetRequest("made-up", "brand-new-pass")).getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(ada.getPasswordHash()).isEqualTo(before);
    }
}
