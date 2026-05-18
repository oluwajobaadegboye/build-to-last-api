package com.btl.transport.notification;

import com.btl.transport.participant.Participant;
import com.btl.transport.run.Run;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final TwilioService twilioService;
    private final SendGridService sendGridService;
    private final NotificationConfigRepository configRepository;

    @Value("${btl.frontend-base-url}")
    private String frontendBaseUrl;

    public void sendRegistrationConfirmation(Participant p) {
        NotificationConfig config = getConfig();
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        Map<String, String> vars = Map.of(
            "name", p.getFullName(),
            "btl_code", p.getBtlCode(),
            "link", statusLink
        );
        String body = renderTemplate(config.getTemplateRegistration(), vars);
        if (p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
            sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                "BTL 2026 — Transport Registration Confirmed", body);
        }
    }

    public void sendDelayNotification(Participant p, String flightNumber, int delayMins, boolean major) {
        NotificationConfig config = getConfig();
        Map<String, String> vars = Map.of(
            "name", p.getFullName(),
            "flight", flightNumber,
            "delay_mins", String.valueOf(delayMins)
        );
        String template = major ? config.getTemplateDelayMajor() : config.getTemplateDelayMinor();
        String body = renderTemplate(template, vars);
        if (p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (major && p.getEmail() != null) {
            sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                "BTL 2026 — Flight Delay Update", body);
        }
    }

    public void sendCancellationNotification(Participant p, String flightNumber) {
        NotificationConfig config = getConfig();
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        Map<String, String> vars = Map.of(
            "name", p.getFullName(),
            "flight", flightNumber,
            "btl_code", p.getBtlCode(),
            "link", statusLink
        );
        String body = renderTemplate(config.getTemplateCancellation(), vars);
        if (p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
            sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                "BTL 2026 — Flight Cancellation", body);
        }
    }

    public void sendShuttleReminders(Run run, NotificationConfig config) {
        // Placeholder — implemented by ReminderJob
        log.info("Shuttle reminder triggered for run {}", run.getRunId());
    }

    public void forwardHelpMessage(String fromPhone, String message,
                                   NotificationConfig config,
                                   Participant participant) {
        String context = String.format("%s %s from %s: \"%s\"",
            participant.getBtlCode(), participant.getFullName(), fromPhone, message);
        if (config.getAdminWhatsapp1() != null)
            twilioService.sendWhatsApp(config.getAdminWhatsapp1(), context);
        if (config.getAdminWhatsapp2() != null)
            twilioService.sendWhatsApp(config.getAdminWhatsapp2(), context);
    }

    public String renderTemplate(String template, Map<String, String> vars) {
        if (template == null) return "";
        String result = template;
        for (var entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private NotificationConfig getConfig() {
        return configRepository.findByConfigKey("main")
            .orElseThrow(() -> new IllegalStateException("Notification config not found"));
    }
}
