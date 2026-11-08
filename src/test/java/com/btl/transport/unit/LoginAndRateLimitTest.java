package com.btl.transport.unit;

import com.btl.transport.admin.*;
import com.btl.transport.config.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Per-account login limit and rate-limit path grouping. */
class LoginAndRateLimitTest {

    @Test
    void rate_limit_groups_codes_but_keeps_named_endpoints() {
        RateLimitFilter f = new RateLimitFilter();
        String n1 = ReflectionTestUtils.invokeMethod(f, "normalizePath", "/api/v1/update-flight");
        String n2 = ReflectionTestUtils.invokeMethod(f, "normalizePath", "/api/v1/admin/participants/MS-001/check-in");
        String n3 = ReflectionTestUtils.invokeMethod(f, "normalizePath", "/api/v1/admin/participants/BTL-032");
        assertThat(n1).isEqualTo("/api/v1/update-flight");
        assertThat(n2).isEqualTo("/api/v1/admin/participants/{code}/check-in");
        assertThat(n3).isEqualTo("/api/v1/admin/participants/{code}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ten_failures_lock_only_that_account_and_old_entries_are_swept() {
        AdminUserRepository users = mock(AdminUserRepository.class);
        when(users.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        AdminAuthController c = new AdminAuthController(new AdminProperties(), mock(JwtService.class), users,
            mock(ProgramAdminRepository.class), mock(AdminAccess.class));

        for (int i = 0; i < 10; i++)
            assertThat(c.login(new AdminAuthController.LoginRequest("victim", "bad")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(c.login(new AdminAuthController.LoginRequest("victim", "bad")).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(c.login(new AdminAuthController.LoginRequest("someone-else", "bad")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Deque<Instant>> failures = (Map<String, Deque<Instant>>) ReflectionTestUtils.getField(c, "failures");
        failures.get("someone-else").clear();
        failures.get("someone-else").add(Instant.now().minusSeconds(3600));   // older than the window
        ReflectionTestUtils.invokeMethod(c, "pruneFailures");
        assertThat(failures).containsKey("victim").doesNotContainKey("someone-else");
    }
}
