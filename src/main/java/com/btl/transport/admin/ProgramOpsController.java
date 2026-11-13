package com.btl.transport.admin;

import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.payment.PaymentService;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.program.RegistrationRules;
import com.btl.transport.room.RoomAssignment;
import com.btl.transport.room.RoomAssignmentRepository;
import com.btl.transport.room.RoomOccupant;
import com.btl.transport.souvenir.ParticipantSouvenir;
import com.btl.transport.souvenir.SouvenirItem;
import com.btl.transport.souvenir.SouvenirItemRepository;
import com.btl.transport.souvenir.SouvenirService;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

/** Program policies, the overview, payment reminders, venue check-in and the activity log. */
@Tag(name = "Program operations")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class ProgramOpsController {

    private final ProgramRepository programRepository;
    private final ParticipantRepository participantRepository;
    private final PaymentService paymentService;
    private final RoomAssignmentRepository roomRepository;
    private final SouvenirItemRepository souvenirItemRepository;
    private final SouvenirService souvenirService;
    private final AuditLogRepository auditLogRepository;
    private final AdminAccess adminAccess;

    private Program program(String id) {
        Program p = programRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(p.getId());
        return p;
    }

    private Program current(String headerId) {
        String id = headerId != null && !headerId.isBlank() ? headerId : adminAccess.currentProgramId();
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        return program(id);
    }

    // ── Policies ────────────────────────────────────────────────────────────

    public record PoliciesRequest(
        @JsonProperty("show_payment_to_attendees") Boolean showPaymentToAttendees,
        @JsonProperty("registrations_see_amounts") Boolean registrationsSeeAmounts,
        @JsonProperty("payment_emails_enabled") Boolean paymentEmailsEnabled,
        @JsonProperty("show_breakout_group") Boolean showBreakoutGroup,
        @JsonProperty("ask_gender") Boolean askGender,
        @JsonProperty("ask_phone") Boolean askPhone,
        /** "YYYY-MM-DDTHH:mm" in the program's timezone; "" clears */
        @JsonProperty("registration_closes_at") String registrationClosesAt,
        /** 0 clears */
        @JsonProperty("max_registrations") Integer maxRegistrations
    ) {}

    private Map<String, Object> policies(Program p) {
        long registered = participantRepository.countByProgramId(p.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("show_payment_to_attendees", Boolean.TRUE.equals(p.getShowPaymentToAttendees()));
        m.put("registrations_see_amounts", !Boolean.FALSE.equals(p.getRegistrationsSeeAmounts()));
        m.put("payment_emails_enabled", Boolean.TRUE.equals(p.getPaymentEmailsEnabled()));
        m.put("show_breakout_group", !Boolean.FALSE.equals(p.getShowBreakoutGroup()));
        m.put("ask_gender", !Boolean.FALSE.equals(p.getAskGender()));
        m.put("ask_phone", !Boolean.FALSE.equals(p.getAskPhone()));
        m.put("registration_closes_at", p.getRegistrationClosesAt());
        m.put("max_registrations", p.getMaxRegistrations());
        m.put("registered", registered);
        m.put("registration_state", RegistrationRules.state(p, registered, OffsetDateTime.now()));
        m.put("timezone", p.getTimezone());
        return m;
    }

    @Operation(summary = "Program policies")
    @GetMapping("/programs/{id}/policies")
    public Map<String, Object> getPolicies(@PathVariable String id) {
        return policies(program(id));
    }

    @Operation(summary = "Update program policies", description = "Payment visibility, payment emails, breakout group visibility, registration close date and capacity")
    @PatchMapping("/programs/{id}/policies")
    @Transactional
    public Map<String, Object> updatePolicies(@PathVariable String id, @RequestBody PoliciesRequest req) {
        Program p = program(id);
        if (req.showPaymentToAttendees() != null) p.setShowPaymentToAttendees(req.showPaymentToAttendees());
        if (req.registrationsSeeAmounts() != null) p.setRegistrationsSeeAmounts(req.registrationsSeeAmounts());
        if (req.paymentEmailsEnabled() != null) p.setPaymentEmailsEnabled(req.paymentEmailsEnabled());
        if (req.showBreakoutGroup() != null) p.setShowBreakoutGroup(req.showBreakoutGroup());
        if (req.askGender() != null) p.setAskGender(req.askGender());
        if (req.askPhone() != null) p.setAskPhone(req.askPhone());
        if (req.registrationClosesAt() != null) {
            String v = req.registrationClosesAt().trim();
            if (v.isEmpty()) p.setRegistrationClosesAt(null);
            else {
                try { LocalDateTime.parse(v); } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closing time must look like 2027-02-20T23:59");
                }
                p.setRegistrationClosesAt(v.length() > 16 ? v.substring(0, 16) : v);
            }
        }
        if (req.maxRegistrations() != null) {
            if (req.maxRegistrations() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity can't be negative");
            p.setMaxRegistrations(req.maxRegistrations() == 0 ? null : req.maxRegistrations());
        }
        return policies(programRepository.save(p));
    }

    // ── Overview ────────────────────────────────────────────────────────────

    @Operation(summary = "Program overview", description = "Counts for registrations, payments, rooms, souvenirs and check-in — limited to what the caller's permissions cover")
    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Map<String, Object> overview(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        Program prog = current(programId);
        Set<String> perms = adminAccess.permissions();
        boolean full = perms.contains(AdminPermissions.FULL);
        List<Participant> people = participantRepository.findAllByProgramIdWithHotel(prog.getId());
        Map<String, Object> out = new LinkedHashMap<>();

        long registered = people.size();
        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("registered", registered);
        reg.put("checked_in", people.stream().filter(p -> p.getCheckedInAt() != null).count());
        reg.put("state", RegistrationRules.state(prog, registered, OffsetDateTime.now()));
        reg.put("closes_at", prog.getRegistrationClosesAt());
        reg.put("max", prog.getMaxRegistrations());
        out.put("registration", reg);

        if (full || perms.contains(AdminPermissions.REGISTRATIONS) || perms.contains(AdminPermissions.PAYMENTS)) {
            Map<String, Object> pay = new LinkedHashMap<>();
            Map<String, Long> byStatus = new LinkedHashMap<>(Map.of(PaymentService.PAID, 0L, PaymentService.PARTIAL, 0L, PaymentService.UNPAID, 0L));
            for (Participant p : people) byStatus.merge(PaymentService.normalizeStatus(p.getPaymentStatus()), 1L, Long::sum);
            pay.put("paid", byStatus.get(PaymentService.PAID));
            pay.put("partial", byStatus.get(PaymentService.PARTIAL));
            pay.put("unpaid", byStatus.get(PaymentService.UNPAID));
            pay.put("currency", prog.getFeeCurrency() != null ? prog.getFeeCurrency() : "USD");
            pay.put("emails_enabled", Boolean.TRUE.equals(prog.getPaymentEmailsEnabled()));
            if (PaymentService.canSeeAmounts(prog, perms)) {
                Map<Integer, BigDecimal> totals = paymentService.totals(people.stream().map(Participant::getId).toList());
                BigDecimal collected = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal fee = prog.getRegistrationFee();
                BigDecimal outstanding = null;
                if (fee != null) {
                    outstanding = BigDecimal.ZERO;
                    for (Participant p : people) {
                        if (PaymentService.PAID.equals(PaymentService.normalizeStatus(p.getPaymentStatus()))) continue;
                        outstanding = outstanding.add(fee.subtract(totals.getOrDefault(p.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO));
                    }
                }
                pay.put("fee", fee);
                pay.put("collected", collected);
                pay.put("outstanding", outstanding);
                pay.put("expected", fee != null ? fee.multiply(BigDecimal.valueOf(registered)) : null);
            }
            out.put("payments", pay);
        }

        if (full || perms.contains(AdminPermissions.ACCOMMODATION)) {
            int rooms = 0, beds = 0, filled = 0;
            for (RoomAssignment r : roomRepository.findByProgramIdWithOccupants(prog.getId())) {
                rooms++;
                for (RoomOccupant o : r.getOccupants()) {
                    beds++;
                    if (o.getParticipant() != null || (o.getName() != null && !o.getName().isBlank())) filled++;
                }
            }
            out.put("rooms", Map.of("rooms", rooms, "beds", beds, "filled", filled));
        }

        if (full || perms.contains(AdminPermissions.SOUVENIRS)) {
            Map<Integer, Map<Integer, ParticipantSouvenir>> recs = souvenirService.recordsForProgram(prog.getId());
            List<Map<String, Object>> items = new ArrayList<>();
            for (SouvenirItem i : souvenirItemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(prog.getId())) {
                long given = people.stream().filter(p -> {
                    ParticipantSouvenir r = recs.getOrDefault(p.getId(), Map.of()).get(i.getId());
                    return r != null && Boolean.TRUE.equals(r.getReceived());
                }).count();
                items.add(Map.of("name", i.getName(), "given", given, "total", registered));
            }
            out.put("souvenirs", items);
        }
        return out;
    }

    // ── Payment reminders ───────────────────────────────────────────────────

    public record RemindersRequest(List<String> statuses) {}

    private static final java.time.Duration REMINDER_COOLDOWN = java.time.Duration.ofMinutes(10);
    private final Map<String, OffsetDateTime> reminderClaims = new java.util.concurrent.ConcurrentHashMap<>();

    @Operation(summary = "Email payment reminders", description = "Sends to everyone unpaid / part paid (or the given statuses). Needs payment emails turned on.")
    @PostMapping("/payments/reminders")
    public Map<String, Object> reminders(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                         @RequestBody(required = false) RemindersRequest req) {
        Program prog = current(programId);
        // One send per program every 10 minutes. The in-memory claim is atomic, so a double-click can't send twice;
        // the activity log covers restarts.
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime window = now.minus(REMINDER_COOLDOWN);
        boolean[] claimed = {false};
        reminderClaims.compute(prog.getId(), (k, last) -> {
            if (last != null && last.isAfter(window)) return last;
            claimed[0] = true;
            return now;
        });
        if (!claimed[0] || auditLogRepository.existsByProgramIdAndActionAndCreatedAtAfter(prog.getId(), "Sent payment reminders", window)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Reminders were just sent — try again in a few minutes");
        }
        try {
            return Map.of("sent", paymentService.sendReminders(prog.getId(), req == null ? null : req.statuses()));
        } catch (ResponseStatusException e) {
            // Refused before anything was sent (e.g. payment emails are off): allow a retry right away.
            // Any other failure may have happened part-way through, so the cooldown stays to avoid double emails.
            reminderClaims.remove(prog.getId(), now);
            throw e;
        }
    }

    // ── Check-in ────────────────────────────────────────────────────────────

    @Operation(summary = "Check-in list", description = "Everyone in the program with what the desk needs: arrival, payment status, room, souvenirs to collect")
    @GetMapping("/check-in")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> checkInList(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        Program prog = current(programId);
        Map<Integer, String> roomOf = new HashMap<>();
        for (RoomAssignment r : roomRepository.findByProgramIdWithOccupants(prog.getId())) {
            String label = String.join(" · ", Arrays.stream(new String[]{r.getHotelName(), r.getRoomLabel()})
                .filter(s -> s != null && !s.isBlank()).toList());
            for (RoomOccupant o : r.getOccupants()) if (o.getParticipant() != null) roomOf.put(o.getParticipant().getId(), label);
        }
        List<SouvenirItem> items = souvenirItemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(prog.getId());
        Map<Integer, Map<Integer, ParticipantSouvenir>> recs = items.isEmpty() ? Map.of() : souvenirService.recordsForProgram(prog.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(prog.getId())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("btl_code", p.getBtlCode());
            m.put("full_name", p.getFullName());
            m.put("email", p.getEmail());
            m.put("phone", p.getPhone());
            m.put("checked_in_at", p.getCheckedInAt() != null ? p.getCheckedInAt().toString() : null);
            m.put("checked_in_by", p.getCheckedInBy());
            m.put("payment_status", PaymentService.normalizeStatus(p.getPaymentStatus()));
            m.put("room", roomOf.getOrDefault(p.getId(), p.getHotel() != null ? p.getHotel().getHotelName() : null));
            Map<Integer, ParticipantSouvenir> mine = recs.getOrDefault(p.getId(), Map.of());
            m.put("souvenirs_pending", items.stream().filter(i -> { var r = mine.get(i.getId()); return r == null || !Boolean.TRUE.equals(r.getReceived()); })
                .map(SouvenirItem::getName).toList());
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("full_name")).toLowerCase(Locale.ROOT)));
        return out;
    }

    public record CheckInRequest(@JsonProperty("checked_in") Boolean checkedIn) {}

    @Operation(summary = "Check a participant in (or undo)")
    @PutMapping("/participants/{code}/check-in")
    @Transactional
    public Map<String, Object> checkIn(@PathVariable String code, @RequestBody CheckInRequest req) {
        Participant p = participantRepository.findByBtlCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(p.getProgramId());
        boolean in = !Boolean.FALSE.equals(req.checkedIn());
        Audit.action(in ? "Checked in" : "Undid check-in for");
        if (in && p.getCheckedInAt() == null) { p.setCheckedInAt(OffsetDateTime.now()); p.setCheckedInBy(adminAccess.currentUsername()); }
        if (!in) { p.setCheckedInAt(null); p.setCheckedInBy(null); }
        participantRepository.save(p);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checked_in_at", p.getCheckedInAt() != null ? p.getCheckedInAt().toString() : null);
        m.put("checked_in_by", p.getCheckedInBy());
        return m;
    }

    // ── Activity log ────────────────────────────────────────────────────────

    private static final int ACTIVITY_SCAN_LIMIT = 5000;
    private static final java.util.regex.Pattern RAW_ACTION = java.util.regex.Pattern.compile("^(POST|PUT|PATCH|DELETE) /.*");

    @Operation(summary = "Activity log",
        description = "Most recent admin changes first. Filters: category (checkin, participants, payments, souvenirs, accommodation, "
            + "transport, form, settings, team, other), user, q (name, code or detail). Pass ?before={id} for older entries.")
    @GetMapping("/activity")
    public List<Map<String, Object>> activity(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                              @RequestParam(required = false) Long before,
                                              @RequestParam(defaultValue = "50") int limit,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) String user,
                                              @RequestParam(required = false) String q) {
        Program prog = current(programId);
        int want = Math.max(1, Math.min(limit, 200));
        String cat = category == null || category.isBlank() ? null : category.trim();
        String who = user == null || user.isBlank() ? null : user.trim();
        String needle = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        boolean filtered = cat != null || needle != null;
        int batch = filtered ? 200 : want;

        List<Map<String, Object>> out = new ArrayList<>();
        Long cursor = before;
        int scanned = 0;
        // Filters on category/search are applied here (older rows have no stored category), scanning newest first.
        while (out.size() < want && scanned < ACTIVITY_SCAN_LIMIT) {
            var page = PageRequest.of(0, batch);
            List<AuditLog> rows = who == null
                ? (cursor == null ? auditLogRepository.findByProgramIdOrderByIdDesc(prog.getId(), page)
                                  : auditLogRepository.findByProgramIdAndIdLessThanOrderByIdDesc(prog.getId(), cursor, page))
                : (cursor == null ? auditLogRepository.findByProgramIdAndUsernameOrderByIdDesc(prog.getId(), who, page)
                                  : auditLogRepository.findByProgramIdAndUsernameAndIdLessThanOrderByIdDesc(prog.getId(), who, cursor, page));
            if (rows.isEmpty()) break;
            scanned += rows.size();
            Map<String, String> oldNames = participantNamesForOldRows(rows);
            for (AuditLog a : rows) {
                Map<String, Object> m = activityEntry(a, oldNames);
                if (cat != null && !cat.equals(m.get("category"))) continue;
                if (needle != null && !matches(m, needle)) continue;
                out.add(m);
                if (out.size() == want) break;
            }
            cursor = rows.get(rows.size() - 1).getId();
            if (rows.size() < batch) break;
        }
        return out;
    }

    @Operation(summary = "Admins who appear in the activity log", description = "For the activity page's person filter")
    @GetMapping("/activity/people")
    public List<String> activityPeople(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        return auditLogRepository.findUsernames(current(programId).getId());
    }

    /** Rows logged before V57 carry no name; look participants up by code in one query. */
    private Map<String, String> participantNamesForOldRows(List<AuditLog> rows) {
        Set<String> codes = new HashSet<>();
        for (AuditLog a : rows) {
            if (a.getCategory() != null || a.getTargetName() != null) continue;
            AuditCatalog.Match m = AuditCatalog.match(a.getMethod(), a.getPath());
            if (m.kind() == AuditCatalog.Kind.PARTICIPANT_CODE && m.target() != null) codes.add(m.target());
        }
        Map<String, String> names = new HashMap<>();
        if (!codes.isEmpty()) participantRepository.findByBtlCodeIn(codes).forEach(p -> names.put(p.getBtlCode(), p.getFullName()));
        return names;
    }

    private Map<String, Object> activityEntry(AuditLog a, Map<String, String> oldNames) {
        String category = a.getCategory(), action = a.getAction(), target = a.getTarget(), name = a.getTargetName();
        if (category == null) {
            // Logged before categories existed: describe it from the stored method + path.
            AuditCatalog.Match m = AuditCatalog.match(a.getMethod(), a.getPath());
            category = m.category();
            if (!"other".equals(m.category()) || RAW_ACTION.matcher(String.valueOf(action)).matches()) action = m.action();
            if (m.target() != null) target = m.target();
            if (name == null && m.kind() == AuditCatalog.Kind.PARTICIPANT_CODE) name = oldNames.get(m.target());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("username", a.getUsername());
        m.put("action", action);
        m.put("category", category);
        m.put("target", target);
        m.put("target_name", name);
        m.put("detail", a.getDetail());
        m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        return m;
    }

    private static boolean matches(Map<String, Object> m, String needle) {
        for (String k : List.of("target", "target_name", "detail", "action", "username")) {
            Object v = m.get(k);
            if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }
}
