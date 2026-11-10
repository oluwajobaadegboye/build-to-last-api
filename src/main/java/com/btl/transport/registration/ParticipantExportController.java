package com.btl.transport.registration;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.payment.PaymentService;
import com.btl.transport.souvenir.ParticipantSouvenir;
import com.btl.transport.souvenir.SouvenirItem;
import com.btl.transport.souvenir.SouvenirItemRepository;
import com.btl.transport.souvenir.SouvenirService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** CSV of every participant in the program, with one column per custom registration question; columns can be picked. */
@Tag(name = "Participants export")
@RestController
@RequestMapping("/api/v1/admin/participants")
@RequiredArgsConstructor
public class ParticipantExportController {

    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final RegistrationFieldRepository fieldRepository;
    private final RegistrationFieldService fieldService;
    private final PaymentService paymentService;
    private final SouvenirItemRepository souvenirItemRepository;
    private final SouvenirService souvenirService;
    private final com.btl.transport.admin.AdminAccess adminAccess;
    private final com.btl.transport.program.ProgramRepository programRepository;

    /** One CSV column: a stable id (used to pick columns), its heading, the group it's listed under, and how to fill it. */
    record Column(String id, String label, String group, java.util.function.Function<Row, String> value) {}

    /** Everything one participant's row is built from. */
    record Row(Participant p, Flight arr, Flight dep, Map<String, Object> answers, Map<Integer, ParticipantSouvenir> souvenirs, java.math.BigDecimal paid) {}

    /** Every column the export can include, in default order. */
    private List<Column> columns(String programId, boolean showAmounts) {
        List<Column> c = new ArrayList<>();
        String person = "Personal details", travel = "Travel & stay", prog = "Program";
        c.add(new Column("code", "Code", prog, r -> s(r.p().getBtlCode())));
        c.add(new Column("full_name", "Full name", person, r -> s(r.p().getFullName())));
        c.add(new Column("first_name", "First name", person, r -> s(r.p().getFirstName())));
        c.add(new Column("last_name", "Last name", person, r -> s(r.p().getLastName())));
        c.add(new Column("gender", "Gender", person, r -> genderLabel(r.p().getGender())));
        c.add(new Column("email", "Email", person, r -> s(r.p().getEmail())));
        c.add(new Column("phone", "Phone", person, r -> s(r.p().getPhone())));
        c.add(new Column("state", "State", person, r -> s(r.p().getState())));
        c.add(new Column("status", "Status", prog, r -> r.p().getStatus() != null ? r.p().getStatus().name().toLowerCase() : ""));
        c.add(new Column("hotel", "Hotel", travel, r -> r.p().getHotel() != null ? s(r.p().getHotel().getHotelName()) : ""));
        c.add(new Column("shuttle", "Shuttle opt-in", travel, r -> Boolean.TRUE.equals(r.p().getShuttleOptIn()) ? "Yes" : "No"));
        c.add(new Column("arrival_flight", "Arrival flight", travel, r -> flightLabel(r.arr())));
        c.add(new Column("arrival_time", "Arrival time", travel, r -> r.arr() != null && r.arr().getSubmittedDatetime() != null ? r.arr().getSubmittedDatetime().toString() : ""));
        c.add(new Column("departure_flight", "Departure flight", travel, r -> flightLabel(r.dep())));
        c.add(new Column("departure_time", "Departure time", travel, r -> r.dep() != null && r.dep().getSubmittedDatetime() != null ? r.dep().getSubmittedDatetime().toString() : ""));
        c.add(new Column("breakout_group", "Breakout group", prog, r -> r.p().getBreakoutGroup() != null ? String.valueOf(r.p().getBreakoutGroup()) : ""));
        c.add(new Column("registered_at", "Registered at", prog, r -> r.p().getCreatedAt() != null ? r.p().getCreatedAt().toString() : ""));
        c.add(new Column("payment_status", "Payment status", prog, r -> switch (PaymentService.normalizeStatus(r.p().getPaymentStatus())) {
            case PaymentService.PAID -> "Paid"; case PaymentService.PARTIAL -> "Part paid"; default -> "Unpaid"; }));
        c.add(new Column("amount_paid", "Amount paid", prog, r -> showAmounts && r.paid() != null ? r.paid().toPlainString() : ""));
        for (RegistrationField f : fieldRepository.findByProgramIdOrderBySortOrderAscIdAsc(programId)) {
            String label = Boolean.TRUE.equals(f.getActive()) ? f.getLabel() : f.getLabel() + " (removed)";
            c.add(new Column("q:" + f.getFieldKey(), label, "Additional info", r -> answerText(r.answers().get(f.getFieldKey()))));
        }
        // Per souvenir: the chosen option (items with options only) and whether it was received.
        for (SouvenirItem item : souvenirItemRepository.findByProgramIdAndActiveTrueOrderBySortOrderAscIdAsc(programId)) {
            Integer id = item.getId();
            if (!souvenirService.options(item).isEmpty()) {
                c.add(new Column("souvenir:" + id + ":choice", item.getName() + " (choice)", "Souvenirs", r -> {
                    ParticipantSouvenir rec = r.souvenirs().get(id); return rec != null ? s(rec.getChoice()) : ""; }));
            }
            c.add(new Column("souvenir:" + id + ":received", item.getName() + " received", "Souvenirs", r -> {
                ParticipantSouvenir rec = r.souvenirs().get(id); return rec != null && Boolean.TRUE.equals(rec.getReceived()) ? "Yes" : "No"; }));
        }
        return c;
    }

    private static String requireProgram(String programId) {
        if (programId == null || programId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        }
        return programId;
    }

    @Operation(summary = "Export columns", description = "Every column the participants CSV can include, for choosing which to export")
    @GetMapping("/export/columns")
    @Transactional(readOnly = true)
    public List<Map<String, String>> exportColumns(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        String pid = requireProgram(programId);
        return columns(pid, true).stream()
            .map(c -> Map.of("id", c.id(), "label", c.label(), "group", c.group()))
            .toList();
    }

    @Operation(summary = "Export participants CSV",
        description = "All participants of the X-Program-Id program, including custom question answers. "
            + "Optional `columns`: comma-separated column ids (from /export/columns) to include, in that order; default is every column.")
    @GetMapping(value = "/export.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@RequestHeader(value = "X-Program-Id", required = false) String programId,
                                         @RequestParam(value = "columns", required = false) String columnIds) {
        requireProgram(programId);
        List<Participant> participants = participantRepository.findAllByProgramIdWithHotel(programId);
        boolean showAmounts = PaymentService.canSeeAmounts(programRepository.findById(programId).orElse(null), adminAccess.permissions());
        List<Column> all = columns(programId, showAmounts);
        List<Column> chosen = all;
        if (columnIds != null && !columnIds.isBlank()) {
            Map<String, Column> byId = new LinkedHashMap<>();
            all.forEach(c -> byId.put(c.id(), c));
            chosen = Arrays.stream(columnIds.split(",")).map(String::trim).distinct()
                .map(byId::get).filter(Objects::nonNull).toList();
            if (chosen.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one column to export");
        }
        boolean needPaid = showAmounts && chosen.stream().anyMatch(c -> c.id().equals("amount_paid"));
        boolean needFlights = chosen.stream().anyMatch(c -> c.id().startsWith("arrival_") || c.id().startsWith("departure_"));
        boolean needSouvenirs = chosen.stream().anyMatch(c -> c.id().startsWith("souvenir:"));

        Map<Integer, java.math.BigDecimal> paidTotals = needPaid
            ? paymentService.totals(participants.stream().map(Participant::getId).toList()) : Map.of();
        Map<Integer, Map<Integer, ParticipantSouvenir>> souvenirRecords = needSouvenirs ? souvenirService.recordsForProgram(programId) : Map.of();
        Map<Integer, List<Flight>> flights = participants.isEmpty() || !needFlights ? Map.of()
            : flightRepository.findByParticipantIn(participants).stream()
                .collect(Collectors.groupingBy(f -> f.getParticipant().getId()));

        StringBuilder csv = new StringBuilder("\uFEFF"); // BOM so Excel reads UTF-8
        row(csv, chosen.stream().map(Column::label).toList());
        for (Participant p : participants) {
            List<Flight> fl = flights.getOrDefault(p.getId(), List.of());
            Row r = new Row(p,
                fl.stream().filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null),
                fl.stream().filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null),
                fieldService.parseAnswers(p.getCustomAnswers()),
                souvenirRecords.getOrDefault(p.getId(), Map.of()),
                paidTotals.getOrDefault(p.getId(), java.math.BigDecimal.ZERO));
            row(csv, chosen.stream().map(c -> c.value().apply(r)).toList());
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"participants-" + programId + ".csv\"")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String s(String v) { return v == null ? "" : v; }

    private static String flightLabel(Flight f) {
        if (f == null) return "";
        return (s(f.getAirline()) + " " + s(f.getFlightNumber())).trim();
    }

    static String answerText(Object v) {
        if (v == null) return "";
        if (v instanceof Boolean b) return b ? "Yes" : "No";
        if (v instanceof Collection<?> c) return c.stream().map(String::valueOf).collect(Collectors.joining("; "));
        return String.valueOf(v);
    }

    private static void row(StringBuilder sb, List<String> cells) {
        sb.append(cells.stream().map(ParticipantExportController::cell).collect(Collectors.joining(","))).append("\r\n");
    }

    /** RFC 4180 quoting, plus a leading quote on formula-like values to prevent spreadsheet formula injection. */
    private static String cell(String v) {
        String x = v == null ? "" : v;
        if (!x.isEmpty() && "=+-@\t\r".indexOf(x.charAt(0)) >= 0 && !x.matches("^[+-]?\\d[\\d\\s().-]*$")) x = "'" + x;
        return "\"" + x.replace("\"", "\"\"") + "\"";
    }

    /** "male" → "Male", "female" → "Female", none → "". */
    private static String genderLabel(String g) {
        if (g == null || g.isBlank()) return "";
        return Character.toUpperCase(g.charAt(0)) + g.substring(1).toLowerCase();
    }
}
