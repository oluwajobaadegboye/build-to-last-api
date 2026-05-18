package com.btl.transport.scheduler;

import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunRepository;
import com.btl.transport.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "btl.reminders.enabled", havingValue = "true", matchIfMissing = false)
public class ReminderJob {

    private final RunRepository runRepository;
    private final NotificationConfigRepository configRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 300_000)
    public void sendReminders() {
        NotificationConfig config = configRepository.findByConfigKey("main").orElse(null);
        if (config == null) return;

        List<Run> runs = runRepository.findScheduledRunsForToday();
        for (Run run : runs) {
            try {
                notificationService.sendShuttleReminders(run, config);
            } catch (Exception e) {
                log.warn("Reminder failed for run {}: {}", run.getRunId(), e.getMessage());
            }
        }
    }
}
