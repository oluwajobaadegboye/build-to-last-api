package com.btl.transport.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BtlCodeService {

    private final JdbcTemplate jdbcTemplate;

    public String generateNextCode() {
        Long next = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(CAST(SUBSTRING(btl_code FROM 5) AS INTEGER)), 0) + 1" +
            " FROM participants WHERE btl_code ~ '^BTL-[0-9]+$'",
            Long.class
        );
        return String.format("BTL-%03d", next);
    }
}
