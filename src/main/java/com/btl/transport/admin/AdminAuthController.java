package com.btl.transport.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminProperties props;
    private final JwtService jwtService;

    private static final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        AdminProperties.AdminUser match = props.getUsers().stream()
            .filter(u -> u.getUsername() != null && u.getUsername().equals(body.username()))
            .findFirst()
            .orElse(null);

        if (match == null || match.getPasswordHash() == null
                || !bcrypt.matches(body.password(), match.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password"));
        }

        String token = jwtService.generateToken(body.username());
        Instant expiry = jwtService.getExpiry(token);
        return ResponseEntity.ok(Map.of(
            "token", token,
            "expires_at", expiry.toString()
        ));
    }
}
