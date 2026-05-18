package com.btl.transport.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BtlCodeService {

    private final JdbcTemplate jdbcTemplate;

    public String generateNextCode() {
        Long seq = jdbcTemplate.queryForObject(
            "SELECT nextval('btl_code_seq')", Long.class
        );
        return String.format("BTL-%03d", seq);
    }
}
