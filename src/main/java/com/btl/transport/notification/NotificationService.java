package com.btl.transport.notification;

import com.btl.transport.participant.Participant;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.run.Run;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final TwilioService twilioService;
    private final SendGridService sendGridService;
    private final NotificationConfigRepository configRepository;
    private final ProgramRepository programRepository;

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

    public void sendRescheduleNotification(Participant p, String flightNumber,
                                            OffsetDateTime oldTime, OffsetDateTime newTime) {
        NotificationConfig config = getConfig();
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        ZoneId zone = zoneFor(p);
        Map<String, String> vars = Map.of(
            "name", p.getFullName(),
            "flight", flightNumber,
            "old_time", formatDateTime(oldTime, zone),
            "new_time", formatDateTime(newTime, zone),
            "link", statusLink
        );
        String body = renderTemplate(config.getTemplateReschedule(), vars);
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
            sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                "BTL 2026 — Flight Rescheduled", body);
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

    public void sendRoomAssignment(Participant p, String hotelName, String roomLabel,
                                    String roomType, List<String> roommateNames) {
        NotificationConfig config = getConfig();
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        String roommatesText = roommateNames.isEmpty() ? "" : String.join(", ", roommateNames);
        String roommatesSection = roommateNames.isEmpty() ? "" :
            "<tr><td style=\"padding:8px 24px 20px;\">" +
            "<div style=\"height:1px;background:rgba(255,255,255,0.08);margin-bottom:12px;\"></div>" +
            "<p style=\"margin:0 0 6px;font-size:10px;font-weight:700;letter-spacing:0.22em;text-transform:uppercase;color:#546475;\">Roommates</p>" +
            "<p style=\"margin:0;font-size:13px;color:#CBD5E1;line-height:1.6;\">" + roommatesText + "</p>" +
            "</td></tr>";
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name",             p.getFullName());
        vars.put("hotel",            hotelName);
        vars.put("room",             roomLabel);
        vars.put("room_type",        roomType);
        vars.put("roommates",        roommatesText);
        vars.put("roommates_section", roommatesSection);
        vars.put("status_url",       statusLink);
        vars.put("btl_code",         p.getBtlCode());
        String template = config.getTemplateRoomAssignment();
        if (template == null) template = "Hi {{name}}, your room at {{hotel}}, {{room}} ({{room_type}}) is confirmed. View details: {{status_url}}";
        String plainBody = renderTemplate(template, vars);
        if (p.getEmail() != null) {
            String htmlTemplate = loadHtmlTemplate("email-room-assignment.html");
            if (htmlTemplate != null) {
                sendGridService.sendHtmlEmail(p.getEmail(), p.getFullName(),
                    "BTL 2026 — Your Room Assignment",
                    renderTemplate(htmlTemplate, vars), plainBody);
            } else {
                sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                    "BTL 2026 — Your Room Assignment", plainBody);
            }
        }
    }

    public void sendRoomAssignment(String name, String email, String hotelName,
                                    String roomLabel, String roomType, List<String> roommateNames) {
        NotificationConfig config = getConfig();
        String roommatesText = roommateNames.isEmpty() ? "" : String.join(", ", roommateNames);
        String roommatesSection = roommateNames.isEmpty() ? "" :
            "<tr><td style=\"padding:8px 24px 20px;\">" +
            "<div style=\"height:1px;background:rgba(255,255,255,0.08);margin-bottom:12px;\"></div>" +
            "<p style=\"margin:0 0 6px;font-size:10px;font-weight:700;letter-spacing:0.22em;text-transform:uppercase;color:#546475;\">Roommates</p>" +
            "<p style=\"margin:0;font-size:13px;color:#CBD5E1;line-height:1.6;\">" + roommatesText + "</p>" +
            "</td></tr>";
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("name",              name);
        vars.put("hotel",             hotelName);
        vars.put("room",              roomLabel);
        vars.put("room_type",         roomType);
        vars.put("roommates",         roommatesText);
        vars.put("roommates_section", roommatesSection);
        vars.put("status_url",        frontendBaseUrl);
        vars.put("btl_code",          "");
        String template = config.getTemplateRoomAssignment();
        if (template == null) template = "Hi {{name}}, your room at {{hotel}}, {{room}} ({{room_type}}) is confirmed.";
        String plainBody = renderTemplate(template, vars);
        String htmlTemplate = loadHtmlTemplate("email-room-assignment.html");
        if (htmlTemplate != null) {
            sendGridService.sendHtmlEmail(email, name, "BTL 2026 — Your Room Assignment",
                renderTemplate(htmlTemplate, vars), plainBody);
        } else {
            sendGridService.sendEmail(email, name, "BTL 2026 — Your Room Assignment", plainBody);
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

    private String formatDateTime(OffsetDateTime time, ZoneId zone) {
        if (time == null) return "unknown";
        return time.atZoneSameInstant(zone)
                   .format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a z"));
    }

    private ZoneId zoneFor(Participant p) {
        if (p.getProgramId() == null) return ZoneId.of("America/New_York");
        return programRepository.findById(p.getProgramId())
            .map(Program::getTimezone)
            .filter(tz -> tz != null && !tz.isBlank())
            .map(tz -> { try { return ZoneId.of(tz); } catch (Exception e) { return ZoneId.of("America/New_York"); } })
            .orElse(ZoneId.of("America/New_York"));
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
