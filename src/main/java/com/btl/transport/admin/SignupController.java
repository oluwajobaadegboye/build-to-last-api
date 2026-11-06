package com.btl.transport.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.regex.Pattern;

/** Public self-service sign-up: anyone can create an admin account, then create their own programs. */
@Tag(name = "Auth", description = "Public account sign-up")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SignupController {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._@-]{3,64}$");
    private static final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    private final AdminUserRepository adminUserRepository;
    private final AdminProperties adminProperties;
    private final JwtService jwtService;

    public record SignupRequest(
        String username,
        String password,
        @JsonProperty("display_name") String displayName,
        String email
    ) {}

    @Operation(summary = "Sign up", description = "Creates an admin account with no programs and returns a JWT. The user then creates programs, becoming their owner.")
    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        if (!USERNAME.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Username must be 3–64 characters: letters, numbers, . _ @ -"));
        }
        if (req.password() == null || req.password().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }
        boolean reserved = adminProperties.getUsers().stream()
            .anyMatch(u -> username.equalsIgnoreCase(u.getUsername()));
        if (reserved || adminUserRepository.findAll().stream()
                .anyMatch(u -> username.equalsIgnoreCase(u.getUsername()))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
        }
        AdminUser user = AdminUser.builder()
            .username(username)
            .passwordHash(bcrypt.encode(req.password()))
            .displayName(blankToNull(req.displayName()))
            .email(blankToNull(req.email()))
            .role("FULL")
            .createdAt(OffsetDateTime.now())
            .build();
        try {
            adminUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
        }
        String token = jwtService.generateToken(user.getUsername(), null, "FULL");
        return ResponseEntity.ok(Map.of(
            "token", token,
            "expires_at", jwtService.getExpiry(token).toString()
        ));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
