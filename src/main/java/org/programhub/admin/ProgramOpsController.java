package org.programhub.admin;

import org.programhub.participant.Participant;
import org.programhub.participant.ParticipantRepository;
import org.programhub.payment.PaymentService;
import org.programhub.program.Program;
import org.programhub.program.ProgramRepository;
import org.programhub.program.RegistrationRules;
import org.programhub.room.RoomAssignment;
import org.programhub.room.RoomAssignmentRepository;
import org.programhub.room.RoomOccupant;
import org.programhub.souvenir.ParticipantSouvenir;
import org.programhub.souvenir.SouvenirItem;
import org.programhub.souvenir.SouvenirItemRepository;
import org.programhub.souvenir.SouvenirService;
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
import java.io.IOException;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.programhub.notification.NotificationService;
import org.programhub.participant.ParticipantService;
import org.programhub.payment.ParticipantPaymentRepository;
import org.programhub.program.FeeRules;
import org.programhub.program.ProgramPaymentQr;
import org.programhub.program.ProgramPaymentQrRepository;
import org.programhub.program.RoomVisibility;
import org.programhub.registration.RegistrationFieldService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

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
    private final ParticipantPaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final ParticipantService participantService;
    private final ProgramPaymentQrRepository qrRepository;
    private final RegistrationFieldService registrationFieldService;

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
        @JsonProperty("max_registrations") Integer maxRegistrations,
        @JsonProperty("show_fee_on_landing") Boolean showFeeOnLanding,
        /** "" = the default ("Registration fee") */
        @JsonProperty("fee_label") String feeLabel,
        @JsonProperty("waitlist_enabled") Boolean waitlistEnabled,
        @JsonProperty("payment_reminders_enabled") Boolean paymentRemindersEnabled,
        /** 0 = no automatic reminder */
        @JsonProperty("auto_reminder_days") Integer autoReminderDays,
        @JsonProperty("attendee_self_edit") Boolean attendeeSelfEdit,
        /** How to pay (Zelle details etc.); "" clears */
        @JsonProperty("payment_instructions") String paymentInstructions,
        /** Fee by answer: the question key ("" = off) and {option: amount} (blank amount = normal fee) */
        @JsonProperty("fee_question_key") String feeQuestionKey,
        @JsonProperty("fee_by_option") Map<String, Object> feeByOption,
        /** always | checkin | date | checkin_or_date */
        @JsonProperty("room_visibility") String roomVisibility,
        /** "YYYY-MM-DDTHH:mm" in program time; "" clears */
        @JsonProperty("room_visible_from") String roomVisibleFrom
    ) {}

    private Map<String, Object> policies(Program p) {
        long registered = participantRepository.countByProgramIdAndWaitlistedAtIsNull(p.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("show_payment_to_attendees", Boolean.TRUE.equals(p.getShowPaymentToAttendees()));
        m.put("registrations_see_amounts", !Boolean.FALSE.equals(p.getRegistrationsSeeAmounts()));
        m.put("payment_emails_enabled", Boolean.TRUE.equals(p.getPaymentEmailsEnabled()));
        m.put("show_breakout_group", !Boolean.FALSE.equals(p.getShowBreakoutGroup()));
        m.put("ask_gender", !Boolean.FALSE.equals(p.getAskGender()));
        m.put("ask_phone", !Boolean.FALSE.equals(p.getAskPhone()));
        m.put("registration_closes_at", p.getRegistrationClosesAt());
        m.put("max_registrations", p.getMaxRegistrations());
        m.put("show_fee_on_landing", Boolean.TRUE.equals(p.getShowFeeOnLanding()));
        m.put("fee_label", p.getFeeLabel());
        m.put("waitlist_enabled", Boolean.TRUE.equals(p.getWaitlistEnabled()));
        m.put("waitlisted", participantRepository.countByProgramId(p.getId()) - registered);
        m.put("payment_reminders_enabled", Boolean.TRUE.equals(p.getPaymentRemindersEnabled()));
        m.put("auto_reminder_days", p.getAutoReminderDays());
        m.put("auto_reminder_sent_at", p.getAutoReminderSentAt() != null ? p.getAutoReminderSentAt().toString() : null);
        m.put("start_date", p.getStartDate());
        m.put("attendee_self_edit", Boolean.TRUE.equals(p.getAttendeeSelfEdit()));
        m.put("payment_instructions", p.getPaymentInstructions());
        m.put("has_payment_qr", qrRepository.existsById(p.getId()));
        m.put("fee_question_key", FeeRules.byAnswer(p) ? p.getFeeQuestionKey() : null);
        m.put("fee_by_option", FeeRules.optionFees(p));
        m.put("room_visibility", RoomVisibility.mode(p));
        m.put("room_visible_from", p.getRoomVisibleFrom());
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
        if (req.showFeeOnLanding() != null) p.setShowFeeOnLanding(req.showFeeOnLanding());
        if (req.feeLabel() != null) {
            String label = req.feeLabel().trim();
            if (label.length() > 40) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the fee label to 40 characters");
            p.setFeeLabel(label.isEmpty() ? null : label);
        }
        if (req.waitlistEnabled() != null) p.setWaitlistEnabled(req.waitlistEnabled());
        if (req.paymentRemindersEnabled() != null) p.setPaymentRemindersEnabled(req.paymentRemindersEnabled());
        if (req.autoReminderDays() != null) {
            int d = req.autoReminderDays();
            if (d < 0 || d > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between 1 and 60 days before the program");
            Integer next = d == 0 ? null : d;
            // A new timing is a new reminder: it may go out again.
            if (!Objects.equals(next, p.getAutoReminderDays())) p.setAutoReminderSentAt(null);
            p.setAutoReminderDays(next);
        }
        if (req.attendeeSelfEdit() != null) p.setAttendeeSelfEdit(req.attendeeSelfEdit());
        if (req.paymentInstructions() != null) {
            String t = req.paymentInstructions().trim();
            if (t.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep payment instructions under 2000 characters");
            p.setPaymentInstructions(t.isEmpty() ? null : t);
        }
        boolean feesChanged = false;
        if (req.feeQuestionKey() != null || req.feeByOption() != null) {
            String before = p.getFeeQuestionKey() + "|" + p.getFeeByOption();
            String key = req.feeQuestionKey() != null ? req.feeQuestionKey().trim() : p.getFeeQuestionKey();
            if (key == null || key.isEmpty()) {
                p.setFeeQuestionKey(null);
                p.setFeeByOption(null);
            } else {
                var field = registrationFieldService.activeField(p.getId(), key);
                if (field == null || !List.of("select", "radio").contains(field.getType()))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a dropdown or single-choice question");
                List<String> options = registrationFieldService.options(field);
                Map<String, BigDecimal> amounts = new LinkedHashMap<>();
                Map<String, Object> given = req.feeByOption() != null ? req.feeByOption()
                    : new LinkedHashMap<>(FeeRules.optionFees(p));
                for (var e : given.entrySet()) {
                    if (e.getValue() == null || String.valueOf(e.getValue()).isBlank()) continue;
                    if (!options.contains(e.getKey()))
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + e.getKey() + "\" isn't an option of that question");
                    BigDecimal v;
                    try { v = new BigDecimal(String.valueOf(e.getValue()).trim()).setScale(2, RoundingMode.HALF_UP); }
                    catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter amounts as numbers, e.g. 280"); }
                    if (v.signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fees can't be negative");
                    amounts.put(e.getKey(), v);
                }
                p.setFeeQuestionKey(key);
                p.setFeeByOption(FeeRules.toJson(amounts));
            }
            feesChanged = !before.equals(p.getFeeQuestionKey() + "|" + p.getFeeByOption());
        }
        if (req.roomVisibility() != null) {
            String m = req.roomVisibility().trim();
            if (!RoomVisibility.MODES.contains(m))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose always, checkin, date or checkin_or_date");
            p.setRoomVisibility(m.equals(RoomVisibility.ALWAYS) ? null : m);
        }
        if (req.roomVisibleFrom() != null) {
            String v = req.roomVisibleFrom().trim();
            if (v.isEmpty()) p.setRoomVisibleFrom(null);
            else {
                try { LocalDateTime.parse(v); } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date and time must look like 2027-07-02T15:00");
                }
                p.setRoomVisibleFrom(v.length() > 16 ? v.substring(0, 16) : v);
            }
        }
        Program saved = programRepository.save(p);
        if (feesChanged) {
            programRepository.flush();
            int changed = paymentService.recalculateForProgram(saved.getId());
            if (changed > 0) Audit.detail("Fees changed · " + changed + " payment status" + (changed == 1 ? "" : "es") + " updated");
        }
        return policies(saved);
    }

    // ── Attendee lookup (help desk) ───────────────────────────────────────

    @Operation(summary = "Find an attendee", description = "Search by name, code, email or phone (any admin of the program); up to 25 matches")
    @GetMapping("/lookup")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lookup(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                            @RequestParam(value = "q", defaultValue = "") String q) {
        Program prog = current(programId);
        String needle = q.trim().toLowerCase(Locale.ROOT);
        String digits = q.replaceAll("\\D", "");
        if (needle.length() < 2) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(prog.getId())) {
            boolean hit = (p.getFullName() != null && p.getFullName().toLowerCase(Locale.ROOT).contains(needle))
                || (p.getRegCode() != null && p.getRegCode().toLowerCase(Locale.ROOT).contains(needle))
                || (p.getEmail() != null && p.getEmail().toLowerCase(Locale.ROOT).contains(needle))
                || (digits.length() >= 4 && p.getPhone() != null && p.getPhone().replaceAll("\\D", "").contains(digits));
            if (!hit) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reg_code", p.getRegCode());
            m.put("full_name", p.getFullName());
            m.put("email", p.getEmail());
            m.put("phone", p.getPhone());
            m.put("checked_in", p.getCheckedInAt() != null);
            m.put("waitlisted", p.getWaitlistedAt() != null);
            out.add(m);
        }
        // Best matches first: names that start with the search, then the rest alphabetically.
        out.sort(Comparator.<Map<String, Object>, Boolean>comparing(m -> !String.valueOf(m.get("full_name")).toLowerCase(Locale.ROOT).startsWith(needle))
            .thenComparing(m -> String.valueOf(m.get("full_name")).toLowerCase(Locale.ROOT)));
        return out.size() > 25 ? out.subList(0, 25) : out;
    }

    // ── Room visibility (Accommodation admins) ─────────────────────────────

    public record RoomVisibilityRequest(@JsonProperty("room_visibility") String roomVisibility,
                                        @JsonProperty("room_visible_from") String roomVisibleFrom) {}

    @Operation(summary = "When attendees see their room", description = "always | checkin | date | checkin_or_date, with a date/time in program time")
    @PatchMapping("/programs/{id}/room-visibility")
    @Transactional
    public Map<String, Object> updateRoomVisibility(@PathVariable String id, @RequestBody RoomVisibilityRequest req) {
        return updatePolicies(id, new PoliciesRequest(null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, req.roomVisibility(), req.roomVisibleFrom()));
    }

    // ── Payment QR code ────────────────────────────────────────────────────

    @Operation(summary = "Upload the payment QR code", description = "PNG or JPEG up to 2 MB; shown after registering, in emails and on the attendee page")
    @PostMapping(value = "/programs/{id}/payment-qr", consumes = "multipart/form-data")
    @Transactional
    public Map<String, Object> uploadPaymentQr(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException {
        Program p = program(id);
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!type.equals("image/png") && !type.equals("image/jpeg"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PNG or JPEG image");
        if (file.getSize() > 2_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep the image under 2 MB");
        qrRepository.save(ProgramPaymentQr.builder()
            .programId(p.getId()).contentType(type).data(file.getBytes()).updatedAt(OffsetDateTime.now()).build());
        return policies(p);
    }

    @Operation(summary = "Remove the payment QR code")
    @DeleteMapping("/programs/{id}/payment-qr")
    @Transactional
    public Map<String, Object> deletePaymentQr(@PathVariable String id) {
        Program p = program(id);
        if (qrRepository.existsById(p.getId())) qrRepository.deleteById(p.getId());
        return policies(p);
    }

    // ── Payments ledger export ─────────────────────────────────────────────

    private static String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        // Neutralise spreadsheet formulas, then quote.
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Operation(summary = "Payments ledger (CSV)", description = "Every payment and refund recorded for the program, oldest first")
    @GetMapping(value = "/payments/export.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> ledger(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        Program prog = current(programId);
        if (!PaymentService.canSeeAmounts(prog, adminAccess.permissions())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Amounts are hidden for your access");
        Map<Integer, Participant> byId = new HashMap<>();
        for (Participant p : participantRepository.findAllByProgramIdWithHotel(prog.getId())) byId.put(p.getId(), p);
        String cur = prog.getFeeCurrency() != null ? prog.getFeeCurrency() : "USD";
        ZoneId zone;
        try { zone = ZoneId.of(prog.getTimezone() != null ? prog.getTimezone() : "America/New_York"); }
        catch (Exception e) { zone = ZoneId.of("America/New_York"); }
        DateTimeFormatter when = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder out = new StringBuilder("Date,Name,Code,Type,Method,Amount,Currency,Note,Recorded by\n");
        for (var pay : paymentRepository.findByProgramIdOrderByRecordedAtAsc(prog.getId())) {
            Participant p = byId.get(pay.getParticipantId());
            boolean refund = PaymentService.REFUND.equals(pay.getKind());
            BigDecimal amt = refund ? pay.getAmount().negate() : pay.getAmount();
            out.append(String.join(",",
                csv(pay.getRecordedAt() != null ? pay.getRecordedAt().atZoneSameInstant(zone).format(when) : ""),
                csv(p != null ? p.getFullName() : "(removed)"), csv(p != null ? p.getRegCode() : ""),
                csv(refund ? "Refund" : "Payment"), csv(pay.getMethod()), csv(amt.toPlainString()), csv(cur),
                csv(pay.getNote()), csv(pay.getRecordedBy()))).append("\n");
        }
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"payments.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(out.toString());
    }

    // ── Waitlist ───────────────────────────────────────────────────────────

    @Operation(summary = "Give a waitlisted person a place", description = "Takes them off the waitlist and emails them that they're in")
    @PostMapping("/participants/{code}/admit")
    @Transactional
    public Map<String, Object> admit(@PathVariable String code) {
        Participant p = participantRepository.findByRegCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        adminAccess.requireProgram(p.getProgramId());
        if (p.getWaitlistedAt() == null) return Map.of("waitlisted", false);
        p.setWaitlistedAt(null);
        participantService.onAdmitted(p);
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        try { notificationService.sendWaitlistPlace(p); } catch (Exception ignored) { /* the place stands even if the email fails */ }
        return Map.of("waitlisted", false);
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

        long waitlisted = people.stream().filter(p -> p.getWaitlistedAt() != null).count();
        long registered = people.size() - waitlisted;
        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("registered", registered);
        reg.put("waitlisted", waitlisted);
        reg.put("checked_in", people.stream().filter(p -> p.getCheckedInAt() != null).count());
        reg.put("state", RegistrationRules.state(prog, registered, OffsetDateTime.now()));
        reg.put("closes_at", prog.getRegistrationClosesAt());
        reg.put("max", prog.getMaxRegistrations());
        out.put("registration", reg);

        if (full || perms.contains(AdminPermissions.REGISTRATIONS) || perms.contains(AdminPermissions.PAYMENTS)) {
            Map<String, Object> pay = new LinkedHashMap<>();
            Map<String, Long> byStatus = new LinkedHashMap<>(Map.of(PaymentService.PAID, 0L, PaymentService.PARTIAL, 0L, PaymentService.UNPAID, 0L));
            List<Participant> placed = people.stream().filter(p -> p.getWaitlistedAt() == null).toList();
            for (Participant p : placed) byStatus.merge(PaymentService.normalizeStatus(p.getPaymentStatus()), 1L, Long::sum);
            pay.put("paid", byStatus.get(PaymentService.PAID));
            pay.put("partial", byStatus.get(PaymentService.PARTIAL));
            pay.put("unpaid", byStatus.get(PaymentService.UNPAID));
            pay.put("currency", prog.getFeeCurrency() != null ? prog.getFeeCurrency() : "USD");
            pay.put("emails_enabled", Boolean.TRUE.equals(prog.getPaymentEmailsEnabled()));
            pay.put("reminders_enabled", Boolean.TRUE.equals(prog.getPaymentRemindersEnabled()));
            if (PaymentService.canSeeAmounts(prog, perms)) {
                Map<Integer, BigDecimal> totals = paymentService.totals(people.stream().map(Participant::getId).toList());
                BigDecimal collected = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal fee = prog.getRegistrationFee();
                BigDecimal outstanding = null, expected = null;
                if (FeeRules.hasAnyFee(prog)) {
                    outstanding = BigDecimal.ZERO;
                    expected = BigDecimal.ZERO;
                    for (Participant p : placed) {
                        BigDecimal mine = FeeRules.feeFor(prog, p);  // by their answer when fees differ
                        if (mine == null) continue;
                        expected = expected.add(mine);
                        if (PaymentService.PAID.equals(PaymentService.normalizeStatus(p.getPaymentStatus()))) continue;
                        outstanding = outstanding.add(mine.subtract(totals.getOrDefault(p.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO));
                    }
                }
                pay.put("fee", fee);
                pay.put("collected", collected);
                pay.put("outstanding", outstanding);
                pay.put("expected", expected);
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

    private static final Duration REMINDER_COOLDOWN = Duration.ofMinutes(10);
    private final Map<String, OffsetDateTime> reminderClaims = new ConcurrentHashMap<>();

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
            if (p.getWaitlistedAt() != null) continue;  // no place yet: not expected at the desk
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reg_code", p.getRegCode());
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
        Participant p = participantRepository.findByRegCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
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
    private static final Pattern RAW_ACTION = Pattern.compile("^(POST|PUT|PATCH|DELETE) /.*");

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
        if (!codes.isEmpty()) participantRepository.findByRegCodeIn(codes).forEach(p -> names.put(p.getRegCode(), p.getFullName()));
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
