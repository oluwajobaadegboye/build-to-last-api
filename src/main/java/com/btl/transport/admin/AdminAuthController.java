package com.btl.transport.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminProperties props;
    private final JwtService jwtService;
    private final AdminUserRepository adminUserRepository;

    private static final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        // 1. Try DB-stored admin user (look up by username alone — no program_id required)
        AdminUser dbUser = adminUserRepository.findByUsername(body.username()).orElse(null);
        if (dbUser != null && bcrypt.matches(body.password(), dbUser.getPasswordHash())) {
            String token = jwtService.generateToken(body.username(), dbUser.getProgramId());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "expires_at", jwtService.getExpiry(token).toString()
            ));
        }

        // 2. Fall back to YAML-configured super-admins
        AdminProperties.AdminUser match = props.getUsers().stream()
            .filter(u -> u.getUsername() != null && u.getUsername().equals(body.username()))
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

    public record ChangePasswordRequest(
        @JsonProperty("old_password") String oldPassword,
        @JsonProperty("new_password") String newPassword
    ) {}

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest body) {
        if (body.oldPassword() == null || body.newPassword() == null || body.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters"));
        }
        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String username = jwtService.validateAndExtractUsername(token);
        AdminUser dbUser = adminUserRepository.findByUsername(username).orElse(null);
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
}
