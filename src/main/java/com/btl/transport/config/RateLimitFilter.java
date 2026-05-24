package com.btl.transport.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled || isExempt(request) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String ip   = clientIp(request);
        String path = normalizePath(request.getRequestURI());
        String key  = request.getMethod() + ":" + path + ":" + ip;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(path, request.getMethod()));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long retryAfterSecs = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("Rate limit exceeded — ip={} path={} method={}", ip, path, request.getMethod());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSecs));
            response.getWriter().write(
                "{\"error\":\"Too many requests - please slow down\",\"status\":429,\"timestamp\":\"" +
                OffsetDateTime.now() + "\"}"
            );
        }
    }

    // ── Bucket factory ─────────────────────────────────────────────────────

    private Bucket createBucket(String path, String method) {
        Bandwidth limit = switch (method + ":" + path) {
            case "POST:/api/v1/register"      -> perHour(5);
            case "POST:/api/v1/admin/login"   -> per(10, Duration.ofMinutes(15));
            case "POST:/api/v1/update-flight" -> perHour(20);
            case "GET:/api/v1/participant-status" -> perMinute(30);
            default -> perMinute(60);
        };
        return Bucket.builder().addLimit(limit).build();
    }

    private static Bandwidth perMinute(long tokens) {
        return Bandwidth.builder().capacity(tokens).refillGreedy(tokens, Duration.ofMinutes(1)).build();
    }

    private static Bandwidth perHour(long tokens) {
        return Bandwidth.builder().capacity(tokens).refillGreedy(tokens, Duration.ofHours(1)).build();
    }

    private static Bandwidth per(long tokens, Duration duration) {
        return Bandwidth.builder().capacity(tokens).refillGreedy(tokens, duration).build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isExempt(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/v1/twilio-webhook");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalizePath(String uri) {
        // Collapse path variables so /admin/participants/BTL-001 and /admin/participants/BTL-002
        // share one bucket rather than each getting their own.
        return uri
            .replaceAll("/BTL-[A-Z0-9]+", "/{btlCode}")
            .replaceAll("/\\d+", "/{id}");
    }
}
