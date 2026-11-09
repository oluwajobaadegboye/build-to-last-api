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
    private final com.btl.transport.notification.SendGridService sendGridService;

    @org.springframework.beans.factory.annotation.Value("${btl.frontend-base-url}")
    private String frontendBaseUrl;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final java.time.Duration RESEND_GAP = java.time.Duration.ofMinutes(2);
    /** Verification links stop working after this. */
    private static final java.time.Duration LINK_LIFETIME = java.time.Duration.ofHours(48);
    /** Unconfirmed sign-ups older than this give their username back. */
    private static final java.time.Duration UNVERIFIED_HOLD = java.time.Duration.ofDays(7);

    public record SignupRequest(
        String username,
        String password,
        @JsonProperty("display_name") String displayName,
        String email
    ) {}

    @Operation(summary = "Sign up", description = "Creates an admin account and emails a verification link. The account can sign in (and create programs) once the email is verified.")
    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest req) {
        String username = req.username() == null ? "" : req.username().trim();
        if (!USERNAME.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Username must be 3–64 characters: letters, numbers, . _ @ -"));
        }
        String email = req.email() == null ? "" : req.email().trim();
        if (!EMAIL.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid email — we'll send a link to confirm it"));
        }
        if (req.password() == null || req.password().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }
        // Free the username if an earlier sign-up never confirmed its email.
        adminUserRepository.findByUsernameIgnoreCase(username)
            .filter(u -> Boolean.FALSE.equals(u.getEmailVerified()) && u.getCreatedAt() != null
                && u.getCreatedAt().isBefore(OffsetDateTime.now().minus(UNVERIFIED_HOLD)))
            .ifPresent(u -> { adminUserRepository.delete(u); adminUserRepository.flush(); });
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
            .email(email)
            .role("FULL")
            .createdAt(OffsetDateTime.now())
            .emailVerified(false)
            .verificationToken(newToken())
            .verificationSentAt(OffsetDateTime.now())
            .build();
        try {
            adminUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already taken"));
        }
        sendVerification(user);
        return ResponseEntity.ok(Map.of("verification_required", true, "email", email));
    }

    public record TokenRequest(String token) {}
    public record ResendRequest(String username) {}

    @Operation(summary = "Verify email", description = "Confirms the address from the emailed link and signs the user in")
    @PostMapping("/verify-email")
    @Transactional
    public ResponseEntity<Map<String, Object>> verify(@RequestBody TokenRequest req) {
        if (req == null || req.token() == null || req.token().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "This link is incomplete"));
        }
        AdminUser user = adminUserRepository.findByVerificationToken(req.token().trim()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "This link has expired or was already used. Try signing in."));
        }
        if (user.getVerificationSentAt() != null && user.getVerificationSentAt().isBefore(OffsetDateTime.now().minus(LINK_LIFETIME))) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "This link has expired. Sign in to get a new one."));
        }
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        adminUserRepository.save(user);
        String token = jwtService.generateToken(user.getUsername(), null, "FULL");
        return ResponseEntity.ok(Map.of("token", token, "expires_at", jwtService.getExpiry(token).toString()));
    }

    @Operation(summary = "Resend verification email")
    @PostMapping("/resend-verification")
    @Transactional
    public ResponseEntity<Map<String, Object>> resend(@RequestBody ResendRequest req) {
        // Same answer whether or not the account exists, so usernames can't be probed.
        Map<String, Object> ok = Map.of("success", true);
        if (req == null || req.username() == null) return ResponseEntity.ok(ok);
        adminUserRepository.findByUsernameIgnoreCase(req.username().trim())
            .filter(u -> Boolean.FALSE.equals(u.getEmailVerified()) && u.getEmail() != null)
            .filter(u -> u.getVerificationSentAt() == null || u.getVerificationSentAt().isBefore(OffsetDateTime.now().minus(RESEND_GAP)))
            .ifPresent(u -> {
                u.setVerificationToken(newToken());  // a fresh link each time; older links stop working
                u.setVerificationSentAt(OffsetDateTime.now());
                adminUserRepository.save(u);
                sendVerification(u);
            });
        return ResponseEntity.ok(ok);
    }

    private static String newToken() {
        return java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private void sendVerification(AdminUser u) {
        String link = frontendBaseUrl.replaceAll("/+$", "") + "/verify-email?token=" + u.getVerificationToken();
        String who = u.getDisplayName() != null ? u.getDisplayName() : u.getUsername();
        String body = "Hi " + who + ",\n\n"
            + "Confirm your email to finish setting up your ProgramHub account:\n\n" + link + "\n\n"
            + "If you didn't sign up, you can ignore this email.";
        String html = com.btl.transport.notification.EmailLayout.forPlatform()
            .title("Confirm your email")
            .preheader("One click to finish setting up your ProgramHub account")
            .greeting(who)
            .paragraph("Thanks for signing up. Confirm your email address to finish setting up your ProgramHub account.")
            .button("Confirm email", link)
            .note("If you didn't create a ProgramHub account, you can safely ignore this email.")
            .html();
        sendGridService.sendHtmlEmail(u.getEmail(), u.getDisplayName(), "Confirm your email", html, body);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
