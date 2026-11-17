package com.btl.transport.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Registration and driver codes: the program's letters, a dash and 5 random characters ("BTL-7KQ4M").
 * The code alone opens an attendee's page (and a driver's passenger list), so it's random rather than counting
 * up ("BTL-001" could be guessed); RateLimitFilter also limits wrong guesses. Older counting codes keep working.
 * The part after the dash starts with a digit (so the dash can be put back when someone types it without one)
 * and leaves out look-alikes: no 0/O, 1/I/L.
 */
@Service
@RequiredArgsConstructor
public class RegCodeService {

    /** 5 characters: 8 × 31⁴ ≈ 7.4 million possibilities per prefix. */
    static final int LENGTH = 5;
    private static final String FIRST = "23456789";
    private static final String REST = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    /** A new, unused registration code for the program, e.g. "BTL-7KQ4M". */
    public String generateNextCode(String programId, String programIni) {
        String prefix = derivePrefix(programIni);
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = prefix + "-" + randomPart();
            Integer used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participants WHERE reg_code = ?", Integer.class, code);
            if (used == null || used == 0) return code;
        }
        throw new IllegalStateException("Couldn't make a unique registration code");
    }

    /** A new, unused driver code, e.g. "DRV-7KQ4M". */
    public String generateDriverCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = "DRV-" + randomPart();
            Integer used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE driver_code = ?", Integer.class, code);
            if (used == null || used == 0) return code;
        }
        throw new IllegalStateException("Couldn't make a unique driver code");
    }

    private static String randomPart() {
        StringBuilder sb = new StringBuilder(LENGTH).append(FIRST.charAt(RANDOM.nextInt(FIRST.length())));
        for (int i = 1; i < LENGTH; i++) sb.append(REST.charAt(RANDOM.nextInt(REST.length())));
        return sb.toString();
    }

    /** Program initials as letters only, at most 5 (what the code box accepts); "TRP" when there are none. */
    static String derivePrefix(String ini) {
        String letters = ini == null ? "" : ini.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (letters.isEmpty()) return "TRP";
        return letters.length() > 5 ? letters.substring(0, 5) : letters;
    }
}
