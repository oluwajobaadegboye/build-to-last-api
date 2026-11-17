package com.btl.transport.announcement;

import com.btl.transport.admin.AdminAccess;
import com.btl.transport.admin.AdminUser;
import com.btl.transport.admin.AdminUserRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.program.Program;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Admin: write an announcement, pick who gets it, send it, and see what was sent (program = X-Program-Id). */
@Tag(name = "Announcements", description = "Emails from the organisers to attendees, also shown on their pages")
@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;
    private final AnnouncementSender sender;
    private final AnnouncementRepository repository;
    private final AdminAccess adminAccess;
    private final AdminUserRepository adminUserRepository;
    private final com.btl.transport.notification.SendGridService sendGrid;

    public record PreviewRequest(AnnouncementService.Audience audience) {}
    public record TestRequest(String subject, String body) {}
    public record SendRequest(String subject, String body, AnnouncementService.Audience audience,
                              @JsonProperty("show_on_status") Boolean showOnStatus,
                              @JsonProperty("request_id") String requestId) {}
    public record UpdateRequest(@JsonProperty("show_on_status") Boolean showOnStatus) {}

    private Program program(String programId) {
        if (programId == null || programId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        adminAccess.requireProgram(programId);
        return service.program(programId);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        program(programId);
        return repository.findTop100ByProgramIdOrderByCreatedAtDesc(programId).stream().map(AnnouncementController::dto).toList();
    }

    @GetMapping("/options")
    public Map<String, Object> options(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        return service.options(program(programId));
    }

    /** How many people the filters cover, with a few names, before sending. */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                       @RequestBody PreviewRequest req) {
        program(programId);
        List<Participant> to = service.recipients(programId, req.audience());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", to.size());
        m.put("sample", to.stream().limit(5).map(Participant::getFullName).toList());
        m.put("label", service.label(programId, req.audience()));
        return m;
    }

    /** Sends the email to the signed-in admin only, so they can see how it looks. */
    @PostMapping("/test")
    public Map<String, Object> test(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                    @RequestBody TestRequest req) {
        Program prog = program(programId);
        AnnouncementService.validate(req.subject(), req.body());
        AdminUser me = Optional.ofNullable(adminAccess.currentUsername()).flatMap(adminUserRepository::findByUsernameIgnoreCase).orElse(null);
        if (me == null || me.getEmail() == null || me.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your account has no email address to send a test to");
        }
        String first = me.getDisplayName() != null && !me.getDisplayName().isBlank() ? me.getDisplayName().trim().split("\\s+")[0] : null;
        var e = sender.compose(prog, req.subject().trim(), req.body().trim(), first, null);
        boolean ok = sendTest(e, me);
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The test email couldn't be sent — try again");
        return Map.of("sent_to", me.getEmail());
    }

    private boolean sendTest(AnnouncementSender.Email e, AdminUser me) {
        return sendGrid.sendHtmlEmailChecked(e.senderName(), me.getEmail(), me.getDisplayName(), "[Test] " + e.subject(), e.html(), e.plain());
    }

    @PostMapping
    public Map<String, Object> send(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                    @RequestBody SendRequest req) {
        program(programId);
        Announcement a = service.create(programId, req.subject(), req.body(), req.audience(),
            !Boolean.FALSE.equals(req.showOnStatus()), req.requestId(), adminAccess.currentUsername());
        if (Announcement.SENDING.equals(a.getStatus())) sender.deliverAsync(a.getId());
        return dto(a);
    }

    /** Show or hide it on attendees' pages (the emails already went out). */
    @PatchMapping("/{id}")
    public Map<String, Object> update(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                      @PathVariable Integer id, @RequestBody UpdateRequest req) {
        program(programId);
        Announcement a = repository.findById(id).filter(x -> x.getProgramId().equals(programId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        if (req.showOnStatus() != null) a.setShowOnStatus(req.showOnStatus());
        return dto(repository.save(a));
    }

    static Map<String, Object> dto(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("subject", a.getSubject());
        m.put("body", a.getBody());
        m.put("audience_label", a.getAudienceLabel());
        m.put("show_on_status", a.getShowOnStatus());
        m.put("status", a.getStatus());
        m.put("recipients", a.getRecipients());
        m.put("sent_count", a.getSentCount());
        m.put("failed_count", a.getFailedCount());
        m.put("sent_by", a.getSentBy());
        m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        m.put("finished_at", a.getFinishedAt() != null ? a.getFinishedAt().toString() : null);
        return m;
    }
}
