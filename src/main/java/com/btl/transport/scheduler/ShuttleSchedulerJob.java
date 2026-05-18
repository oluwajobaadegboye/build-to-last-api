package com.btl.transport.scheduler;

import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.run.RunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShuttleSchedulerJob {

    private final RunService runService;

    // 6 AM Indianapolis time (EDT = UTC-4) = 10:00 UTC
    // Runs on Jun 11, 12, 13, 14 of any year
    @Scheduled(cron = "0 0 10 11,12,13,14 6 ?", zone = "UTC")
    public void generateDailySlots() {
        LocalDate today = LocalDate.now();
        ConferenceDay day = toConferenceDay(today);
        if (day == null) {
            log.info("Today {} is not a conference day — skipping shuttle slot generation", today);
            return;
        }
        log.info("Generating shuttle slots for {} ({})", today, day);
        runService.generateSlotsForDate(today, day);
    }

    private ConferenceDay toConferenceDay(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case THURSDAY -> ConferenceDay.THURSDAY;
            case FRIDAY -> ConferenceDay.FRIDAY;
            case SATURDAY -> ConferenceDay.SATURDAY;
            case SUNDAY -> ConferenceDay.SUNDAY;
            default -> null;
        };
    }
}
