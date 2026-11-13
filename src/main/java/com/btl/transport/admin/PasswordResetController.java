package com.btl.transport.admin;

import com.btl.transport.notification.EmailLayout;
import com.btl.transport.notification.SendGridService;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * "Forgot password?" for admin accounts: emails a one-time link to the account's confirmed address.
 * Public endpoints under /api/v1/auth (outside the admin JWT filter).
 */
@Slf4j
@Tag(name = "Auth", description = "Public account sign-up and password reset")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    /** How long an emailed link works. */
    static final Duration LINK_LIFETIME = Duration.ofMinutes(30);
    /** At most one email per account in this gap, so the form can't be used to flood an inbox. */
    static final Duration RESEND_GAP = Duration.ofMinutes(2);
    static final int MIN_PASSWORD = 8;

    private static final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminUserRepository adminUserRepository;
    private final PasswordResetRepository resetRepository;
    private final AdminAuthController authController;
    private final SendGridService sendGridService;

    @Value("${btl.frontend-base-url}")
    private String frontendBaseUrl;

    public record ForgotRequest(String identifier) {}
    public record TokenRequest(String token) {}
    public record ResetRequest(String token, @JsonProperty("new_password") String newPassword) {}

    @Operation(summary = "Forgot password",
        description = "Emails a one-time reset link to the confirmed email of the account matching the username or email. Always answers the same way, so it can't be used to find accounts.")
    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> forgot(@RequestBody(required = false) ForgotRequest req) {
        Map<String, Object> ok = Map.of("success", true);
        String id = req == null || req.identifier() == null ? "" : req.identifier().trim();
        if (id.isEmpty() || id.length() > 254) return ResponseEntity.ok(ok);

        List<AdminUser> accounts = new ArrayList<>();
        adminUserRepository.findByUsernameIgnoreCase(id).ifPresent(accounts::add);
        // An email can belong to more than one account; each gets its own link naming its username.
        if (accounts.isEmpty() && id.contains("@")) accounts.addAll(adminUserRepository.findByEmailIgnoreCase(id));

        OffsetDateTime now = OffsetDateTime.now();
        for (AdminUser u : accounts) {
            if (u.getEmail() == null || u.getEmail().isBlank() || !Boolean.TRUE.equals(u.getEmailVerified())) continue;
            boolean tooSoon = resetRepository.findFirstByAdminUserIdOrderByCreatedAtDesc(u.getId())
                .map(r -> r.getCreatedAt().isAfter(now.minus(RESEND_GAP)))
                .orElse(false);
            if (tooSoon) continue;

            resetRepository.retireOpenLinks(u.getId(), now);  // only the newest link works
            String token = newToken();
            resetRepository.save(PasswordReset.builder()
                .adminUserId(u.getId())
                .tokenHash(hash(token))
                .createdAt(now)
                .expiresAt(now.plus(LINK_LIFETIME))
                .build());
            // Sent in the background so the response takes the same time whether or not an account matched.
            String to = u.getEmail(), name = u.getDisplayName(), username = u.getUsername();
            CompletableFuture.runAsync(() -> {
                try { sendResetEmail(to, name, username, token); }
                catch (Exception e) { log.error("Password reset email to admin {} failed: {}", username, e.getMessage()); }
            });
        }
        return ResponseEntity.ok(ok);
    }

    @Operation(summary = "Check a reset link", description = "Says whether a reset link still works, and for which username")
    @PostMapping("/reset-password/check")
    public ResponseEntity<Map<String, Object>> check(@RequestBody(required = false) TokenRequest req) {
        PasswordReset r = openLink(req == null ? null : req.token());
        if (r == null) return invalidLink();
        AdminUser u = adminUserRepository.findById(r.getAdminUserId()).orElse(null);
        if (u == null) return invalidLink();
        return ResponseEntity.ok(Map.of("valid", true, "username", u.getUsername()));
    }

    @Operation(summary = "Reset password", description = "Sets a new password using an emailed reset link. The link stops working afterwards.")
    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<Map<String, Object>> reset(@RequestBody(required = false) ResetRequest req) {
        String pw = req == null ? null : req.newPassword();
        if (pw == null || pw.length() < MIN_PASSWORD) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least " + MIN_PASSWORD + " characters"));
        }
        if (pw.length() > 72) {  // bcrypt ignores anything past 72 bytes
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at most 72 characters"));
        }
        PasswordReset r = openLink(req.token());
        if (r == null) return invalidLink();
        AdminUser u = adminUserRepository.findById(r.getAdminUserId()).orElse(null);
        if (u == null) return invalidLink();

        u.setPasswordHash(bcrypt.encode(pw));
        adminUserRepository.save(u);
        resetRepository.retireOpenLinks(u.getId(), OffsetDateTime.now());
        authController.clearFailures(u.getUsername());
        log.info("Admin {} reset their password with an emailed link", u.getUsername());
        return ResponseEntity.ok(Map.of("success", true, "username", u.getUsername()));
    }

    /** Daily tidy-up of links that expired more than a day ago. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        resetRepository.deleteExpiredBefore(OffsetDateTime.now().minusDays(1));
    }

    private PasswordReset openLink(String token) {
        if (token == null || token.isBlank() || token.length() > 200) return null;
        return resetRepository.findByTokenHash(hash(token.trim()))
            .filter(r -> r.getUsedAt() == null && r.getExpiresAt().isAfter(OffsetDateTime.now()))
            .orElse(null);
    }

    private static ResponseEntity<Map<String, Object>> invalidLink() {
        return ResponseEntity.status(HttpStatus.GONE)
            .body(Map.of("error", "This link has expired or was already used. Request a new one from the sign-in page."));
    }

    private void sendResetEmail(String to, String name, String username, String token) {
        String link = frontendBaseUrl.replaceAll("/+$", "") + "/admin/reset-password?token=" + token;
        String who = name != null && !name.isBlank() ? name : username;
        long mins = LINK_LIFETIME.toMinutes();
        String plain = "Hi " + who + ",\n\n"
            + "We received a request to reset the password for your ProgramHub account (username: " + username + ").\n\n"
            + "Choose a new password here (the link works once, for " + mins + " minutes):\n\n" + link + "\n\n"
            + "If you didn't ask for this, you can ignore this email. Your password won't change.";
        String html = EmailLayout.forPlatform()
            .title("Reset your password")
            .preheader("Choose a new password for your ProgramHub account")
            .greeting(who)
            .paragraph("We received a request to reset the password for your ProgramHub account.")
            .details(null, List.<String[]>of(EmailLayout.row("Username", username)))
            .button("Choose a new password", link)
            .note("This link works once and expires in " + mins + " minutes. If you didn't ask for this, you can ignore this email. Your password won't change.")
            .html();
        sendGridService.sendHtmlEmail(to, name, "Reset your ProgramHub password", html, plain);
    }

    static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
