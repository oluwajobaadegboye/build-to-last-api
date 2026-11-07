package com.btl.transport.registration;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
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

/** CSV of every participant in the program, with one column per custom registration question. */
@Tag(name = "Participants export")
@RestController
@RequestMapping("/api/v1/admin/participants")
@RequiredArgsConstructor
public class ParticipantExportController {

    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final RegistrationFieldRepository fieldRepository;
    private final RegistrationFieldService fieldService;

    @Operation(summary = "Export participants CSV", description = "All participants of the X-Program-Id program, including custom question answers")
    @GetMapping(value = "/export.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@RequestHeader(value = "X-Program-Id", required = false) String programId) {
        if (programId == null || programId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No program selected");
        }
        List<Participant> participants = participantRepository.findAllByProgramIdWithHotel(programId);
        List<RegistrationField> fields = fieldRepository.findByProgramIdOrderBySortOrderAscIdAsc(programId);

        Map<Integer, List<Flight>> flights = participants.isEmpty() ? Map.of()
            : flightRepository.findByParticipantIn(participants).stream()
                .collect(Collectors.groupingBy(f -> f.getParticipant().getId()));

        List<String> header = new ArrayList<>(List.of(
            "Code", "Full name", "Email", "Phone", "State", "Status", "Hotel", "Shuttle opt-in",
            "Arrival flight", "Arrival time", "Departure flight", "Departure time", "Breakout group", "Registered at"));
        for (RegistrationField f : fields) {
            header.add(Boolean.TRUE.equals(f.getActive()) ? f.getLabel() : f.getLabel() + " (removed)");
        }

        StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel reads UTF-8
        row(csv, header);
        for (Participant p : participants) {
            List<Flight> fl = flights.getOrDefault(p.getId(), List.of());
            Flight arr = fl.stream().filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
            Flight dep = fl.stream().filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);
            Map<String, Object> answers = fieldService.parseAnswers(p.getCustomAnswers());

            List<String> r = new ArrayList<>(List.of(
                s(p.getBtlCode()), s(p.getFullName()), s(p.getEmail()), s(p.getPhone()), s(p.getState()),
                p.getStatus() != null ? p.getStatus().name().toLowerCase() : "",
                p.getHotel() != null ? s(p.getHotel().getHotelName()) : "",
                Boolean.TRUE.equals(p.getShuttleOptIn()) ? "Yes" : "No",
                flightLabel(arr), arr != null && arr.getSubmittedDatetime() != null ? arr.getSubmittedDatetime().toString() : "",
                flightLabel(dep), dep != null && dep.getSubmittedDatetime() != null ? dep.getSubmittedDatetime().toString() : "",
                p.getBreakoutGroup() != null ? String.valueOf(p.getBreakoutGroup()) : "",
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""));
            for (RegistrationField f : fields) r.add(answerText(answers.get(f.getFieldKey())));
            row(csv, r);
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
}
