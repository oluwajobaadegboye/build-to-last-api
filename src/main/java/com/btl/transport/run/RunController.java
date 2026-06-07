package com.btl.transport.run;

import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.RunStatusEnum;
import com.btl.transport.hotel.Hotel;
import com.btl.transport.hotel.HotelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Tag(name = "Runs", description = "Public shuttle schedule and real-time run status endpoints")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RunController {

    private final RunRepository runRepository;
    private final RunService runService;
    private final HotelRepository hotelRepository;

    // ── GET /api/v1/shuttle-status ─────────────────────────────────────────
    @Operation(summary = "Get shuttle status", description = "Returns the full multi-day shuttle schedule, the next upcoming departure, and hotel pickup stop order")
    @GetMapping("/shuttle-status")
    public ResponseEntity<Map<String, Object>> shuttleStatus(
            @RequestParam(name = "program_id", required = false) String programId) {
        List<Hotel> hotels = hotelRepository.findAllByOrderByShuttleStopOrderAsc();
        List<Run> allRuns = programId != null
                ? runRepository.findByProgramIdWithDetails(programId)
                : runRepository.findAllWithDetails();

        Map<String, Map<String, List<Map<String, Object>>>> schedule = new LinkedHashMap<>();
        for (ConferenceDay day : ConferenceDay.values()) {
            String dayKey = day.name().toLowerCase();
            Map<String, List<Map<String, Object>>> dayMap = new LinkedHashMap<>();
            dayMap.put("to_church", new ArrayList<>());
            dayMap.put("to_hotel", new ArrayList<>());
            dayMap.put("to_airport", new ArrayList<>());
            schedule.put(dayKey, dayMap);
        }

        for (Run r : allRuns) {
            if (r.getConferenceDay() == null || r.getDirection() == null) continue;
            String dayKey = r.getConferenceDay().name().toLowerCase();
            String dirKey = r.getDirection().name().toLowerCase();
            schedule.getOrDefault(dayKey, Map.of())
                .getOrDefault(dirKey, new ArrayList<>())
                .add(runToMap(r));
        }

        // Find next departure from now
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("America/Indiana/Indianapolis"));
        Run next = findNextDeparture(allRuns, now);

        Map<String, Object> nextDep = null;
        if (next != null) {
            nextDep = new LinkedHashMap<>();
            nextDep.put("run_id", next.getRunId());
            nextDep.put("depart_time", next.getDepartTime());
            nextDep.put("direction", next.getDirection() != null ? next.getDirection().name().toLowerCase() : null);
            nextDep.put("conference_day", next.getConferenceDay() != null ? next.getConferenceDay().name().toLowerCase() : null);
            nextDep.put("seats_left", next.getSeatsRemaining());
            nextDep.put("vehicle_label", next.getVehicle() != null ? next.getVehicle().getLabel() : null);
            nextDep.put("driver_name", next.getDriver() != null ? next.getDriver().getName() : null);
        }

        List<Map<String, Object>> stopOrder = hotels.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stop_order", h.getShuttleStopOrder());
            m.put("hotel_name", h.getHotelName());
            m.put("pickup_address", h.getPickupAddress());
            return m;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated_at", OffsetDateTime.now().toString());
        result.put("next_departure", nextDep);
        result.put("schedule", schedule);
        result.put("stop_order", stopOrder);
        result.put("meta", Map.of(
            "conference_start", "2026-06-11",
            "conference_end", "2026-06-14",
            "airport", "IND"
        ));

        return ResponseEntity.ok(result);
    }

    private Run findNextDeparture(List<Run> runs, OffsetDateTime now) {
        LocalDate today = now.toLocalDate();
        String nowTime = String.format("%02d:%02d", now.getHour(), now.getMinute());

        return runs.stream()
            .filter(r -> r.getConferenceDate() != null && !r.getConferenceDate().isBefore(today))
            .filter(r -> r.getDepartTime() != null)
            .filter(r -> r.getConferenceDate().isAfter(today)
                || r.getDepartTime().compareTo(nowTime) >= 0)
            .filter(r -> r.getStatus() != null
                && r.getStatus() != RunStatusEnum.CANCELLED
                && r.getStatus() != RunStatusEnum.COMPLETED)
            .min(Comparator.comparing((Run r) -> r.getConferenceDate() != null ? r.getConferenceDate().toString() : "")
                .thenComparing(r -> r.getDepartTime() != null ? r.getDepartTime() : ""))
            .orElse(null);
    }

    private Map<String, Object> runToMap(Run r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_id", r.getRunId());
        m.put("depart_time", r.getDepartTime());
        m.put("status", r.getStatus() != null ? r.getStatus().name().toLowerCase() : null);
        m.put("seats_total", r.getSeatsTotal());
        m.put("seats_filled", r.getSeatsFilled());
        m.put("seats_left", r.getSeatsRemaining());
        m.put("vehicle_label", r.getVehicle() != null ? r.getVehicle().getLabel() : null);
        m.put("driver_name", r.getDriver() != null ? r.getDriver().getName() : null);
        return m;
    }
}
