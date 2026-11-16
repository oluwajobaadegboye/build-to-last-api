package com.btl.transport.scheduler;

import com.btl.transport.payment.PaymentService;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * The optional automatic payment reminder: once per program, {@code auto_reminder_days} before the start
 * date (in the program's timezone), to everyone still unpaid or part paid. Needs payment reminders on.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderJob {

    private final ProgramRepository programRepository;
    private final PaymentService paymentService;

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")  // hourly; each program sends at most once
    public void run() {
        for (Program p : programRepository.findAll()) {
            try { if (due(p)) send(p); }
            catch (Exception e) { log.warn("Automatic payment reminder failed for {}: {}", p.getId(), e.getMessage()); }
        }
    }

    /** Due on/after (start − days) and before the program ends, if not sent yet. */
    static boolean due(Program p, LocalDate today) {
        if (!Boolean.TRUE.equals(p.getPaymentRemindersEnabled()) || p.getAutoReminderDays() == null
            || p.getAutoReminderSentAt() != null || p.getStartDate() == null || p.getRegistrationFee() == null) return false;
        LocalDate start;
        try { start = LocalDate.parse(p.getStartDate().trim().substring(0, Math.min(10, p.getStartDate().trim().length()))); }
        catch (Exception e) { return false; }
        return !today.isBefore(start.minusDays(p.getAutoReminderDays())) && !today.isAfter(start);
    }

    private boolean due(Program p) {
        ZoneId zone;
        try { zone = ZoneId.of(p.getTimezone() != null ? p.getTimezone() : "America/New_York"); }
        catch (Exception e) { zone = ZoneId.of("America/New_York"); }
        return due(p, LocalDate.now(zone));
    }

    private void send(Program p) {
        // Mark first, so a failure part-way can never cause a second batch next hour.
        p.setAutoReminderSentAt(OffsetDateTime.now());
        programRepository.save(p);
        int n = paymentService.sendReminders(p.getId(), null);
        log.info("Automatic payment reminder for {}: {} sent", p.getId(), n);
    }
}
