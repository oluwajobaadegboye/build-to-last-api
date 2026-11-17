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
        String n4 = ReflectionTestUtils.invokeMethod(f, "normalizePath", "/api/v1/admin/participants/ms-002/payment-status");
        assertThat(n4).isEqualTo("/api/v1/admin/participants/{code}/payment-status");
        String n5 = ReflectionTestUtils.invokeMethod(f, "normalizePath", "/api/v1/participant/BTL-7KQ4M/board-run");
        assertThat(n5).isEqualTo("/api/v1/participant/{code}/board-run");
        String n6 = ReflectionTestUtils.invokeMethod(f, "normalizePath", "/api/v1/admin/participants/BTL-7KQ4M/check-in");
        assertThat(n6).isEqualTo("/api/v1/admin/participants/{code}/check-in");
    }

    @Test
    void wrong_code_limit_covers_only_public_code_lookups() {
        assertThat(RateLimitFilter.isCodeLookup("GET", "/api/v1/participant-status")).isTrue();
        assertThat(RateLimitFilter.isCodeLookup("GET", "/api/v1/driver/me")).isTrue();
        assertThat(RateLimitFilter.isCodeLookup("PATCH", "/api/v1/my-details")).isTrue();
        assertThat(RateLimitFilter.isCodeLookup("PATCH", "/api/v1/participant/{code}/board-run")).isTrue();
        assertThat(RateLimitFilter.isCodeLookup("GET", "/api/v1/programs/yc26")).isFalse();
        assertThat(RateLimitFilter.isCodeLookup("GET", "/api/v1/admin/participants/{code}")).isFalse();
    }

    @Test
    void wrong_codes_are_counted_per_device_on_a_shared_network() {
        var phone1 = new org.springframework.mock.web.MockHttpServletRequest();
        phone1.addHeader("X-Device-Id", "7f3c1d2e-aaaa-bbbb-cccc-123456789abc");
        var phone2 = new org.springframework.mock.web.MockHttpServletRequest();
        phone2.addHeader("X-Device-Id", "0a9b8c7d-aaaa-bbbb-cccc-123456789abc");
        var junk = new org.springframework.mock.web.MockHttpServletRequest();
        junk.addHeader("X-Device-Id", "<script>");
        String k1 = ReflectionTestUtils.invokeMethod(RateLimitFilter.class, "deviceKey", phone1, "1.2.3.4");
        String k2 = ReflectionTestUtils.invokeMethod(RateLimitFilter.class, "deviceKey", phone2, "1.2.3.4");
        String k3 = ReflectionTestUtils.invokeMethod(RateLimitFilter.class, "deviceKey", junk, "1.2.3.4");
        assertThat(k1).isNotEqualTo(k2).startsWith("1.2.3.4|");
        assertThat(k3).isEqualTo("1.2.3.4|-");
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
