package com.btl.transport.scheduler;

import com.btl.transport.program.Program;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReminderJobTest {

    private static Program program(Integer days) {
        Program p = new Program();
        p.setStartDate("2026-12-10");
        p.setRegistrationFee(new BigDecimal("100"));
        p.setPaymentRemindersEnabled(true);
        p.setAutoReminderDays(days);
        return p;
    }

    @Test
    void due_from_n_days_before_until_the_start() {
        Program p = program(7);
        assertThat(PaymentReminderJob.due(p, LocalDate.of(2026, 12, 2))).isFalse();
        assertThat(PaymentReminderJob.due(p, LocalDate.of(2026, 12, 3))).isTrue();
        assertThat(PaymentReminderJob.due(p, LocalDate.of(2026, 12, 10))).isTrue();
        assertThat(PaymentReminderJob.due(p, LocalDate.of(2026, 12, 11))).isFalse();
    }

    @Test
    void never_twice_never_when_off() {
        Program sent = program(7);
        sent.setAutoReminderSentAt(OffsetDateTime.now());
        assertThat(PaymentReminderJob.due(sent, LocalDate.of(2026, 12, 5))).isFalse();
        assertThat(PaymentReminderJob.due(program(null), LocalDate.of(2026, 12, 5))).isFalse();
        Program off = program(7);
        off.setPaymentRemindersEnabled(false);
        assertThat(PaymentReminderJob.due(off, LocalDate.of(2026, 12, 5))).isFalse();
        Program noFee = program(7);
        noFee.setRegistrationFee(null);
        assertThat(PaymentReminderJob.due(noFee, LocalDate.of(2026, 12, 5))).isFalse();
    }
}
