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
import java.util.HashMap;
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

    // ── Default message texts ──────────────────────────────────────────────
    // Used when the program hasn't set its own text. Everything program-specific comes from
    // placeholders filled from the program's details (see ProgramBranding), never a fixed program.
    static final String DEFAULT_REGISTRATION =
        "Hi {{name}}! You're registered for {{program_name}} ✅\nYour code: {{btl_code}}\n"
        + "Use it to see {{covers}}: {{status_url}}\nReply HELP for assistance.";
    static final String DEFAULT_DELAY_MINOR =
        "Hi {{name}}, your flight {{flight}} is delayed by about {{delay_mins}} minutes ⏳ "
        + "Your pickup will be adjusted automatically. — {{team_name}}";
    static final String DEFAULT_DELAY_MAJOR =
        "Hi {{name}}, your flight {{flight}} has a major delay (about {{delay_mins}} minutes) ⚠️ "
        + "Please contact a coordinator about your pickup. — {{team_name}}";
    static final String DEFAULT_RESCHEDULE =
        "Hi {{name}}, your flight {{flight}} has been rescheduled. Original arrival: {{old_time}}. "
        + "New arrival: {{new_time}}. Check {{link}} for your updated pickup details. — {{team_name}}";
    static final String DEFAULT_CANCELLATION =
        "Hi {{name}} ({{btl_code}}), your flight {{flight}} appears cancelled or changed ❌ "
        + "Please contact a coordinator as soon as possible or reply HELP. — {{team_name}}";
    static final String DEFAULT_ROOM_ASSIGNMENT =
        "Hi {{name}}, your room for {{program_name}} is ready. {{lodging_label}}: {{hotel}}, "
        + "Room: {{room}} ({{room_type}}). View {{covers}}: {{status_url}}";

    public void sendRegistrationConfirmation(Participant p) {
        Program program = programOf(p.getProgramId());
        ProgramBranding brand = ProgramBranding.of(program);
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        Map<String, String> vars = new HashMap<>(brand.textVars());
        vars.put("name", p.getFullName());
        vars.put("btl_code", p.getBtlCode());
        vars.put("status_url", statusLink);
        String plainBody = renderTemplate(
            textTemplate(p.getProgramId(), NotificationConfig::getTemplateRegistration, DEFAULT_REGISTRATION), vars);
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), plainBody);
        if (p.getEmail() != null) {
            String subject = brand.subject("Registration Confirmed");
            String htmlTemplate = loadHtmlTemplate("email-registration.html");
            if (htmlTemplate != null) {
                sendGridService.sendHtmlEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), subject,
                    renderTemplate(htmlTemplate, htmlVars(brand, vars)), plainBody);
            } else {
                sendGridService.sendEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), subject, plainBody);
            }
        }
    }

    public void sendDelayNotification(Participant p, String flightNumber, int delayMins, boolean major) {
        ProgramBranding brand = ProgramBranding.of(programOf(p.getProgramId()));
        Map<String, String> vars = new HashMap<>(brand.textVars());
        vars.put("name", p.getFullName());
        vars.put("flight", flightNumber);
        vars.put("delay_mins", String.valueOf(delayMins));
        String body = renderTemplate(major
            ? textTemplate(p.getProgramId(), NotificationConfig::getTemplateDelayMajor, DEFAULT_DELAY_MAJOR)
            : textTemplate(p.getProgramId(), NotificationConfig::getTemplateDelayMinor, DEFAULT_DELAY_MINOR), vars);
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
            sendGridService.sendEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), brand.subject("Flight Delay Update"), body);
        }
    }

    public void sendRescheduleNotification(Participant p, String flightNumber,
                                            OffsetDateTime oldTime, OffsetDateTime newTime) {
        ProgramBranding brand = ProgramBranding.of(programOf(p.getProgramId()));
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        ZoneId zone = zoneFor(p);
        Map<String, String> vars = new HashMap<>(brand.textVars());
        vars.put("name", p.getFullName());
        vars.put("flight", flightNumber);
        vars.put("old_time", formatDateTime(oldTime, zone));
        vars.put("new_time", formatDateTime(newTime, zone));
        vars.put("link", statusLink);
        String body = renderTemplate(
            textTemplate(p.getProgramId(), NotificationConfig::getTemplateReschedule, DEFAULT_RESCHEDULE), vars);
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
            sendGridService.sendEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), brand.subject("Flight Rescheduled"), body);
        }
    }

    public void sendCancellationNotification(Participant p, String flightNumber) {
        ProgramBranding brand = ProgramBranding.of(programOf(p.getProgramId()));
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        Map<String, String> vars = new HashMap<>(brand.textVars());
        vars.put("name", p.getFullName());
        vars.put("flight", flightNumber);
        vars.put("btl_code", p.getBtlCode());
        vars.put("link", statusLink);
        String body = renderTemplate(
            textTemplate(p.getProgramId(), NotificationConfig::getTemplateCancellation, DEFAULT_CANCELLATION), vars);
        if (smsEnabled && p.getPhone() != null) twilioService.sendSms(p.getPhone(), body);
        if (p.getEmail() != null) {
            sendGridService.sendEmailAs(brand.senderName(), p.getEmail(), p.getFullName(), brand.subject("Flight Cancellation"), body);
        }
    }

    public void sendRoomAssignment(Participant p, String hotelName, String roomLabel,
                                    String roomType, List<String> roommateNames) {
        String statusLink = frontendBaseUrl + "/status?code=" + p.getBtlCode();
        sendRoomAssignmentEmail(p.getProgramId(), p.getFullName(), p.getEmail(), p.getBtlCode(), statusLink,
            hotelName, roomLabel, roomType, roommateNames);
    }

    /** Room assignment for an occupant who isn't a registered participant (no code / status link). */
    public void sendRoomAssignment(String programId, String name, String email, String hotelName,
                                    String roomLabel, String roomType, List<String> roommateNames) {
        sendRoomAssignmentEmail(programId, name, email, "", frontendBaseUrl,
            hotelName, roomLabel, roomType, roommateNames);
    }

    private void sendRoomAssignmentEmail(String programId, String name, String email, String code, String statusLink,
                                         String hotelName, String roomLabel, String roomType, List<String> roommateNames) {
        if (email == null) return;
        ProgramBranding brand = ProgramBranding.of(programOf(programId));
        String roommatesText = roommateNames.isEmpty() ? "" : String.join(", ", roommateNames);
        String roommatesSection = roommateNames.isEmpty() ? "" :
            "<tr><td style=\"padding:8px 24px 20px;\">" +
            "<div style=\"height:1px;background:rgba(255,255,255,0.08);margin-bottom:12px;\"></div>" +
            "<p style=\"margin:0 0 6px;font-size:10px;font-weight:700;letter-spacing:0.22em;text-transform:uppercase;color:#546475;\">Roommates</p>" +
            "<p style=\"margin:0;font-size:13px;color:#CBD5E1;line-height:1.6;\">" + ProgramBranding.escape(roommatesText) + "</p>" +
            "</td></tr>";
        Map<String, String> vars = new HashMap<>(brand.textVars());
        vars.put("name",       name);
        vars.put("hotel",      hotelName);
        vars.put("room",       roomLabel);
        vars.put("room_type",  roomType);
        vars.put("roommates",  roommatesText);
        vars.put("status_url", statusLink);
        vars.put("btl_code",   code);
        String plainBody = renderTemplate(
            textTemplate(programId, NotificationConfig::getTemplateRoomAssignment, DEFAULT_ROOM_ASSIGNMENT), vars);
        String subject = brand.subject("Your Room Assignment");
        String htmlTemplate = loadHtmlTemplate("email-room-assignment.html");
        if (htmlTemplate != null) {
            Map<String, String> html = htmlVars(brand, vars);
            html.put("roommates_section", roommatesSection);  // already escaped markup
            sendGridService.sendHtmlEmailAs(brand.senderName(), email, name, subject, renderTemplate(htmlTemplate, html), plainBody);
        } else {
            sendGridService.sendEmailAs(brand.senderName(), email, name, subject, plainBody);
        }
    }

    // ── Program lookups ────────────────────────────────────────────────────

    private Program programOf(String programId) {
        return programId == null ? null : programRepository.findById(programId).orElse(null);
    }

    /** The program's own text for a message if it set one, otherwise the generic default. */
    private String textTemplate(String programId, java.util.function.Function<NotificationConfig, String> field,
                                String fallback) {
        if (programId == null) return fallback;
        return configRepository.findByProgramId(programId)
            .map(field)
            .filter(t -> t != null && !t.isBlank())
            .orElse(fallback);
    }

    /** HTML placeholders: branding (escaped, incl. layout blocks) plus the message values, escaped. */
    private static Map<String, String> htmlVars(ProgramBranding brand, Map<String, String> textVars) {
        Map<String, String> html = new HashMap<>();
        textVars.forEach((k, v) -> html.put(k, ProgramBranding.escape(v)));
        html.putAll(brand.htmlVars());
        return html;
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
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

}
