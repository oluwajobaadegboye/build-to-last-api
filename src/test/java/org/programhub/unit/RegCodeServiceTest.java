package org.programhub.unit;

import org.programhub.common.RegCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegCodeServiceTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    RegCodeService service;

    @Test
    void code_is_prefix_and_five_random_characters() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        assertThat(service.generateNextCode("p_123", "BTL")).matches("BTL-[2-9][2-9A-HJKMNP-Z]{4}");
    }

    @Test
    void codes_are_not_sequential_or_repeated() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) codes.add(service.generateNextCode("p_123", "YC"));
        // 7.4 million possibilities: a rare repeat is fine here (the real check retries), counting up is not.
        assertThat(codes.size()).isGreaterThan(195);
        assertThat(codes).doesNotContain("YC-001", "YC-002");
    }

    @Test
    void retries_when_a_code_is_already_taken() {
        AtomicInteger calls = new AtomicInteger();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
            .thenAnswer(inv -> calls.incrementAndGet() < 3 ? 1 : 0);
        assertThat(service.generateNextCode("p_123", "YC")).matches("YC-[2-9][2-9A-HJKMNP-Z]{4}");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void prefix_falls_back_and_is_capped() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        assertThat(service.generateNextCode("p", null)).startsWith("TRP-");
        assertThat(service.generateNextCode("p", "123")).startsWith("TRP-");
        assertThat(service.generateNextCode("p", "Abcdefgh")).startsWith("ABCDE-");
    }

    @Test
    void driver_codes_are_random() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);
        assertThat(service.generateDriverCode()).matches("DRV-[2-9][2-9A-HJKMNP-Z]{4}");
    }
}
