package org.programhub.config;

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
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /*
     * Wrong codes. The server only sees a network's public address: every phone on a venue's Wi-Fi (or on the
     * same mobile carrier gateway) shows up as one address. So wrong codes are limited per device — the app
     * sends a random id it keeps on the phone — with a much higher ceiling per network that only a guessing
     * script would reach (it could make up a new device id each time, but not a new network).
     */
    private final Map<String, Bucket> deviceMisses = new ConcurrentHashMap<>();
    private final Map<String, Bucket> networkMisses = new ConcurrentHashMap<>();
    public static final int WRONG_CODES_PER_DEVICE = 10;
    public static final int WRONG_CODES_PER_NETWORK = 100;
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

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

        // Pages opened with a code alone: limit wrong codes so codes can't be found by guessing.
        boolean codeLookup = isCodeLookup(request.getMethod(), path);
        Bucket deviceMiss = null, networkMiss = null;
        String device = null;
        if (codeLookup) {
            device = deviceKey(request, ip);
            deviceMiss  = deviceMisses.computeIfAbsent(device, k -> Bucket.builder().addLimit(perHour(WRONG_CODES_PER_DEVICE)).build());
            networkMiss = networkMisses.computeIfAbsent(ip, k -> Bucket.builder().addLimit(perHour(WRONG_CODES_PER_NETWORK)).build());
            if (deviceMiss.getAvailableTokens() <= 0 || networkMiss.getAvailableTokens() <= 0) {
                log.warn("Too many wrong codes — ip={} device={} path={}", ip, device, path);
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Retry-After", "600");
                response.getWriter().write("{\"error\":\"Too many codes that didn't match. Please wait a few minutes and try again, or ask the organisers for your code.\",\"status\":429}");
                return;
            }
        }
        // Code lookups are counted per device too, so a busy venue on one Wi-Fi isn't slowed down.
        String key  = request.getMethod() + ":" + path + ":" + ip + (device != null ? ":" + device : "");

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(path, request.getMethod()));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            if (deviceMiss != null && response.getStatus() == 404) { deviceMiss.tryConsume(1); networkMiss.tryConsume(1); }
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
            // Walk-up sign-ups at a venue share one network address.
            case "POST:/api/v1/register"      -> perHour(60);
            // Per network: generous, since a whole venue can share one address. Per-account limits live in AdminAuthController.
            case "POST:/api/v1/admin/login"   -> per(120, Duration.ofMinutes(15));
            case "POST:/api/v1/auth/signup"   -> perHour(5);
            case "POST:/api/v1/auth/forgot-password" -> perHour(10);
            case "POST:/api/v1/auth/reset-password"  -> perHour(30);
            case "POST:/api/v1/admin/programs" -> perHour(20);
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

    /** The app's device id (X-Device-Id) on this network; requests without one count as one device per network. */
    static String deviceKey(HttpServletRequest request, String ip) {
        String id = request.getHeader("X-Device-Id");
        return ip + "|" + (id != null && DEVICE_ID.matcher(id).matches() ? id : "-");
    }

    /** Public requests that open someone's details with a registration or driver code. */
    public static boolean isCodeLookup(String method, String path) {
        return ("GET".equals(method) && (path.equals("/api/v1/participant-status") || path.equals("/api/v1/driver/me")))
            || ("PATCH".equals(method) && (path.equals("/api/v1/my-details")
                || path.startsWith("/api/v1/participant/") || path.startsWith("/api/v1/driver/run/")));
    }

    private boolean isExempt(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/v1/twilio-webhook");
    }

    private String clientIp(HttpServletRequest request) {
        // The load balancer appends the address it saw to X-Forwarded-For, so the LAST entry is the real
        // client. The first entry is whatever the caller sent and could be faked to dodge the limits.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = hops[hops.length - 1].trim();
            if (!last.isEmpty()) return last;
        }
        return request.getRemoteAddr();
    }

    private String normalizePath(String uri) {
        // Collapse path variables so /admin/participants/BTL-001 and /admin/participants/BTL-002
        // share one bucket rather than each getting their own.
        return uri
            // Registration codes are letters, a dash and a digit then letters/digits (MS-001, BTL-7KQ4M, any
            // case); words like /update-flight or /check-in have no digit after the dash, so keep their own limits.
            .replaceAll("/[A-Za-z]{1,8}\\d*-\\d[A-Za-z0-9]*(?=/|$)", "/{code}")
            .replaceAll("/\\d+", "/{id}");
    }
}
