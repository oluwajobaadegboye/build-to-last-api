package com.btl.transport.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BtlCodeService {

    private final JdbcTemplate jdbcTemplate;

    public String generateNextCode(String programId, String programIni) {
        String prefix = derivePrefix(programIni);
        String pattern = "^" + prefix + "-[0-9]+$";
        Long next = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(CAST(SUBSTRING(btl_code FROM ?) AS INTEGER)), 0) + 1" +
            " FROM participants WHERE btl_code ~ ? AND program_id = ?",
            Long.class,
            prefix.length() + 2,  // skip "PREFIX-"
            pattern,
            programId
        );
        return String.format("%s-%03d", prefix, next);
    }

    private String derivePrefix(String ini) {
        if (ini != null && !ini.isBlank()) {
            return ini.trim().toUpperCase().replaceAll("[^A-Z]", "");
        }
        return "TRP";
    }
}
