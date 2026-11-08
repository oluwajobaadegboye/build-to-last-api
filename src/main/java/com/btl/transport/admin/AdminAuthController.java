package com.btl.transport.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin Auth", description = "Admin authentication — login and password management")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminProperties props;
    private final JwtService jwtService;
    private final AdminUserRepository adminUserRepository;
    private final ProgramAdminRepository programAdminRepository;
    private final AdminAccess adminAccess;

    private static final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    /**
     * Failed sign-ins are limited per account (10 per 15 minutes), not per network, so people sharing
     * a venue's Wi-Fi never lock each other out while passwords stay protected from guessing.
     */
    static final int MAX_FAILURES = 10;
    static final java.time.Duration FAILURE_WINDOW = java.time.Duration.ofMinutes(15);
    private final java.util.Map<String, java.util.Deque<Instant>> failures = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.Deque<Instant> recentFailures(String username) {
        java.util.Deque<Instant> q = failures.computeIfAbsent(username, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        Instant cutoff = Instant.now().minus(FAILURE_WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
        return q;
    }

    public record LoginRequest(String username, String password) {}

    @Operation(summary = "Admin login", description = "Authenticate with username and password to receive a JWT bearer token")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        String key = body.username() == null ? "" : body.username().trim().toLowerCase(java.util.Locale.ROOT);
        java.util.Deque<Instant> recent = recentFailures(key);
        if (recent.size() >= MAX_FAILURES) {
            long mins = Math.max(1, java.time.Duration.between(Instant.now(), recent.peekFirst().plus(FAILURE_WINDOW)).toMinutes() + 1);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many failed sign-ins for this account — try again in " + mins + " min"));
        }
        ResponseEntity<Map<String, Object>> res = attemptLogin(body);
        if (res.getStatusCode() == HttpStatus.UNAUTHORIZED) recent.addLast(Instant.now());
        else if (res.getStatusCode().is2xxSuccessful()) failures.remove(key);
        return res;
    }

    private ResponseEntity<Map<String, Object>> attemptLogin(LoginRequest body) {
        // 1. Try DB-stored admin user (look up by username alone — no program_id required)
        AdminUser dbUser = adminUserRepository.findByUsernameIgnoreCase(body.username()).orElse(null);
        if (dbUser != null && bcrypt.matches(body.password(), dbUser.getPasswordHash())) {
            if (Boolean.FALSE.equals(dbUser.getEmailVerified())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "email_not_verified",
                    "message", "Confirm your email first — we sent a link to " + maskEmail(dbUser.getEmail())));
            }
            // One program → token scoped to it (lands straight on dashboard); several/none → picker.
            List<ProgramAdmin> memberships = programAdminRepository.findByAdminUserId(dbUser.getId());
            String tokenProgram = memberships.size() == 1 ? memberships.get(0).getProgramId() : null;
            String tokenRole    = memberships.size() == 1 ? memberships.get(0).getRole() : dbUser.getRole();
            String token = jwtService.generateToken(dbUser.getUsername(), tokenProgram, tokenRole);
            return ResponseEntity.ok(Map.of(
                "token", token,
                "expires_at", jwtService.getExpiry(token).toString()
            ));
        }

        // 2. Fall back to YAML-configured super-admins
        AdminProperties.AdminUser match = props.getUsers().stream()
            .filter(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(body.username()))
            .findFirst()
            .orElse(null);

        if (match == null || match.getPasswordHash() == null
                || !bcrypt.matches(body.password(), match.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password"));
        }

        String token = jwtService.generateToken(body.username(), null);
        Instant expiry = jwtService.getExpiry(token);
        return ResponseEntity.ok(Map.of(
            "token", token,
            "expires_at", expiry.toString()
        ));
    }

    @Operation(summary = "Current admin", description = "Returns the signed-in admin and the programs they can manage, with their role in each")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader("Authorization") String authHeader) {
        String username = jwtService.validateAndExtractUsername(authHeader.substring(7));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("username", username);
        out.put("is_super", adminAccess.isSuper());
        List<Map<String, Object>> programs = new ArrayList<>();
        Integer userId = adminAccess.currentUserId();
        if (userId != null) {
            adminUserRepository.findById(userId).ifPresent(u -> out.put("display_name", u.getDisplayName()));
            for (ProgramAdmin pa : programAdminRepository.findByAdminUserId(userId)) {
                programs.add(Map.of("program_id", pa.getProgramId(), "role", pa.getRole(),
                    "permissions", AdminPermissions.parse(pa.getRole())));
            }
        }
        out.put("programs", programs);
        return ResponseEntity.ok(out);
    }

    public record ChangePasswordRequest(
        @JsonProperty("old_password") String oldPassword,
        @JsonProperty("new_password") String newPassword
    ) {}

    @Operation(summary = "Change password", description = "Change the authenticated admin user's password. Requires a valid JWT and the current password")
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest body) {
        if (body.oldPassword() == null || body.newPassword() == null || body.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters"));
        }
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String username = jwtService.validateAndExtractUsername(token);
        AdminUser dbUser = adminUserRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (dbUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Password change is not supported for this account type"));
        }
        if (!bcrypt.matches(body.oldPassword(), dbUser.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Current password is incorrect"));
        }
        dbUser.setPasswordHash(bcrypt.encode(body.newPassword()));
        adminUserRepository.save(dbUser);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "your email";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        return (name.length() <= 2 ? name.charAt(0) + "*" : name.substring(0, 2) + "***") + "@" + parts[1];
    }
}
