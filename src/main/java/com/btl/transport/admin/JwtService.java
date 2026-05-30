package com.btl.transport.admin;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AdminProperties props;

    public String generateToken(String username, String programId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds((long) props.getJwt().getExpiryHours() * 3600);
        var builder = Jwts.builder()
            .subject(username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry));
        if (programId != null) {
            builder = builder.claim("program_id", programId);
        }
        return builder.signWith(signingKey()).compact();
    }

    public String validateAndExtractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractProgramId(String token) {
        try { return parseClaims(token).get("program_id", String.class); } catch (Exception e) { return null; }
    }

    public Instant getExpiry(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
