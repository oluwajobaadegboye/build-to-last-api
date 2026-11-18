package org.programhub.announcement;

import org.programhub.notification.EmailLayout;
import org.programhub.notification.ProgramBranding;
import org.programhub.notification.SendGridService;
import org.programhub.participant.Participant;
import org.programhub.participant.ParticipantRepository;
import org.programhub.program.Program;
import org.programhub.program.ProgramRepository;
import org.programhub.scheduler.JobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Emails an announcement to its recipients in the background (a few hundred emails take a minute or two).
 * Each recipient is marked as they're emailed, so if the server restarts part-way the rest are sent when it
 * comes back, and nobody gets it twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementSender {

    private final AnnouncementRepository repository;
    private final ProgramRepository programRepository;
    private final ParticipantRepository participantRepository;
    private final SendGridService sendGrid;
    private final JdbcTemplate jdbc;
    private final JobLock jobLock;

    @Value("${btl.frontend-base-url}")
    private String frontendBaseUrl;

    /** The email for one person. {@code code} = their registration code (null for a test send). */
    public Email compose(Program prog, String subject, String body, String firstName, String code) {
        var brand = ProgramBranding.of(prog);
        String statusLink = code == null ? frontendBaseUrl + "/status" : frontendBaseUrl + "/status?code=" + code;
        String html = EmailLayout.forProgram(brand)
            .title(subject)
            .preheader(firstLine(body))
            .greeting(firstName)
            .richText(body)
            .button("Open my registration", statusLink)
            .footerNote("You're receiving this because you registered for " + brand.nameOrGeneric() + ".")
            .html();
        String plain = (firstName == null || firstName.isBlank() ? "Hello," : "Hi " + firstName.trim() + ",") + "\n\n"
            + body.replace("**", "") + "\n\nOpen my registration: " + statusLink + "\n\n" + brand.teamName();
        return new Email(brand.senderName(), subject, html, plain);
    }

    public record Email(String senderName, String subject, String html, String plain) {}

    private static String firstLine(String body) {
        String s = body.replaceAll("[*\\[\\]]", "").replaceAll("\\s+", " ").trim();
        return s.length() > 110 ? s.substring(0, 109) + "…" : s;
    }

    @Async
    public void deliverAsync(Integer announcementId) {
        deliver(announcementId);
    }

    /** Emails everyone not yet emailed. Only one server copy works on an announcement at a time. */
    public void deliver(Integer announcementId) {
        String lock = "announcement-" + announcementId;
        if (!jobLock.acquire(lock, Duration.ofMinutes(20))) return;
        try { send(announcementId, lock); }
        finally { jdbc.update("DELETE FROM job_locks WHERE name = ?", lock); }
    }

    private void send(Integer announcementId, String lock) {
        Announcement a = repository.findById(announcementId).orElse(null);
        if (a == null || !Announcement.SENDING.equals(a.getStatus())) return;
        Program prog = programRepository.findById(a.getProgramId()).orElse(null);
        if (prog == null) return;
        List<Integer> pending = jdbc.queryForList(
            "SELECT participant_id FROM announcement_recipients WHERE announcement_id = ? AND delivered IS NULL",
            Integer.class, announcementId);
        int done = 0;
        for (Participant p : participantRepository.findAllById(pending)) {
            boolean ok = false;
            try {
                if (p.getEmail() != null && !p.getEmail().isBlank()) {
                    Email e = compose(prog, a.getSubject(), a.getBody(), firstName(p), p.getRegCode());
                    ok = sendGrid.sendHtmlEmailChecked(e.senderName(), p.getEmail(), p.getFullName(), e.subject(), e.html(), e.plain());
                }
            } catch (Exception ex) {
                log.warn("Announcement {} to participant {} failed: {}", announcementId, p.getId(), ex.getMessage());
            }
            jdbc.update("UPDATE announcement_recipients SET delivered = ? WHERE announcement_id = ? AND participant_id = ?",
                ok, announcementId, p.getId());
            if (++done % 20 == 0) {
                updateCounts(announcementId, false);
                // Still working: keep the other server copy from picking this up.
                jdbc.update("UPDATE job_locks SET locked_until = now() + interval '20 minutes' WHERE name = ?", lock);
            }
        }
        // Anyone removed since it was created can't be emailed now.
        jdbc.update("UPDATE announcement_recipients SET delivered = false WHERE announcement_id = ? AND delivered IS NULL", announcementId);
        updateCounts(announcementId, true);
        log.info("Announcement {} for {} sent", announcementId, a.getProgramId());
    }

    private void updateCounts(Integer id, boolean finished) {
        jdbc.update(
            "UPDATE announcements SET " +
            "sent_count = (SELECT COUNT(*) FROM announcement_recipients WHERE announcement_id = ? AND delivered), " +
            "failed_count = (SELECT COUNT(*) FROM announcement_recipients WHERE announcement_id = ? AND delivered = false)" +
            (finished ? ", status = 'sent', finished_at = ?" : "") + " WHERE id = ?",
            finished ? new Object[]{id, id, OffsetDateTime.now(), id} : new Object[]{id, id, id});
    }

    private static String firstName(Participant p) {
        if (p.getFirstName() != null && !p.getFirstName().isBlank()) return p.getFirstName();
        String full = p.getFullName() == null ? "" : p.getFullName().trim();
        return full.isEmpty() ? null : full.split("\\s+")[0];
    }

    /**
     * Carries on with any announcement still marked as sending whose server stopped part-way (its claim has
     * run out). One being sent right now is skipped: its server still holds the claim.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void resumeUnfinished() {
        for (Announcement a : repository.findByStatus(Announcement.SENDING)) {
            if (a.getCreatedAt() != null && a.getCreatedAt().isAfter(OffsetDateTime.now().minusMinutes(2))) continue;
            deliver(a.getId());
        }
    }
}
