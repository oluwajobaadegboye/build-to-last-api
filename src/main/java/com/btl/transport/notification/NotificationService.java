package com.btl.transport.notification;

import com.btl.transport.participant.Participant;
import com.btl.transport.run.Run;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Value("${btl.notifications.sms-enabled:false}")
    private boolean smsEnabled;

    public void sendRegistrationConfirmation(Participant p) {
        NotificationConfig config = getConfig();
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        Map<String, String> vars = Map.of(
            "name", p.getFullName(),
            "btl_code", p.getBtlCode(),
            "status_url", statusLink
        );
        String plainBody = renderTemplate(config.getTemplateRegistration(), vars);
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), plainBody);
        if (p.getEmail() != null) {
            String htmlTemplate = loadHtmlTemplate("email-registration.html");
            if (htmlTemplate != null) {
                sendGridService.sendHtmlEmail(p.getEmail(), p.getFullName(),
                    "BTL 2026 — Transport Registration Confirmed",
                    renderTemplate(htmlTemplate, vars), plainBody);
            } else {
                sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                    "BTL 2026 — Transport Registration Confirmed", plainBody);
            }
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
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
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
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
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

    private String loadHtmlTemplate(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + filename);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("HTML template {} not found, falling back to plain text", filename);
            return null;
        }
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
