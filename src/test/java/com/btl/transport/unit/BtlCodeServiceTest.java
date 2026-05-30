package com.btl.transport.unit;

import com.btl.transport.common.BtlCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BtlCodeServiceTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    @InjectMocks
    BtlCodeService service;

    @Test
    void code_format_is_BTL_padded_three_digits() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(42L);
        assertThat(service.generateNextCode("p_123", "BTL")).isEqualTo("BTL-042");
    }

    @Test
    void code_pads_single_digit() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(1L);
        assertThat(service.generateNextCode("p_123", "BTL")).isEqualTo("BTL-001");
    }

    @Test
    void code_handles_large_sequence() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(400L);
        assertThat(service.generateNextCode("p_123", "BTL")).isEqualTo("BTL-400");
    }

    @Test
    void concurrent_generation_produces_unique_codes() throws InterruptedException {
        AtomicLong counter = new AtomicLong(0);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
            .thenAnswer(inv -> counter.incrementAndGet());

        CopyOnWriteArraySet<String> codes = new CopyOnWriteArraySet<>();
        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int i = 0; i < 100; i++) {
            pool.submit(() -> codes.add(service.generateNextCode("p_123", "BTL")));
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(codes).hasSize(100);
    }
}
