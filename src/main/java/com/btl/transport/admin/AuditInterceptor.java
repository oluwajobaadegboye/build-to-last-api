package com.btl.transport.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records every successful admin change (POST/PUT/PATCH/DELETE) in admin_audit_log, in the same request —
 * no extra calls from the browser. Request bodies are never stored (they can hold passwords).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuditInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String P = "^/api/v1/admin";

    private record Label(String method, Pattern path, String action) {}

    /** Readable names for common changes; anything else is logged as "METHOD /path". Group 1 = target. */
    private static final List<Label> LABELS = List.of(
        new Label("POST", Pattern.compile(P + "/participants/([^/]+)/payments$"), "Recorded a payment"),
        new Label("DELETE", Pattern.compile(P + "/participants/([^/]+)/payments/[^/]+$"), "Deleted a payment"),
        new Label("PATCH", Pattern.compile(P + "/participants/([^/]+)/payment-status$"), "Changed payment status"),
        new Label("PUT", Pattern.compile(P + "/participants/([^/]+)/souvenirs/[^/]+$"), "Updated a souvenir hand-out"),
        new Label("PUT", Pattern.compile(P + "/participants/([^/]+)/check-in$"), "Changed check-in"),
        new Label("DELETE", Pattern.compile(P + "/participants?/([^/]+)$"), "Deleted a participant"),
        new Label("PATCH", Pattern.compile(P + "/participants?/([^/]+)$"), "Edited a participant"),
        new Label("PUT", Pattern.compile(P + "/participants?/([^/]+)$"), "Edited a participant"),
        new Label("POST", Pattern.compile(P + "/participants?/([^/]+)/resend-code$"), "Resent a registration code"),
        new Label("POST", Pattern.compile(P + "/users$()"), "Added an admin user"),
        new Label("PUT", Pattern.compile(P + "/users/([^/]+)$"), "Edited an admin user"),
        new Label("DELETE", Pattern.compile(P + "/users/([^/]+)$"), "Removed an admin user"),
        new Label("PATCH", Pattern.compile(P + "/programs/[^/]+/policies$()"), "Changed program policies"),
        new Label("PATCH", Pattern.compile(P + "/programs/([^/]+)$"), "Changed program settings"),
        new Label("POST", Pattern.compile(P + "/payments/reminders$()"), "Sent payment reminders"),
        new Label("*", Pattern.compile(P + "/souvenirs(?:/([^/]+))?.*$"), "Changed souvenir items"),
        new Label("*", Pattern.compile(P + "/registration-fields(?:/([^/]+))?.*$"), "Changed registration questions"),
        new Label("*", Pattern.compile(P + "/room-assignments(?:/([^/]+))?.*$"), "Changed room assignments"),
        new Label("*", Pattern.compile(P + "/runs?(?:/([^/]+))?.*$"), "Changed trips")
    );

    private static final Set<String> SKIP = Set.of("/api/v1/admin/login", "/api/v1/admin/change-password");

    private final AuditLogRepository repository;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/admin/**");
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        String method = req.getMethod();
        String path = req.getRequestURI();
        if (!MUTATIONS.contains(method) || res.getStatus() >= 400 || ex != null || SKIP.contains(path)) return;
        Object user = req.getAttribute(AdminAccess.ATTR_USERNAME);
        if (user == null) return;
        try {
            String action = method + " " + path.replaceFirst("^/api/v1/admin", "");
            String target = null;
            for (Label l : LABELS) {
                if (!l.method().equals("*") && !l.method().equals(method)) continue;
                Matcher m = l.path().matcher(path);
                if (m.matches()) {
                    action = l.action();
                    target = m.groupCount() >= 1 && m.group(1) != null && !m.group(1).isBlank() ? m.group(1) : null;
                    break;
                }
            }
            repository.save(AuditLog.builder()
                .programId((String) req.getAttribute(AdminAccess.ATTR_PROGRAM_ID))
                .username(String.valueOf(user))
                .action(action)
                .target(target)
                .method(method)
                .path(path.length() > 300 ? path.substring(0, 300) : path)
                .build());
        } catch (Exception e) {
            log.warn("Audit log write failed for {} {}", method, path, e);  // never fail the user's change
        }
    }
}
