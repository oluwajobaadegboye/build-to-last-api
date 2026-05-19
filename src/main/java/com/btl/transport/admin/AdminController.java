package com.btl.transport.admin;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.ParticipantStatus;
import com.btl.transport.driver.Driver;
import com.btl.transport.driver.DriverRepository;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.run.*;
import com.btl.transport.vehicle.Vehicle;
import com.btl.transport.vehicle.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    // ── Dashboard ──────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        long total = participantRepository.count();
        long alerts = participantRepository.countByNeedsAttentionTrue();
        long monitored = flightRepository.countByPollingActiveTrue();

        LocalDate today = LocalDate.now();
        long todayRuns = runRepository.countByConferenceDate(today);

        return ResponseEntity.ok(Map.of(
            "total_participants", total,
            "registration_target", 400,
            "today_run_count", todayRuns,
            "active_alerts", alerts,
            "flights_monitored", monitored,
            "polling_active", monitored > 0
        ));
    }

    // ── Participants ───────────────────────────────────────────────────────
    @GetMapping("/participants")
    public ResponseEntity<Map<String, Object>> listParticipants(
        @RequestParam(required = false) String status,
        @RequestParam(name = "hotel_id", required = false) Integer hotelId,
        @RequestParam(name = "needs_attention", required = false) Boolean needsAttention,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ParticipantStatus statusEnum = status != null
            ? ParticipantStatus.valueOf(status.toUpperCase()) : null;

        Page<Participant> pageResult = participantRepository.findWithFilters(
            statusEnum, hotelId, needsAttention, search,
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        List<Map<String, Object>> items = pageResult.getContent().stream()
            .map(this::participantSummary).toList();

        return ResponseEntity.ok(Map.of(
            "content", items,
            "total_elements", pageResult.getTotalElements(),
            "total_pages", pageResult.getTotalPages(),
            "page", page,
            "size", size
        ));
    }

    @GetMapping("/participants/{btlCode}")
    public ResponseEntity<Map<String, Object>> getParticipant(@PathVariable String btlCode) {
        Participant p = participantRepository.findByBtlCode(btlCode)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + btlCode));
        return ResponseEntity.ok(participantSummary(p));
    }

    @PatchMapping("/participants/{id}/hotel")
    public ResponseEntity<Map<String, Object>> updateHotel(
        @PathVariable Integer id,
        @RequestBody Map<String, Object> body
    ) {
        Participant p = participantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + id));
        Integer hotelId = ((Number) body.get("hotel_id")).intValue();
        // Hotel lookup — simplified; full impl would fetch hotel
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/participants/{id}/attention")
    public ResponseEntity<Map<String, Object>> updateAttention(
        @PathVariable Integer id,
        @RequestBody Map<String, Object> body
    ) {
        Participant p = participantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + id));
        p.setNeedsAttention(Boolean.TRUE.equals(body.get("needs_attention")));
        p.setAttentionReason((String) body.get("attention_reason"));
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Alerts ────────────────────────────────────────────────────────────
    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts() {
        List<Participant> flagged = participantRepository.findAll().stream()
            .filter(p -> Boolean.TRUE.equals(p.getNeedsAttention()))
            .toList();
        List<Map<String, Object>> alerts = flagged.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("btl_code", p.getBtlCode());
            m.put("participant_name", p.getFullName());
            m.put("hotel_name", p.getHotel() != null ? p.getHotel().getHotelName() : null);
            m.put("alert_type", "needs_attention");
            m.put("details", p.getAttentionReason());
            m.put("flagged_at", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/alerts/{btlCode}/resolve")
    public ResponseEntity<Map<String, Object>> resolveAlert(@PathVariable String btlCode) {
        Participant p = participantRepository.findByBtlCode(btlCode)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + btlCode));
        p.setNeedsAttention(false);
        p.setAttentionReason(null);
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Runs ──────────────────────────────────────────────────────────────
    @GetMapping("/runs")
    public ResponseEntity<List<Map<String, Object>>> getRuns(
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String direction
    ) {
        LocalDate date = day != null ? LocalDate.parse(day) : LocalDate.now();
        Direction dir = direction != null ? Direction.valueOf(direction.toUpperCase()) : null;

        List<Run> runs = dir != null
            ? runRepository.findByConferenceDateAndDirectionOrderByDepartTimeAsc(date, dir)
            : runRepository.findByConferenceDateOrderByDepartTimeAsc(date);

        return ResponseEntity.ok(runs.stream().map(this::runDetail).toList());
    }

    @PatchMapping("/runs/{id}/driver")
    public ResponseEntity<Map<String, Object>> assignDriver(
        @PathVariable Integer id,
        @RequestBody Map<String, Object> body
    ) {
        Run run = runRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + id));
        Integer driverId = ((Number) body.get("driver_id")).intValue();
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
        run.setDriver(driver);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/runs/{id}/vehicle")
    public ResponseEntity<Map<String, Object>> assignVehicle(
        @PathVariable Integer id,
        @RequestBody Map<String, Object> body
    ) {
        Run run = runRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + id));
        Integer vehicleId = ((Number) body.get("vehicle_id")).intValue();
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + vehicleId));
        run.setVehicle(vehicle);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PatchMapping("/run-participants/boarded")
    public ResponseEntity<Map<String, Object>> updateBoarding(@RequestBody Map<String, Object> body) {
        Integer runId = ((Number) body.get("run_id")).intValue();
        Integer participantId = ((Number) body.get("participant_id")).intValue();
        boolean boarded = Boolean.TRUE.equals(body.get("boarded"));

        RunParticipantId rpId = new RunParticipantId(runId, participantId);
        RunParticipant rp = runParticipantRepository.findById(rpId)
            .orElse(RunParticipant.builder().id(rpId).build());
        rp.setBoarded(boarded);
        rp.setBoardedAt(boarded ? OffsetDateTime.now() : null);
        runParticipantRepository.save(rp);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Drivers ───────────────────────────────────────────────────────────
    @GetMapping("/drivers")
    public ResponseEntity<List<Driver>> listDrivers() {
        return ResponseEntity.ok(driverRepository.findAll());
    }

    @PostMapping("/drivers")
    public ResponseEntity<Driver> createDriver(@RequestBody Driver driver) {
        driver.setCreatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(driverRepository.save(driver));
    }

    @PatchMapping("/drivers/{id}")
    public ResponseEntity<Driver> updateDriver(@PathVariable Integer id, @RequestBody Driver updates) {
        Driver d = driverRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + id));
        if (updates.getName() != null) d.setName(updates.getName());
        if (updates.getPhone() != null) d.setPhone(updates.getPhone());
        if (updates.getWhatsapp() != null) d.setWhatsapp(updates.getWhatsapp());
        return ResponseEntity.ok(driverRepository.save(d));
    }

    // ── Vehicles ──────────────────────────────────────────────────────────
    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> listVehicles() {
        return ResponseEntity.ok(vehicleRepository.findAll());
    }

    @PostMapping("/vehicles")
    public ResponseEntity<Vehicle> createVehicle(@RequestBody Vehicle vehicle) {
        vehicle.setCreatedAt(OffsetDateTime.now());
        return ResponseEntity.ok(vehicleRepository.save(vehicle));
    }

    @PatchMapping("/vehicles/{id}")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable Integer id, @RequestBody Vehicle updates) {
        Vehicle v = vehicleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + id));
        if (updates.getLabel() != null) v.setLabel(updates.getLabel());
        if (updates.getCapacity() != null) v.setCapacity(updates.getCapacity());
        if (updates.getType() != null) v.setType(updates.getType());
        return ResponseEntity.ok(vehicleRepository.save(v));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> participantSummary(Participant p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("btl_code", p.getBtlCode());
        m.put("full_name", p.getFullName());
        m.put("email", p.getEmail());
        m.put("phone", p.getPhone());
        m.put("status", p.getStatus() != null ? p.getStatus().name().toLowerCase() : null);
        m.put("needs_attention", Boolean.TRUE.equals(p.getNeedsAttention()));
        m.put("attention_reason", p.getAttentionReason());
        m.put("shuttle_opt_in", Boolean.TRUE.equals(p.getShuttleOptIn()));
        m.put("hotel", p.getHotel() != null ? Map.of(
            "id", p.getHotel().getId(),
            "hotel_name", p.getHotel().getHotelName()
        ) : null);
        m.put("notes", p.getNotes());
        m.put("created_at", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> runDetail(Run r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("run_id", r.getRunId());
        m.put("run_type", r.getRunType() != null ? r.getRunType().name().toLowerCase() : null);
        m.put("direction", r.getDirection() != null ? r.getDirection().name().toLowerCase() : null);
        m.put("conference_day", r.getConferenceDay() != null ? r.getConferenceDay().name().toLowerCase() : null);
        m.put("conference_date", r.getConferenceDate() != null ? r.getConferenceDate().toString() : null);
        m.put("depart_time", r.getDepartTime());
        m.put("seats_total", r.getSeatsTotal());
        m.put("seats_filled", r.getSeatsFilled());
        m.put("seats_left", r.getSeatsRemaining());
        m.put("status", r.getStatus() != null ? r.getStatus().name().toLowerCase() : null);
        m.put("vehicle", r.getVehicle() != null ? Map.of(
            "id", r.getVehicle().getId(),
            "label", r.getVehicle().getLabel(),
            "capacity", r.getVehicle().getCapacity()
        ) : null);
        m.put("driver", r.getDriver() != null ? Map.of(
            "id", r.getDriver().getId(),
            "name", r.getDriver().getName(),
            "phone", r.getDriver().getPhone() != null ? r.getDriver().getPhone() : ""
        ) : null);
        m.put("manifest_sent", Boolean.TRUE.equals(r.getManifestSent()));
        m.put("updated_at", r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
        return m;
    }
}
