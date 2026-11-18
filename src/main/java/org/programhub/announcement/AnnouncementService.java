package org.programhub.announcement;

import org.programhub.participant.Participant;
import org.programhub.participant.ParticipantRepository;
import org.programhub.payment.PaymentService;
import org.programhub.program.Program;
import org.programhub.program.ProgramRepository;
import org.programhub.registration.RegistrationField;
import org.programhub.registration.RegistrationFieldRepository;
import org.programhub.registration.RegistrationFieldService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import org.programhub.program.FeeRules;

/** Who an announcement goes to, and creating one (the emails are sent by {@link AnnouncementSender}). */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    public static final int MAX_SUBJECT = 150;
    public static final int MAX_BODY = 10_000;

    private final AnnouncementRepository repository;
    private final ParticipantRepository participantRepository;
    private final ProgramRepository programRepository;
    private final RegistrationFieldRepository fieldRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    /**
     * Who gets it. {@code who}: "registered" (default: everyone with a place), "waitlist", or "all" (both).
     * The other filters are optional; within one filter any value matches, and all filters must match.
     */
    public record Audience(
        String who,
        @JsonProperty("checked_in") String checkedIn,          // null/"any", "yes", "no"
        List<String> payment,                                 // unpaid / partial / paid
        @JsonProperty("question_key") String questionKey,
        @JsonProperty("question_values") List<String> questionValues,
        List<String> lodging,                                 // hotel / hostel names
        List<Integer> groups                                  // breakout group numbers
    ) {
        static Audience everyone() { return new Audience("registered", null, null, null, null, null, null); }
    }

    public Program program(String programId) {
        return programRepository.findById(programId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program not found"));
    }

    /** Everyone in the program the audience covers who has an email address, in name order. */
    @Transactional(readOnly = true)
    public List<Participant> recipients(String programId, Audience a) {
        Audience aud = a == null ? Audience.everyone() : a;
        String who = aud.who() == null ? "registered" : aud.who();
        Set<String> pay = lower(aud.payment());
        Set<String> lodging = lower(aud.lodging());
        Set<Integer> groups = aud.groups() == null ? Set.of() : new HashSet<>(aud.groups());
        Set<String> answers = lower(aud.questionValues());
        boolean byQuestion = aud.questionKey() != null && !aud.questionKey().isBlank() && !answers.isEmpty();
        List<Participant> out = new ArrayList<>();
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(programId)) {
            if (p.getEmail() == null || p.getEmail().isBlank()) continue;
            boolean waitlisted = p.getWaitlistedAt() != null;
            if ("registered".equals(who) && waitlisted) continue;
            if ("waitlist".equals(who) && !waitlisted) continue;
            if ("yes".equals(aud.checkedIn()) && p.getCheckedInAt() == null) continue;
            if ("no".equals(aud.checkedIn()) && p.getCheckedInAt() != null) continue;
            if (!pay.isEmpty() && !pay.contains(PaymentService.normalizeStatus(p.getPaymentStatus()))) continue;
            if (!lodging.isEmpty() && (p.getHotel() == null || p.getHotel().getHotelName() == null
                || !lodging.contains(p.getHotel().getHotelName().trim().toLowerCase(Locale.ROOT)))) continue;
            if (!groups.isEmpty() && (p.getBreakoutGroup() == null || !groups.contains(p.getBreakoutGroup()))) continue;
            if (byQuestion && !answerMatches(p.getCustomAnswers(), aud.questionKey(), answers)) continue;
            out.add(p);
        }
        out.sort(Comparator.comparing(p -> Optional.ofNullable(p.getFullName()).orElse("").toLowerCase(Locale.ROOT)));
        return out;
    }

    private boolean answerMatches(String answersJson, String key, Set<String> wanted) {
        if (answersJson == null || answersJson.isBlank()) return false;
        try {
            JsonNode v = json.readTree(answersJson).get(key);
            if (v == null || v.isNull()) return false;
            if (v.isArray()) {
                for (JsonNode x : v) if (wanted.contains(x.asText().trim().toLowerCase(Locale.ROOT))) return true;
                return false;
            }
            return wanted.contains(v.asText().trim().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    private static Set<String> lower(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values != null) for (String v : values) if (v != null && !v.isBlank()) out.add(v.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    /** A short description for the history list, e.g. "Everyone registered · Unpaid, Part paid · Age group: Youth". */
    public String label(String programId, Audience a) {
        Audience aud = a == null ? Audience.everyone() : a;
        List<String> parts = new ArrayList<>();
        parts.add(switch (aud.who() == null ? "registered" : aud.who()) {
            case "waitlist" -> "Waitlist";
            case "all" -> "Everyone, including the waitlist";
            default -> "Everyone registered";
        });
        if ("yes".equals(aud.checkedIn())) parts.add("Checked in");
        if ("no".equals(aud.checkedIn())) parts.add("Not checked in");
        if (aud.payment() != null && !aud.payment().isEmpty()) {
            parts.add(String.join(", ", aud.payment().stream().map(s -> switch (PaymentService.normalizeStatus(s)) {
                case PaymentService.PAID -> "Paid";
                case PaymentService.PARTIAL -> "Part paid";
                default -> "Unpaid";
            }).toList()));
        }
        if (aud.questionKey() != null && aud.questionValues() != null && !aud.questionValues().isEmpty()) {
            String q = fieldRepository.findByProgramIdOrderBySortOrderAscIdAsc(programId).stream()
                .filter(f -> aud.questionKey().equals(f.getFieldKey())).map(RegistrationField::getLabel).findFirst().orElse(aud.questionKey());
            parts.add(q + ": " + String.join(", ", aud.questionValues()));
        }
        if (aud.lodging() != null && !aud.lodging().isEmpty()) parts.add(String.join(", ", aud.lodging()));
        if (aud.groups() != null && !aud.groups().isEmpty()) {
            parts.add((aud.groups().size() == 1 ? "Group " : "Groups ") + String.join(", ", aud.groups().stream().map(String::valueOf).toList()));
        }
        String s = String.join(" · ", parts);
        return s.length() > 500 ? s.substring(0, 499) + "…" : s;
    }

    /** What the composer can filter by for this program. */
    @Transactional(readOnly = true)
    public Map<String, Object> options(Program p) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (RegistrationField f : fieldRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(p.getId())) {
            if (!RegistrationFieldService.isChoice(f.getType())) continue;
            List<String> opts = new ArrayList<>();
            try { json.readTree(Optional.ofNullable(f.getOptions()).orElse("[]")).forEach(o -> opts.add(o.asText())); } catch (Exception ignored) { /* no options */ }
            if (opts.isEmpty()) continue;
            questions.add(Map.of("key", f.getFieldKey(), "label", f.getLabel(), "options", opts));
        }
        List<String> lodging = jdbc.queryForList(
            "SELECT DISTINCT h.hotel_name FROM participants p JOIN hotels h ON h.id = p.hotel_id " +
            "WHERE p.program_id = ? AND h.hotel_name IS NOT NULL ORDER BY h.hotel_name", String.class, p.getId());
        List<Integer> groups = jdbc.queryForList(
            "SELECT DISTINCT breakout_group FROM participants WHERE program_id = ? AND breakout_group IS NOT NULL ORDER BY 1",
            Integer.class, p.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questions", questions);
        m.put("lodging", lodging);
        m.put("groups", groups);
        m.put("has_fee", FeeRules.hasAnyFee(p));
        m.put("waitlist_enabled", Boolean.TRUE.equals(p.getWaitlistEnabled()));
        return m;
    }

    public static void validate(String subject, String body) {
        if (subject == null || subject.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a subject");
        if (subject.trim().length() > MAX_SUBJECT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the subject under " + MAX_SUBJECT + " characters");
        if (body == null || body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a message");
        if (body.length() > MAX_BODY) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The message is too long (" + MAX_BODY + " characters at most)");
    }

    /**
     * Saves the announcement and its recipients ("sending"); the caller then starts the emails. The same
     * {@code requestId} twice (a double click, a retried request) returns the first one instead of sending again.
     */
    @Transactional
    public Announcement create(String programId, String subject, String body, Audience audience,
                               boolean showOnStatus, String requestId, String sentBy) {
        validate(subject, body);
        String rid = requestId != null && !requestId.isBlank() ? requestId.trim() : null;
        if (rid != null && rid.length() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request id");
        if (rid != null) {
            Optional<Announcement> same = repository.findByProgramIdAndRequestId(programId, rid);
            if (same.isPresent()) return same.get();
        }
        List<Participant> to = recipients(programId, audience);
        if (to.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nobody matches these filters");
        String audienceJson;
        try { audienceJson = json.writeValueAsString(audience == null ? Audience.everyone() : audience); }
        catch (Exception e) { audienceJson = null; }
        Announcement a = repository.save(Announcement.builder()
            .programId(programId)
            .subject(subject.trim())
            .body(body.trim())
            .audience(audienceJson)
            .audienceLabel(label(programId, audience))
            .showOnStatus(showOnStatus)
            .status(Announcement.SENDING)
            .recipients(to.size())
            .sentBy(sentBy)
            .requestId(rid)
            .createdAt(OffsetDateTime.now())
            .build());
        jdbc.batchUpdate("INSERT INTO announcement_recipients (announcement_id, participant_id) VALUES (?, ?)",
            to.stream().map(p -> new Object[]{a.getId(), p.getId()}).toList());
        return a;
    }

    /** Announcements shown on this attendee's page, newest first. */
    public List<Map<String, Object>> forAttendee(Integer participantId) {
        return jdbc.query(
            "SELECT a.id, a.subject, a.body, a.created_at FROM announcements a " +
            "JOIN announcement_recipients r ON r.announcement_id = a.id " +
            "WHERE r.participant_id = ? AND a.show_on_status ORDER BY a.created_at DESC LIMIT 20",
            (rs, i) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getInt("id"));
                m.put("subject", rs.getString("subject"));
                m.put("body", rs.getString("body"));
                m.put("sent_at", rs.getObject("created_at", OffsetDateTime.class).toString());
                return m;
            }, participantId);
    }
}
