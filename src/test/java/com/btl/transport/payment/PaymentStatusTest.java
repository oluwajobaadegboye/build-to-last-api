package com.btl.transport.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {
    @Test
    void status_follows_total_against_fee() {
        BigDecimal fee = new BigDecimal("150.00");
        assertThat(PaymentService.statusFor(BigDecimal.ZERO, fee)).isEqualTo("unpaid");
        assertThat(PaymentService.statusFor(new BigDecimal("50"), fee)).isEqualTo("partial");
        assertThat(PaymentService.statusFor(new BigDecimal("150"), fee)).isEqualTo("paid");
        assertThat(PaymentService.statusFor(new BigDecimal("175"), fee)).isEqualTo("paid");
        assertThat(PaymentService.normalizeStatus("PAID")).isEqualTo("paid");
        assertThat(PaymentService.normalizeStatus(null)).isEqualTo("unpaid");
    }
}
