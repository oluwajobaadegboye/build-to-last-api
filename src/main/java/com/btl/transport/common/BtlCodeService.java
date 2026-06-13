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
        int offset = prefix.length() + 2;  // skip "PREFIX-"
        Long programMax = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(CAST(SUBSTRING(btl_code FROM ?) AS INTEGER)), 0)" +
            " FROM participants WHERE btl_code ~ ? AND program_id = ?",
            Long.class, offset, pattern, programId
        );
        Long globalMax = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(CAST(SUBSTRING(btl_code FROM ?) AS INTEGER)), 0)" +
            " FROM participants WHERE btl_code ~ ?",
            Long.class, offset, pattern
        );
        return String.format("%s-%03d", prefix, Math.max(programMax, globalMax) + 1);
    }

    private String derivePrefix(String ini) {
        if (ini != null && !ini.isBlank()) {
            return ini.trim().toUpperCase().replaceAll("[^A-Z]", "");
        }
        return "TRP";
    }
}
