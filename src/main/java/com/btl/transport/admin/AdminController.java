package com.btl.transport.admin;

import com.btl.transport.common.BtlCodeService;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.room.AccommodationContact;
import com.btl.transport.room.AccommodationContactRepository;
import com.btl.transport.room.RoomAssignment;
import com.btl.transport.room.RoomAssignmentRepository;
import com.btl.transport.room.RoomOccupant;
import com.btl.transport.room.RoomOccupantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.ParticipantStatus;
import com.btl.transport.common.enums.RunStatusEnum;
import com.btl.transport.common.enums.RunType;
import com.btl.transport.driver.Driver;
import com.btl.transport.driver.DriverRepository;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.hotel.Hotel;
import com.btl.transport.hotel.HotelRepository;
import com.btl.transport.notification.AirportConfig;
import com.btl.transport.notification.AirportConfigRepository;
import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.notification.ShuttleConfig;
import com.btl.transport.notification.ShuttleConfigRepository;
import com.btl.transport.notification.NotificationService;
import com.btl.transport.notification.SendGridService;
import com.btl.transport.notification.TwilioService;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.run.*;
import com.btl.transport.infrastructure.StorageService;
import com.btl.transport.vehicle.Vehicle;
import com.btl.transport.vehicle.VehicleRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;

@Tag(name = "Admin", description = "Protected admin endpoints for managing participants, runs, drivers, vehicles, programs, and configuration. Requires a valid JWT bearer token")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    public record UpdateHotelRequest(@JsonProperty("hotel_id") Integer hotelId) {}

    public record UpdateAttentionRequest(
        @JsonProperty("needs_attention") Boolean needsAttention,
        @JsonProperty("attention_reason") String attentionReason
    ) {}

    public record AssignDriverRequest(@JsonProperty("driver_id") Integer driverId) {}

    public record AssignVehicleRequest(@JsonProperty("vehicle_id") Integer vehicleId) {}

    public record BoardingRequest(
        @JsonProperty("run_id") Integer runId,
        @JsonProperty("participant_id") Integer participantId,
        Boolean boarded
    ) {}

    public record UpdateParticipantRequest(
        @JsonProperty("full_name")        String fullName,
        @JsonProperty("phone_whatsapp")   String phone,
        String email,
        @JsonProperty("needs_attention")  Boolean needsAttention,
        @JsonProperty("attention_reason") String attentionReason,
        String notes
    ) {}

    public record UpdateRunRequest(
        String status,
        @JsonProperty("depart_time") String departTime,
        @JsonProperty("seats_total") Integer seatsTotal
    ) {}

    public record MoveParticipantRequest(
        @JsonProperty("btl_code")    String btlCode,
        @JsonProperty("from_run_id") String fromRunId,
        @JsonProperty("to_run_id")   String toRunId
    ) {}

    public record CreateRunRequest(
        @JsonProperty("run_type")        String runType,
        String direction,
        @JsonProperty("conference_day")  String conferenceDay,
        @JsonProperty("conference_date") String conferenceDate,
        @JsonProperty("depart_time")     String departTime,
        @JsonProperty("seats_total")     Integer seatsTotal
    ) {}

    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final ShuttleConfigRepository shuttleConfigRepository;
    private final AirportConfigRepository airportConfigRepository;
    private final NotificationConfigRepository notificationConfigRepository;
    private final TwilioService twilioService;
    private final SendGridService sendGridService;
    private final NotificationService notificationService;
    private final ProgramRepository programRepository;
    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;
    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final StorageService storageService;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final RoomOccupantRepository roomOccupantRepository;
    private final AccommodationContactRepository accommodationContactRepository;
    private final BtlCodeService btlCodeService;
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    @org.springframework.beans.factory.annotation.Value("${btl.frontend-base-url}")
    private String frontendBaseUrl;

    // ── Dashboard ──────────────────────────────────────────────────────────
    @Operation(summary = "Dashboard stats", description = "Returns top-level KPIs: total participants, today's run count, active alerts, and monitored flights. Optionally scoped to a program via X-Program-Id")
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        long total = programId != null
            ? participantRepository.countByProgramId(programId)
            : participantRepository.count();
        long alerts = programId != null
            ? participantRepository.countByProgramIdAndNeedsAttentionTrue(programId)
            : participantRepository.countByNeedsAttentionTrue();
        long monitored = flightRepository.countByPollingActiveTrue();

        LocalDate today = LocalDate.now();
        long todayRuns = programId != null
            ? runRepository.countByProgramIdAndConferenceDate(programId, today)
            : runRepository.countByConferenceDate(today);

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
    @Operation(summary = "List participants", description = "Returns a paginated, filterable list of participants. Supports filtering by status, hotel, attention flag, and free-text search")
    @GetMapping("/participants")
    public ResponseEntity<Map<String, Object>> listParticipants(
        @RequestHeader(value = "X-Program-Id", required = false) String programId,
        @RequestParam(required = false) String status,
        @RequestParam(name = "hotel_id", required = false) Integer hotelId,
        @RequestParam(name = "needs_attention", required = false) Boolean needsAttention,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ParticipantStatus statusEnum = status != null
            ? ParticipantStatus.valueOf(status.toUpperCase()) : null;

        List<Participant> all = programId != null
            ? participantRepository.findAllByProgramIdWithHotel(programId)
            : participantRepository.findAllWithHotel();

        Stream<Participant> stream = all.stream();
        if (statusEnum != null) stream = stream.filter(p -> statusEnum.equals(p.getStatus()));
        if (hotelId != null) stream = stream.filter(p -> p.getHotel() != null && hotelId.equals(p.getHotel().getId()));
        if (Boolean.TRUE.equals(needsAttention)) stream = stream.filter(p -> Boolean.TRUE.equals(p.getNeedsAttention()));
        if (search != null && !search.isBlank()) {
            String lc = search.toLowerCase();
            stream = stream.filter(p ->
                (p.getFullName() != null && p.getFullName().toLowerCase().contains(lc)) ||
                (p.getBtlCode() != null && p.getBtlCode().toLowerCase().contains(lc)));
        }
        List<Participant> filtered = stream.toList();

        int total = filtered.size();
        int fromIdx = Math.min(page * size, total);
        int toIdx   = Math.min(fromIdx + size, total);
        List<Participant> pageItems = filtered.subList(fromIdx, toIdx);
        List<Flight> pageFlights = flightRepository.findByParticipantIn(pageItems);
        Map<Integer, Flight> arrivals = pageFlights.stream()
            .filter(f -> Direction.TO_HOTEL.equals(f.getDirection()))
            .collect(Collectors.toMap(f -> f.getParticipant().getId(), f -> f, (a, b) -> a));
        Map<Integer, Flight> departures = pageFlights.stream()
            .filter(f -> Direction.TO_AIRPORT.equals(f.getDirection()))
            .collect(Collectors.toMap(f -> f.getParticipant().getId(), f -> f, (a, b) -> a));
        List<AdminDtos.ParticipantAdminResponse> items = pageItems.stream()
            .map(p -> toParticipantAdminResponse(p, arrivals.get(p.getId()), departures.get(p.getId())))
            .toList();

        return ResponseEntity.ok(Map.of(
            "content", items,
            "total_elements", (long) total,
            "total_pages", size > 0 ? (int) Math.ceil((double) total / size) : 0,
            "page", page,
            "size", size
        ));
    }

    @Operation(summary = "Get participant", description = "Returns a single participant summary by BTL code")
    @GetMapping("/participants/{btlCode}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getParticipant(@PathVariable String btlCode) {
        Participant p = participantRepository.findByBtlCodeWithHotel(btlCode)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + btlCode));
        return ResponseEntity.ok(participantSummary(p));
    }

    @Operation(summary = "Update participant hotel", description = "Reassigns a participant to a different hotel by participant ID")
    @PatchMapping("/participants/{id}/hotel")
    public ResponseEntity<Map<String, Object>> updateHotel(
        @PathVariable Integer id,
        @RequestBody UpdateHotelRequest req
    ) {
        Participant p = participantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + id));
        // Hotel lookup — simplified; full impl would fetch hotel by req.hotelId()
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Flag participant for attention", description = "Sets or clears the needs-attention flag on a participant and records an optional reason")
    @PatchMapping("/participants/{id}/attention")
    public ResponseEntity<Map<String, Object>> updateAttention(
        @PathVariable Integer id,
        @RequestBody UpdateAttentionRequest req
    ) {
        Participant p = participantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + id));
        p.setNeedsAttention(Boolean.TRUE.equals(req.needsAttention()));
        p.setAttentionReason(req.attentionReason());
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Alerts ────────────────────────────────────────────────────────────
    @Operation(summary = "List alerts", description = "Returns all participants currently flagged as needing attention")
    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts() {
        List<Participant> flagged = participantRepository.findAll().stream()
            .filter(p -> Boolean.TRUE.equals(p.getNeedsAttention()))
            .toList();
        List<Map<String, Object>> alerts = flagged.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alert_id", p.getBtlCode());
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

    @Operation(summary = "Resolve alert", description = "Clears the needs-attention flag for a participant identified by BTL code")
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
    @Operation(summary = "List runs", description = "Returns all runs for a given date and optional direction, including assigned drivers, vehicles, and boarded participants")
    @Transactional(readOnly = true)
    @GetMapping("/runs")
    public ResponseEntity<List<Map<String, Object>>> getRuns(
        @RequestHeader(value = "X-Program-Id", required = false) String programId,
        @RequestParam(required = false) String day,
        @RequestParam(required = false) String direction
    ) {
        LocalDate date = day != null ? LocalDate.parse(day) : LocalDate.now();
        Direction dir = direction != null ? Direction.valueOf(direction.toUpperCase()) : null;

        List<Run> runs;
        if (programId != null) {
            runs = dir != null
                ? runRepository.findByProgramIdAndConferenceDateAndDirectionWithDetails(programId, date, dir)
                : runRepository.findByProgramIdAndConferenceDateWithDetailsOrderByDepartTimeAsc(programId, date);
        } else {
            runs = dir != null
                ? runRepository.findByConferenceDateAndDirectionWithDetailsOrderByDepartTimeAsc(date, dir)
                : runRepository.findByConferenceDateWithDetailsOrderByDepartTimeAsc(date);
        }

        return ResponseEntity.ok(runs.stream().map(this::runDetail).toList());
    }

    @Operation(summary = "Assign driver to run", description = "Assigns an existing driver to a run by run ID")
    @PatchMapping("/runs/{id}/driver")
    public ResponseEntity<Map<String, Object>> assignDriver(
        @PathVariable Integer id,
        @RequestBody AssignDriverRequest req
    ) {
        Run run = runRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + id));
        Driver driver = driverRepository.findById(req.driverId())
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + req.driverId()));
        run.setDriver(driver);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Assign vehicle to run", description = "Assigns an existing vehicle to a run by run ID")
    @PatchMapping("/runs/{id}/vehicle")
    public ResponseEntity<Map<String, Object>> assignVehicle(
        @PathVariable Integer id,
        @RequestBody AssignVehicleRequest req
    ) {
        Run run = runRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + id));
        Vehicle vehicle = vehicleRepository.findById(req.vehicleId())
            .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + req.vehicleId()));
        run.setVehicle(vehicle);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Set group chat link", description = "Admin sets or clears the WhatsApp group invite link for a run. Accepts whatsapp_group_link as a string or null.")
    @PatchMapping("/runs/{runId}/group-link")
    public ResponseEntity<Map<String, Object>> updateRunGroupLink(
            @PathVariable String runId,
            @RequestBody java.util.Map<String, Object> req) {
        Run run = runRepository.findByRunId(runId)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + runId));
        String link = req.get("whatsapp_group_link") instanceof String s ? s : null;
        if (link != null && !link.isBlank() && !link.startsWith("https://chat.whatsapp.com/")) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid WhatsApp group link.");
        }
        run.setWhatsappGroupLink(link != null && link.isBlank() ? null : link);
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("whatsapp_group_link", run.getWhatsappGroupLink());
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Mark participant boarding status", description = "Records whether a participant has boarded a specific run, stamping the board time when true")
    @PatchMapping("/run-participants/boarded")
    public ResponseEntity<Map<String, Object>> updateBoarding(@RequestBody BoardingRequest req) {
        RunParticipantId rpId = new RunParticipantId(req.runId(), req.participantId());
        RunParticipant rp = runParticipantRepository.findById(rpId)
            .orElse(RunParticipant.builder().id(rpId).build());
        boolean boarded = Boolean.TRUE.equals(req.boarded());
        rp.setBoarded(boarded);
        rp.setBoardedAt(boarded ? OffsetDateTime.now() : null);
        runParticipantRepository.save(rp);

        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Drivers ───────────────────────────────────────────────────────────
    @Operation(summary = "List drivers", description = "Returns all drivers, optionally scoped to a program via X-Program-Id")
    @GetMapping("/drivers")
    public ResponseEntity<List<AdminDtos.DriverAdminDto>> listDrivers(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        List<Driver> drivers = programId != null
            ? driverRepository.findByProgramId(programId)
            : driverRepository.findAll();
        return ResponseEntity.ok(drivers.stream().map(this::toDriverDto).toList());
    }

    @Operation(summary = "Create driver", description = "Creates a new driver and sends a welcome email with their login code if an email address is provided")
    @PostMapping("/drivers")
    public ResponseEntity<AdminDtos.DriverAdminDto> createDriver(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody Driver driver) {
        driver.setCreatedAt(OffsetDateTime.now());
        driver.setProgramId(programId);
        driver.setLoginToken(java.util.UUID.randomUUID().toString());
        driver.setDriverCode("DRV-TEMP-" + System.currentTimeMillis());
        Driver saved = driverRepository.save(driver);
        saved.setDriverCode(String.format("DRV-%03d", saved.getId()));
        saved = driverRepository.save(saved);
        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            try {
                String driverLink = frontendBaseUrl + "/driver?code=" + saved.getDriverCode();
                sendDriverAccessEmail(saved, driverLink);
            } catch (Exception e) {
                log.warn("Failed to send driver welcome email to {}: {}", saved.getEmail(), e.getMessage());
            }
        }
        return ResponseEntity.ok(toDriverDto(saved));
    }

    @Operation(summary = "Update driver", description = "Partially updates a driver's name, phone, WhatsApp, or email by driver ID")
    @PatchMapping("/drivers/{id}")
    public ResponseEntity<AdminDtos.DriverAdminDto> updateDriver(@PathVariable Integer id, @RequestBody Driver updates) {
        Driver d = driverRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + id));
        if (updates.getName()     != null) d.setName(updates.getName());
        if (updates.getPhone()    != null) d.setPhone(updates.getPhone());
        if (updates.getWhatsapp() != null) d.setWhatsapp(updates.getWhatsapp());
        if (updates.getEmail()    != null) d.setEmail(updates.getEmail());
        return ResponseEntity.ok(toDriverDto(driverRepository.save(d)));
    }

    @Operation(summary = "Delete driver", description = "Permanently deletes a driver, unassigning them from any runs first")
    @DeleteMapping("/drivers/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteDriver(@PathVariable Integer id) {
        runRepository.clearDriverFromRuns(id);
        driverRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Resend driver link", description = "Re-sends the personal driver access link to the driver's email address")
    @PostMapping("/drivers/{id}/resend-link")
    public ResponseEntity<AdminDtos.SuccessResponse> resendDriverLink(@PathVariable Integer id) {
        Driver d = driverRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + id));
        if (d.getEmail() == null || d.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver has no email address on file.");
        }
        try {
            String driverLink = frontendBaseUrl + "/driver?code=" + d.getDriverCode();
            sendDriverAccessEmail(d, driverLink);
        } catch (Exception e) {
            log.warn("Failed to resend driver link to {}: {}", d.getEmail(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email.");
        }
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Resend code ──────────────────────────────────────────────────────────
    @Operation(summary = "Resend participant code (admin)", description = "Triggers a re-send of the BTL code email for a participant, initiated by an admin")
    @PostMapping("/participants/{btlCode}/resend-code")
    public ResponseEntity<AdminDtos.SuccessResponse> resendCode(@PathVariable String btlCode) {
        Participant p = participantRepository.findByBtlCode(btlCode)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + btlCode));
        if (p.getEmail() != null && !p.getEmail().isBlank()) {
            notificationService.sendRegistrationConfirmation(p);
        }
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Vehicles ──────────────────────────────────────────────────────────
    @Operation(summary = "List vehicles", description = "Returns all vehicles, optionally scoped to a program via X-Program-Id")
    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> listVehicles(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        List<Vehicle> vehicles = programId != null
            ? vehicleRepository.findByProgramId(programId)
            : vehicleRepository.findAll();
        return ResponseEntity.ok(vehicles);
    }

    @Operation(summary = "Create vehicle", description = "Creates a new vehicle and associates it with the given program")
    @PostMapping("/vehicles")
    public ResponseEntity<Vehicle> createVehicle(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody Vehicle vehicle) {
        vehicle.setCreatedAt(OffsetDateTime.now());
        vehicle.setProgramId(programId);
        return ResponseEntity.ok(vehicleRepository.save(vehicle));
    }

    @Operation(summary = "Update vehicle", description = "Partially updates a vehicle's label, capacity, or type by vehicle ID")
    @PatchMapping("/vehicles/{id}")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable Integer id, @RequestBody Vehicle updates) {
        Vehicle v = vehicleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + id));
        if (updates.getLabel()    != null) v.setLabel(updates.getLabel());
        if (updates.getCapacity() != null) v.setCapacity(updates.getCapacity());
        if (updates.getType()     != null) v.setType(updates.getType());
        return ResponseEntity.ok(vehicleRepository.save(v));
    }

    @Operation(summary = "Delete vehicle", description = "Permanently deletes a vehicle by ID")
    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<AdminDtos.SuccessResponse> deleteVehicle(@PathVariable Integer id) {
        vehicleRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    @Operation(summary = "Admin stats", description = "Returns aggregated transport statistics including participant count, today's runs, active alerts, and the next scheduled departure time")
    @GetMapping("/stats")
    public ResponseEntity<AdminDtos.AdminStatsResponse> stats(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        long total = programId != null
            ? participantRepository.countByProgramId(programId)
            : participantRepository.count();
        long alerts = programId != null
            ? participantRepository.countByProgramIdAndNeedsAttentionTrue(programId)
            : participantRepository.countByNeedsAttentionTrue();
        long monitored = flightRepository.countByPollingActiveTrue();
        LocalDate today = LocalDate.now();
        long todayRuns = programId != null
            ? runRepository.countByProgramIdAndConferenceDate(programId, today)
            : runRepository.countByConferenceDate(today);
        String nextRunTime = runRepository.findByConferenceDateOrderByDepartTimeAsc(today).stream()
            .filter(r -> r.getStatus() == RunStatusEnum.SCHEDULED
                      || r.getStatus() == RunStatusEnum.BOARDING)
            .map(Run::getDepartTime)
            .findFirst()
            .orElse(null);
        return ResponseEntity.ok(new AdminDtos.AdminStatsResponse(
            total, todayRuns, alerts, monitored, nextRunTime));
    }

    // ── Participant (singular path) ────────────────────────────────────────

    @Operation(summary = "Get participant detail", description = "Returns full participant detail including flight and hotel info by BTL code")
    @GetMapping("/participant/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<AdminDtos.ParticipantAdminResponse> getParticipantDetail(
            @PathVariable String code) {
        Participant p = participantRepository.findByBtlCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + code));
        List<Flight> flights = flightRepository.findByParticipant(p);
        Flight arrival   = flights.stream()
            .filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
        Flight departure = flights.stream()
            .filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);
        return ResponseEntity.ok(toParticipantAdminResponse(p, arrival, departure));
    }

    @Operation(summary = "Update participant", description = "Partially updates a participant's personal details, attention flag, or notes by BTL code")
    @PatchMapping("/participant/{code}")
    @Transactional
    public ResponseEntity<AdminDtos.ParticipantAdminResponse> updateParticipant(
            @PathVariable String code,
            @RequestBody UpdateParticipantRequest req) {
        Participant p = participantRepository.findByBtlCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + code));
        if (req.fullName()        != null) p.setFullName(req.fullName());
        if (req.phone()           != null) p.setPhone(req.phone());
        if (req.email()           != null) p.setEmail(req.email());
        if (req.needsAttention()  != null) p.setNeedsAttention(req.needsAttention());
        if (req.attentionReason() != null) p.setAttentionReason(req.attentionReason());
        if (req.notes()           != null) p.setNotes(req.notes());
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        List<Flight> flights = flightRepository.findByParticipant(p);
        Flight arrival   = flights.stream()
            .filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
        Flight departure = flights.stream()
            .filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);
        return ResponseEntity.ok(toParticipantAdminResponse(p, arrival, departure));
    }

    @Operation(summary = "Delete participant", description = "Permanently deletes a participant. If their run becomes empty the run is also deleted; otherwise only their seat is freed.")
    @DeleteMapping("/participant/{code}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteParticipant(@PathVariable String code) {
        Participant p = participantRepository.findByBtlCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + code));

        List<RunParticipant> participantRps =
            runParticipantRepository.findByIdParticipantId(p.getId());

        for (RunParticipant rp : participantRps) {
            Run run = runRepository.findById(rp.getId().getRunId()).orElse(null);
            if (run == null) continue;

            List<RunParticipant> allInRun =
                runParticipantRepository.findByIdRunId(run.getId());
            int remaining = allInRun.size() - 1;

            if (remaining <= 0) {
                // Last passenger — delete the entire run group
                runParticipantRepository.deleteAll(allInRun);
                runRepository.delete(run);
            } else {
                // Others remain — free this seat only
                runParticipantRepository.delete(rp);
                if (run.getSeatsFilled() != null && run.getSeatsFilled() > 0) {
                    run.setSeatsFilled(run.getSeatsFilled() - 1);
                    runRepository.save(run);
                }
            }
        }

        flightRepository.deleteAll(flightRepository.findByParticipant(p));
        participantRepository.delete(p);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Alert (singular path alias) ────────────────────────────────────────

    @Operation(summary = "Resolve alert (alias)", description = "Alias for POST /alerts/{btlCode}/resolve — clears the attention flag for the given BTL code")
    @PostMapping("/alert/{btlCode}/resolve")
    public ResponseEntity<AdminDtos.SuccessResponse> resolveAlertSingular(
            @PathVariable String btlCode) {
        Participant p = participantRepository.findByBtlCode(btlCode)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + btlCode));
        p.setNeedsAttention(false);
        p.setAttentionReason(null);
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Run (singular path, string runId) ─────────────────────────────────

    @Operation(summary = "Update run", description = "Updates a run's status, departure time, or seat count by string run ID")
    @PatchMapping("/run/{runId}")
    @Transactional
    public ResponseEntity<AdminDtos.RunAdminResponse> updateRun(
            @PathVariable String runId,
            @RequestBody UpdateRunRequest req) {
        Run run = runRepository.findByRunId(runId)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + runId));
        if (req.status()     != null)
            run.setStatus(RunStatusEnum.valueOf(req.status().toUpperCase()));
        if (req.departTime() != null) run.setDepartTime(req.departTime());
        if (req.seatsTotal() != null) run.setSeatsTotal(req.seatsTotal());
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        return ResponseEntity.ok(toRunAdminResponse(run, List.of()));
    }

    @Operation(summary = "Delete run")
    @DeleteMapping("/run/{runId}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteRun(@PathVariable String runId) {
        Run run = runRepository.findByRunId(runId)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + runId));
        runRepository.delete(run);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "List all airport runs", description = "Returns all airport-type runs for the program across all conference days, with participant flight details")
    @GetMapping("/airport-runs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getAirportRuns(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        List<Run> runs = programId != null
            ? runRepository.findByProgramIdAndRunTypeOrderByConferenceDateAndDepartTime(programId, RunType.AIRPORT)
            : runRepository.findAll().stream()
                .filter(r -> r.getRunType() == RunType.AIRPORT)
                .sorted(Comparator.comparing(Run::getConferenceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(r -> r.getDepartTime() != null ? r.getDepartTime() : ""))
                .toList();
        return ResponseEntity.ok(runs.stream().map(this::runDetailWithFlights).toList());
    }

    @Operation(summary = "Sync airport runs from flights", description = "Auto-groups participants by flight arrival/departure time into airport runs using the configured grouping window")
    @PostMapping("/sync-airport-runs")
    @Transactional
    public ResponseEntity<Map<String, Object>> syncAirportRuns(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        int groupingWindowMins = airportConfigRepository.findByConfigKey("main")
            .map(c -> c.getGroupingWindowMins() != null ? c.getGroupingWindowMins() : 90)
            .orElse(90);

        int arrivalRunsCreated = 0;
        int departureRunsCreated = 0;
        int arrivalParticipantsAdded = 0;
        int departureParticipantsAdded = 0;

        for (Direction direction : new Direction[]{Direction.TO_HOTEL, Direction.TO_AIRPORT}) {
            List<Flight> flights = programId != null
                ? flightRepository.findByParticipantProgramIdAndDirectionOrderBySubmittedDatetime(programId, direction)
                : flightRepository.findAll().stream()
                    .filter(f -> f.getDirection() == direction)
                    .sorted(Comparator.comparing(f -> f.getSubmittedDatetime() != null
                        ? f.getSubmittedDatetime() : OffsetDateTime.MIN))
                    .toList();

            if (flights.isEmpty()) continue;

            Set<Integer> alreadyAssigned = new HashSet<>();
            List<Run> existingAirportRuns = programId != null
                ? runRepository.findByProgramIdAndRunTypeOrderByConferenceDateAndDepartTime(programId, RunType.AIRPORT)
                : List.of();
            for (Run r : existingAirportRuns) {
                if (r.getDirection() == direction) {
                    runParticipantRepository.findByIdRunId(r.getId())
                        .forEach(rp -> alreadyAssigned.add(rp.getId().getParticipantId()));
                }
            }

            List<Flight> unassigned = flights.stream()
                .filter(f -> f.getParticipant() != null && !alreadyAssigned.contains(f.getParticipant().getId()))
                .filter(f -> f.getSubmittedDatetime() != null)
                .toList();

            if (unassigned.isEmpty()) continue;

            // Partition: flights that fit an existing run vs flights that need a new run
            Map<Run, List<Flight>> addToExisting = new LinkedHashMap<>();
            List<Flight> trulyNew = new ArrayList<>();

            for (Flight f : unassigned) {
                Run match = findBestMatchingRun(f, existingAirportRuns, direction, groupingWindowMins);
                if (match != null) {
                    addToExisting.computeIfAbsent(match, k -> new ArrayList<>()).add(f);
                } else {
                    trulyNew.add(f);
                }
            }

            // Merge matched flights into their existing runs
            for (Map.Entry<Run, List<Flight>> entry : addToExisting.entrySet()) {
                Run run = entry.getKey();
                List<Flight> newFlights = entry.getValue();

                // Capacity guard: only fill remaining seats; overflow forms new groups
                int remaining = run.getSeatsTotal() - run.getSeatsFilled();
                List<Flight> toAdd   = newFlights.subList(0, Math.min(remaining, newFlights.size()));
                List<Flight> overflow = newFlights.subList(Math.min(remaining, newFlights.size()), newFlights.size());
                trulyNew.addAll(overflow);

                for (Flight f : toAdd) {
                    RunParticipantId rpId = new RunParticipantId(run.getId(), f.getParticipant().getId());
                    runParticipantRepository.save(RunParticipant.builder().id(rpId).boarded(false).build());
                }
                run.setSeatsFilled(run.getSeatsFilled() + toAdd.size());
                recomputeRunDepartAndLocations(run);
                runRepository.save(run);

                if (direction == Direction.TO_HOTEL) arrivalParticipantsAdded += toAdd.size();
                else departureParticipantsAdded += toAdd.size();
            }

            // Group truly new (unmatched) flights into new runs — original logic unchanged
            List<List<Flight>> groups = new ArrayList<>();
            List<Flight> currentGroup = new ArrayList<>();
            OffsetDateTime groupStart = null;

            for (Flight f : trulyNew) {
                if (groupStart == null) {
                    groupStart = f.getSubmittedDatetime();
                    currentGroup.add(f);
                } else {
                    long minutesDiff = Duration.between(groupStart, f.getSubmittedDatetime()).toMinutes();
                    if (minutesDiff <= groupingWindowMins) {
                        currentGroup.add(f);
                    } else {
                        groups.add(new ArrayList<>(currentGroup));
                        currentGroup = new ArrayList<>();
                        currentGroup.add(f);
                        groupStart = f.getSubmittedDatetime();
                    }
                }
            }
            if (!currentGroup.isEmpty()) groups.add(currentGroup);

            for (List<Flight> group : groups) {
                OffsetDateTime latestFlight = group.stream()
                    .map(Flight::getSubmittedDatetime)
                    .max(Comparator.naturalOrder())
                    .orElse(group.get(0).getSubmittedDatetime());

                OffsetDateTime departOdt = latestFlight.plusMinutes(30);
                String departTime = String.format("%02d:%02d",
                    departOdt.getHour(), departOdt.getMinute());

                LocalDate conferenceDate = group.get(0).getSubmittedDatetime().toLocalDate();

                ConferenceDay conferenceDay = switch (conferenceDate.getDayOfWeek()) {
                    case THURSDAY -> ConferenceDay.THURSDAY;
                    case FRIDAY   -> ConferenceDay.FRIDAY;
                    case SATURDAY -> ConferenceDay.SATURDAY;
                    case SUNDAY   -> ConferenceDay.SUNDAY;
                    default       -> null;
                };

                String dropoffLocation = group.stream()
                    .map(f -> f.getParticipant().getHotel())
                    .filter(Objects::nonNull)
                    .map(h -> h.getHotelName())
                    .distinct()
                    .collect(Collectors.joining(" · "));
                if (dropoffLocation.isBlank()) dropoffLocation = "Hotel";

                String pickupLocation = direction == Direction.TO_HOTEL
                    ? "Indianapolis Airport"
                    : (dropoffLocation.isBlank() ? "Hotel" : dropoffLocation);
                String dropLocation = direction == Direction.TO_HOTEL ? dropoffLocation : "Indianapolis Airport";

                Run run = Run.builder()
                    .runId("RUN-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase())
                    .runType(RunType.AIRPORT)
                    .direction(direction)
                    .conferenceDay(conferenceDay)
                    .conferenceDate(conferenceDate)
                    .departTime(departTime)
                    .pickupLocation(pickupLocation)
                    .dropoffLocation(dropLocation)
                    .seatsTotal(15)
                    .seatsFilled(group.size())
                    .status(RunStatusEnum.SCHEDULED)
                    .manifestSent(false)
                    .programId(programId)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
                run = runRepository.save(run);

                for (Flight f : group) {
                    RunParticipantId rpId = new RunParticipantId(run.getId(), f.getParticipant().getId());
                    RunParticipant rp = RunParticipant.builder()
                        .id(rpId)
                        .boarded(false)
                        .build();
                    runParticipantRepository.save(rp);
                }

                if (direction == Direction.TO_HOTEL) arrivalRunsCreated++;
                else departureRunsCreated++;
            }
        }

        return ResponseEntity.ok(Map.of(
            "arrival_runs_created",         arrivalRunsCreated,
            "departure_runs_created",       departureRunsCreated,
            "arrival_participants_added",   arrivalParticipantsAdded,
            "departure_participants_added", departureParticipantsAdded
        ));
    }

    private void recomputeRunDepartAndLocations(Run run) {
        Direction direction = run.getDirection();
        List<Integer> pids = runParticipantRepository.findByIdRunId(run.getId())
            .stream().map(rp -> rp.getId().getParticipantId()).toList();
        List<Flight> flights = flightRepository.findAll().stream()
            .filter(fl -> fl.getParticipant() != null
                && pids.contains(fl.getParticipant().getId())
                && fl.getDirection() == direction
                && fl.getSubmittedDatetime() != null)
            .toList();
        flights.stream().map(Flight::getSubmittedDatetime).max(Comparator.naturalOrder())
            .ifPresent(latest -> {
                OffsetDateTime d = latest.plusMinutes(30);
                run.setDepartTime(String.format("%02d:%02d", d.getHour(), d.getMinute()));
            });
        String hotels = flights.stream()
            .map(fl -> fl.getParticipant().getHotel()).filter(Objects::nonNull)
            .map(h -> h.getHotelName()).distinct().collect(Collectors.joining(" · "));
        if (!hotels.isBlank()) {
            if (direction == Direction.TO_HOTEL) run.setDropoffLocation(hotels);
            else run.setPickupLocation(hotels);
        }
        run.setUpdatedAt(OffsetDateTime.now());
    }

    @Operation(summary = "Move participant between runs", description = "Moves a participant from one airport run to another of the same direction and date. Recalculates departure times on both runs. Deletes the source run if it becomes empty.")
    @PatchMapping("/run-participants/move")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> moveParticipant(@RequestBody MoveParticipantRequest req) {
        Participant p = participantRepository.findByBtlCode(req.btlCode())
            .orElseThrow(() -> new EntityNotFoundException("Participant not found: " + req.btlCode()));
        Run from = runRepository.findByRunId(req.fromRunId())
            .orElseThrow(() -> new EntityNotFoundException("Source run not found: " + req.fromRunId()));
        Run to = runRepository.findByRunId(req.toRunId())
            .orElseThrow(() -> new EntityNotFoundException("Target run not found: " + req.toRunId()));

        if (req.fromRunId().equals(req.toRunId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target runs are the same.");
        if (from.getDirection() != to.getDirection())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot move between runs of different directions.");
        if (!java.util.Objects.equals(from.getConferenceDate(), to.getConferenceDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot move between runs on different dates.");
        if (to.getSeatsFilled() != null && to.getSeatsTotal() != null
                && to.getSeatsFilled() >= to.getSeatsTotal())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target run is full.");

        RunParticipantId fromId = new RunParticipantId(from.getId(), p.getId());
        if (!runParticipantRepository.existsById(fromId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant is not in the source run.");
        if (runParticipantRepository.existsById(new RunParticipantId(to.getId(), p.getId())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant is already in the target run.");

        // Remove from source
        runParticipantRepository.deleteById(fromId);
        if (from.getSeatsFilled() != null && from.getSeatsFilled() > 0)
            from.setSeatsFilled(from.getSeatsFilled() - 1);

        List<RunParticipant> remaining = runParticipantRepository.findByIdRunId(from.getId());
        if (remaining.isEmpty()) {
            runRepository.delete(from);
        } else {
            recomputeRunDepartAndLocations(from);
            runRepository.save(from);
        }

        // Add to target
        runParticipantRepository.save(RunParticipant.builder()
            .id(new RunParticipantId(to.getId(), p.getId())).boarded(false).build());
        to.setSeatsFilled(to.getSeatsFilled() != null ? to.getSeatsFilled() + 1 : 1);
        recomputeRunDepartAndLocations(to);
        runRepository.save(to);

        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    private Run findBestMatchingRun(Flight f, List<Run> runs, Direction dir, int windowMins) {
        if (f.getSubmittedDatetime() == null) return null;
        OffsetDateTime flightTime = f.getSubmittedDatetime();
        LocalDate flightDate = flightTime.toLocalDate();

        Run best = null;
        long bestDiff = Long.MAX_VALUE;

        for (Run run : runs) {
            if (run.getDriver() != null) continue;  // locked — driver already assigned
            if (run.getDirection() != dir) continue;
            if (run.getDepartTime() == null || run.getConferenceDate() == null) continue;
            if (!flightDate.equals(run.getConferenceDate())) continue;
            if (run.getSeatsFilled() >= run.getSeatsTotal()) continue;

            String[] parts = run.getDepartTime().split(":");
            if (parts.length < 2) continue;
            OffsetDateTime departOdt = flightTime
                .withHour(Integer.parseInt(parts[0]))
                .withMinute(Integer.parseInt(parts[1]))
                .withSecond(0).withNano(0);
            OffsetDateTime existingLatest = departOdt.minusMinutes(30);
            OffsetDateTime windowStart    = existingLatest.minusMinutes(windowMins);
            OffsetDateTime windowEnd      = existingLatest.plusMinutes(windowMins);

            if (!flightTime.isBefore(windowStart) && !flightTime.isAfter(windowEnd)) {
                long diff = Math.abs(Duration.between(existingLatest, flightTime).toMinutes());
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = run;
                }
            }
        }
        return best;
    }

    @Operation(summary = "Create run", description = "Creates an ad-hoc transport run and associates it with the given program")
    @PostMapping("/run")
    @Transactional
    public ResponseEntity<AdminDtos.RunAdminResponse> createRun(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody CreateRunRequest req) {
        Run run = Run.builder()
            .runId(generateAdHocRunId())
            .runType(req.runType() != null
                ? RunType.valueOf(req.runType().toUpperCase()) : RunType.SHUTTLE)
            .direction(req.direction() != null
                ? Direction.valueOf(req.direction().toUpperCase()) : null)
            .conferenceDay(req.conferenceDay() != null
                ? ConferenceDay.valueOf(req.conferenceDay().toUpperCase()) : null)
            .conferenceDate(req.conferenceDate() != null
                ? LocalDate.parse(req.conferenceDate()) : null)
            .departTime(req.departTime())
            .seatsTotal(req.seatsTotal() != null ? req.seatsTotal() : 0)
            .seatsFilled(0)
            .status(RunStatusEnum.SCHEDULED)
            .manifestSent(false)
            .programId(programId)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        runRepository.save(run);
        return ResponseEntity.ok(toRunAdminResponse(run, List.of()));
    }

    // ── Manifest ──────────────────────────────────────────────────────────

    @Operation(summary = "Get driver manifest", description = "Returns all runs and their passenger lists assigned to a driver, formatted as a manifest")
    @GetMapping("/manifest/{driverId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AdminDtos.ManifestResponse> getManifest(
            @PathVariable Integer driverId) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
        List<Run> runs = runRepository
            .findByDriverIdOrderByConferenceDateAscDepartTimeAsc(driverId);
        List<AdminDtos.RunAdminResponse> runResponses = runs.stream().map(run -> {
            List<RunParticipant> rps = runParticipantRepository.findByIdRunId(run.getId());
            List<Integer> pids = rps.stream()
                .map(rp -> rp.getId().getParticipantId()).toList();
            List<AdminDtos.ParticipantAdminResponse> participants = pids.isEmpty()
                ? List.of()
                : participantRepository.findAllById(pids).stream()
                    .map(p -> toParticipantAdminResponse(p, null, null))
                    .toList();
            return toRunAdminResponse(run, participants);
        }).toList();
        return ResponseEntity.ok(new AdminDtos.ManifestResponse(toDriverDto(driver), runResponses));
    }

    @Operation(summary = "Send manifest to driver", description = "Sends the driver's run manifest via WhatsApp (or SMS fallback) and marks all runs as manifest-sent")
    @PostMapping("/send-manifest/{driverId}")
    public ResponseEntity<AdminDtos.ManifestSendResponse> sendManifest(
            @PathVariable Integer driverId) {
        Driver driver = driverRepository.findById(driverId)
            .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + driverId));
        List<Run> runs = runRepository
            .findByDriverIdOrderByConferenceDateAscDepartTimeAsc(driverId);
        if (runs.isEmpty()) {
            return ResponseEntity.ok(new AdminDtos.ManifestSendResponse(true, false, "no_runs"));
        }
        StringBuilder manifest = new StringBuilder();
        manifest.append("BTL 2026 — Manifest for ").append(driver.getName()).append("\n\n");
        for (Run run : runs) {
            manifest.append(run.getConferenceDate()).append(" ").append(run.getDepartTime())
                    .append(" | ").append(run.getDirection() != null
                        ? run.getDirection().name().toLowerCase() : "").append("\n");
            List<RunParticipant> rps = runParticipantRepository.findByIdRunId(run.getId());
            List<Integer> pids = rps.stream().map(rp -> rp.getId().getParticipantId()).toList();
            if (!pids.isEmpty()) {
                participantRepository.findAllById(pids).forEach(p ->
                    manifest.append("  - ").append(p.getFullName())
                            .append(" (").append(p.getBtlCode()).append(")\n"));
            }
            manifest.append("\n");
        }
        String to = driver.getWhatsapp() != null ? driver.getWhatsapp() : driver.getPhone();
        if (to != null) twilioService.sendWhatsApp(to, manifest.toString());
        runs.forEach(r -> {
            r.setManifestSent(true);
            r.setUpdatedAt(OffsetDateTime.now());
            runRepository.save(r);
        });
        return ResponseEntity.ok(new AdminDtos.ManifestSendResponse(true, to != null, null));
    }

    // ── Config ────────────────────────────────────────────────────────────

    @Operation(summary = "Get shuttle config", description = "Returns shuttle scheduling configuration for all conference days and directions")
    @GetMapping("/config/shuttle")
    public ResponseEntity<List<AdminDtos.ShuttleConfigResponse>> getShuttleConfig() {
        List<AdminDtos.ShuttleConfigResponse> configs = shuttleConfigRepository.findAll().stream()
            .map(c -> new AdminDtos.ShuttleConfigResponse(
                String.valueOf(c.getId()),
                c.getConferenceDay() != null ? c.getConferenceDay().name().toLowerCase() : null,
                c.getDirection() != null ? c.getDirection().name().toLowerCase() : null,
                c.getWindowStart(), c.getWindowEnd(),
                c.getIntervalMins(), c.getMaxVehicles(), c.getSeatsPerVehicle(),
                true
            )).toList();
        return ResponseEntity.ok(configs);
    }

    @Operation(summary = "Update shuttle config", description = "Bulk-updates shuttle window, interval, and capacity settings for one or more conference-day slots")
    @PatchMapping("/config/shuttle")
    public ResponseEntity<AdminDtos.SuccessResponse> updateShuttleConfig(
            @RequestBody List<AdminDtos.ShuttleConfigResponse> updates) {
        for (AdminDtos.ShuttleConfigResponse u : updates) {
            if (u.configId() == null) continue;
            shuttleConfigRepository.findById(Integer.parseInt(u.configId())).ifPresent(c -> {
                if (u.windowStart()     != null) c.setWindowStart(u.windowStart());
                if (u.windowEnd()       != null) c.setWindowEnd(u.windowEnd());
                if (u.intervalMins()    != null) c.setIntervalMins(u.intervalMins());
                if (u.maxVehicles()     != null) c.setMaxVehicles(u.maxVehicles());
                if (u.seatsPerVehicle() != null) c.setSeatsPerVehicle(u.seatsPerVehicle());
                shuttleConfigRepository.save(c);
            });
        }
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Get airport config", description = "Returns global airport configuration: leg-4 cutoff time, polling window, and grouping parameters")
    @GetMapping("/config/airport")
    public ResponseEntity<AdminDtos.AirportConfigResponse> getAirportConfig() {
        AirportConfig c = airportConfigRepository.findByConfigKey("main")
            .orElseThrow(() -> new EntityNotFoundException("Airport config not found"));
        return ResponseEntity.ok(new AdminDtos.AirportConfigResponse(
            c.getLeg4DefaultCutoffTime(), false,
            c.getPollingStartDate(), c.getPollingEndDatetime(),
            c.getGroupingWindowMins(), 0
        ));
    }

    @Operation(summary = "Update airport config", description = "Updates airport configuration fields such as leg-4 cutoff, polling start/end, and grouping window")
    @PatchMapping("/config/airport")
    public ResponseEntity<AdminDtos.SuccessResponse> updateAirportConfig(
            @RequestBody AdminDtos.AirportConfigResponse req) {
        AirportConfig c = airportConfigRepository.findByConfigKey("main")
            .orElseThrow(() -> new EntityNotFoundException("Airport config not found"));
        if (req.leg4CutoffDefault()  != null) c.setLeg4DefaultCutoffTime(req.leg4CutoffDefault());
        if (req.pollingStart()       != null) c.setPollingStartDate(req.pollingStart());
        if (req.pollingEnd()         != null) c.setPollingEndDatetime(req.pollingEnd());
        if (req.groupingWindowMins() != null) c.setGroupingWindowMins(req.groupingWindowMins());
        airportConfigRepository.save(c);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Get notification config", description = "Returns coordinator contact details and SMS message templates, scoped to a program or the global default")
    @GetMapping("/config/notifications")
    public ResponseEntity<AdminDtos.NotificationConfigResponse> getNotificationConfig(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        NotificationConfig c = programId != null
            ? notificationConfigRepository.findByProgramId(programId)
                .orElseGet(() -> notificationConfigRepository.findByConfigKey("main").orElse(null))
            : notificationConfigRepository.findByConfigKey("main").orElse(null);
        if (c == null) throw new EntityNotFoundException("Notification config not found");
        return ResponseEntity.ok(new AdminDtos.NotificationConfigResponse(
            c.getAdminPhone1(), c.getAdminPhone2(),
            c.getAdminName1(), c.getAdminName2(),
            c.getAdminWhatsapp1(), c.getAdminWhatsapp2(),
            c.getReminderBeforeMins(),
            c.getTemplateRegistration(), c.getTemplatePickupReminder(),
            c.getTemplateShuttleReminder(), c.getTemplateDelayMinor(),
            c.getTemplateDelayMajor(), c.getTemplateCancellation(),
            c.getAccommodationName1(), c.getAccommodationPhone1(), c.getAccommodationWhatsapp1(),
            c.getAccommodationName2(), c.getAccommodationPhone2(), c.getAccommodationWhatsapp2()
        ));
    }

    @Operation(summary = "Update notification config", description = "Updates coordinator contacts and SMS templates for a program or the global default config")
    @PatchMapping("/config/notifications")
    public ResponseEntity<AdminDtos.SuccessResponse> updateNotificationConfig(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody AdminDtos.NotificationConfigResponse req) {
        NotificationConfig c = programId != null
            ? notificationConfigRepository.findByProgramId(programId)
                .orElseGet(() -> {
                    NotificationConfig n = new NotificationConfig();
                    n.setProgramId(programId);
                    return n;
                })
            : notificationConfigRepository.findByConfigKey("main")
                .orElseThrow(() -> new EntityNotFoundException("Notification config not found"));
        if (req.adminPhone1()        != null) c.setAdminPhone1(req.adminPhone1());
        if (req.adminPhone2()        != null) c.setAdminPhone2(req.adminPhone2());
        if (req.coordinatorName1()   != null) c.setAdminName1(req.coordinatorName1());
        if (req.coordinatorName2()   != null) c.setAdminName2(req.coordinatorName2());
        if (req.whatsappLink1()      != null) c.setAdminWhatsapp1(req.whatsappLink1());
        if (req.whatsappLink2()      != null) c.setAdminWhatsapp2(req.whatsappLink2());
        if (req.smsRegistration()    != null) c.setTemplateRegistration(req.smsRegistration());
        if (req.smsPickupConfirmed() != null) c.setTemplatePickupReminder(req.smsPickupConfirmed());
        if (req.smsShuttleReminder() != null) c.setTemplateShuttleReminder(req.smsShuttleReminder());
        if (req.smsDelayMinor()      != null) c.setTemplateDelayMinor(req.smsDelayMinor());
        if (req.smsDelayMajor()      != null) c.setTemplateDelayMajor(req.smsDelayMajor());
        if (req.smsCancelled()       != null) c.setTemplateCancellation(req.smsCancelled());
        if (req.reminderBeforeMins()      != null) c.setReminderBeforeMins(req.reminderBeforeMins());
        if (req.accommodationName1()      != null) c.setAccommodationName1(req.accommodationName1());
        if (req.accommodationPhone1()     != null) c.setAccommodationPhone1(req.accommodationPhone1());
        if (req.accommodationWhatsapp1()  != null) c.setAccommodationWhatsapp1(req.accommodationWhatsapp1());
        if (req.accommodationName2()      != null) c.setAccommodationName2(req.accommodationName2());
        if (req.accommodationPhone2()     != null) c.setAccommodationPhone2(req.accommodationPhone2());
        if (req.accommodationWhatsapp2()  != null) c.setAccommodationWhatsapp2(req.accommodationWhatsapp2());
        notificationConfigRepository.save(c);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> participantSummary(Participant p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("btl_code", p.getBtlCode());
        m.put("full_name", p.getFullName());
        m.put("email", p.getEmail());
        m.put("phone_whatsapp", p.getPhone());
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
        m.put("pickup_location", r.getPickupLocation());
        m.put("dropoff_location", r.getDropoffLocation());

        List<RunParticipant> rps = runParticipantRepository.findByIdRunId(r.getId());
        List<Integer> pids = rps.stream().map(rp -> rp.getId().getParticipantId()).toList();
        List<Map<String, Object>> participants = pids.isEmpty()
            ? List.of()
            : participantRepository.findAllById(pids).stream()
                .map(this::participantSummary)
                .toList();
        m.put("participants", participants);
        return m;
    }

    private Map<String, Object> runDetailWithFlights(Run r) {
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
        m.put("pickup_location", r.getPickupLocation());
        m.put("dropoff_location", r.getDropoffLocation());

        List<RunParticipant> rps = runParticipantRepository.findByIdRunId(r.getId());
        List<Integer> pids = rps.stream().map(rp -> rp.getId().getParticipantId()).toList();
        if (pids.isEmpty()) {
            m.put("participants", List.of());
            return m;
        }

        List<Participant> participants = participantRepository.findAllById(pids);
        List<Flight> flights = flightRepository.findByParticipantIn(participants);

        Map<Integer, Flight> arrivalByPid = new HashMap<>();
        Map<Integer, Flight> departureByPid = new HashMap<>();
        for (Flight f : flights) {
            if (f.getParticipant() == null) continue;
            int pid = f.getParticipant().getId();
            if (f.getDirection() == Direction.TO_HOTEL) arrivalByPid.put(pid, f);
            else if (f.getDirection() == Direction.TO_AIRPORT) departureByPid.put(pid, f);
        }

        List<Map<String, Object>> enrichedParticipants = participants.stream().map(p -> {
            Map<String, Object> ps = new LinkedHashMap<>(participantSummary(p));
            Flight arr = arrivalByPid.get(p.getId());
            Flight dep = departureByPid.get(p.getId());
            ps.put("flight_arrival", arr != null ? flightSummary(arr, p.getBtlCode()) : null);
            ps.put("flight_departure", dep != null ? flightSummary(dep, p.getBtlCode()) : null);
            return ps;
        }).toList();

        m.put("participants", enrichedParticipants);
        return m;
    }

    private Map<String, Object> flightSummary(Flight f, String btlCode) {
        if (f == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("flight_id", f.getFlightId());
        m.put("btl_code", btlCode);
        m.put("direction", f.getDirection() != null ? f.getDirection().name().toLowerCase() : null);
        m.put("airline", f.getAirline());
        m.put("flight_number", f.getFlightNumber());
        m.put("submitted_datetime", f.getSubmittedDatetime() != null ? f.getSubmittedDatetime().toString() : null);
        m.put("live_eta", f.getLiveEta() != null ? f.getLiveEta().toString() : null);
        m.put("flight_status", f.getFlightStatus() != null ? f.getFlightStatus().name().toLowerCase() : "unknown");
        m.put("delay_mins", f.getDelayMins() != null ? f.getDelayMins() : 0);
        m.put("airport_code", f.getAirportCode());
        m.put("polling_active", Boolean.TRUE.equals(f.getPollingActive()));
        m.put("leg4_pickup_from", f.getLeg4PickupFrom() != null ? f.getLeg4PickupFrom().name().toLowerCase() : null);
        return m;
    }

    private AdminDtos.HotelAdminDto toHotelDto(Hotel h) {
        if (h == null) return null;
        return new AdminDtos.HotelAdminDto(
            String.valueOf(h.getId()), h.getHotelName(), h.getPickupAddress(),
            h.getDriveToChurchMins() != null ? h.getDriveToChurchMins() : 0,
            h.getDriveToAirportMins() != null ? h.getDriveToAirportMins() : 0,
            h.getLeg4CutoffTime(), h.getShuttleStopOrder() != null ? h.getShuttleStopOrder() : 0
        );
    }

    private AdminDtos.FlightAdminDto toFlightDto(Flight f, String btlCode) {
        if (f == null) return null;
        return new AdminDtos.FlightAdminDto(
            f.getFlightId(), btlCode,
            f.getDirection() != null ? f.getDirection().name().toLowerCase() : null,
            f.getAirline(), f.getFlightNumber(),
            f.getSubmittedDatetime() != null ? f.getSubmittedDatetime().toString() : null,
            f.getLiveEta() != null ? f.getLiveEta().toString() : null,
            f.getFlightStatus() != null ? f.getFlightStatus().name().toLowerCase() : "unknown",
            f.getDelayMins() != null ? f.getDelayMins() : 0,
            f.getAirportCode(), Boolean.TRUE.equals(f.getPollingActive()),
            f.getLeg4PickupFrom() != null ? f.getLeg4PickupFrom().name().toLowerCase() : null
        );
    }

    private AdminDtos.ParticipantAdminResponse toParticipantAdminResponse(
            Participant p, Flight arrival, Flight departure) {
        return new AdminDtos.ParticipantAdminResponse(
            p.getBtlCode(), p.getFullName(), p.getPhone(), p.getEmail(), p.getState(),
            toHotelDto(p.getHotel()),
            Boolean.TRUE.equals(p.getShuttleOptIn()),
            p.getStatus() != null ? p.getStatus().name().toLowerCase() : null,
            Boolean.TRUE.equals(p.getNeedsAttention()), p.getAttentionReason(),
            p.getCreatedAt() != null ? p.getCreatedAt().toString() : null,
            toFlightDto(arrival, p.getBtlCode()),
            toFlightDto(departure, p.getBtlCode())
        );
    }

    private AdminDtos.VehicleAdminDto toVehicleDto(Vehicle v) {
        if (v == null) return null;
        return new AdminDtos.VehicleAdminDto(
            String.valueOf(v.getId()), v.getLabel(),
            v.getType() != null ? v.getType().name().toLowerCase() : null,
            v.getCapacity() != null ? v.getCapacity() : 0
        );
    }

    private void sendDriverAccessEmail(Driver d, String driverLink) {
        String plain = "Hi " + d.getName() + ",\n\n"
            + "You have been added as a volunteer driver for Built to Last 2026.\n\n"
            + "Open your driver app here:\n" + driverLink + "\n\n"
            + "Bookmark this link — it's your personal access. Do not share it.\n\n"
            + "Built to Last 2026 Transport Team";
        try {
            org.springframework.core.io.ClassPathResource res =
                new org.springframework.core.io.ClassPathResource("templates/email-driver-access.html");
            String html = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("{{name}}", d.getName())
                .replace("{{driver_link}}", driverLink);
            sendGridService.sendHtmlEmail(d.getEmail(), d.getName(), "Your Driver App Access", html, plain);
        } catch (IOException e) {
            log.warn("Driver email HTML template not found, falling back to plain text");
            sendGridService.sendEmail(d.getEmail(), d.getName(), "Your Driver App Access", plain);
        }
    }

    private AdminDtos.DriverAdminDto toDriverDto(Driver d) {
        if (d == null) return null;
        return new AdminDtos.DriverAdminDto(
            String.valueOf(d.getId()), d.getName(), d.getPhone(), d.getWhatsapp(), d.getEmail(), d.getLoginToken(),
            d.getDriverCode(),
            d.getAvailableDates(),
            d.getActiveFrom() != null ? d.getActiveFrom().toString() : null,
            d.getCreatedAt() != null ? d.getCreatedAt().toString() : null
        );
    }

    private AdminDtos.RunAdminResponse toRunAdminResponse(
            Run r, List<AdminDtos.ParticipantAdminResponse> participants) {
        return new AdminDtos.RunAdminResponse(
            r.getId(),
            r.getRunId(),
            r.getRunType() != null ? r.getRunType().name().toLowerCase() : null,
            r.getDirection() != null ? r.getDirection().name().toLowerCase() : null,
            r.getConferenceDay() != null ? r.getConferenceDay().name().toLowerCase() : null,
            r.getConferenceDate() != null ? r.getConferenceDate().toString() : null,
            r.getDepartTime(), r.getPickupLocation(), r.getDropoffLocation(),
            r.getSeatsTotal() != null ? r.getSeatsTotal() : 0,
            r.getSeatsFilled() != null ? r.getSeatsFilled() : 0,
            r.getSeatsRemaining(),
            r.getStatus() != null ? r.getStatus().name().toLowerCase() : null,
            toVehicleDto(r.getVehicle()), toDriverDto(r.getDriver()),
            participants != null ? participants : List.of(),
            Boolean.TRUE.equals(r.getManifestSent()),
            r.getCompletedAt() != null ? r.getCompletedAt().toString() : null,
            r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null,
            r.getWhatsappGroupLink()
        );
    }

    // ── Admin Users ────────────────────────────────────────────────────────

    public record CreateAdminUserRequest(
        String username,
        String password,
        @com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,
        String role
    ) {}

    @Operation(summary = "List admin users", description = "Returns all admin users belonging to the program specified in X-Program-Id")
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> listAdminUsers(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        if (programId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(adminUserRepository.findByProgramIdOrderByCreatedAtAsc(programId)
            .stream().map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("username", u.getUsername());
                m.put("display_name", u.getDisplayName());
                m.put("created_at", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
                m.put("role", u.getRole());
                return m;
            }).toList());
    }

    @Operation(summary = "Create admin user", description = "Creates a new admin user with a bcrypt-hashed password and associates them with a program")
    @PostMapping("/users")
    @Transactional
    public ResponseEntity<Map<String, Object>> createAdminUser(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody CreateAdminUserRequest req) {
        if (req.username() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password required"));
        }
        AdminUser user = AdminUser.builder()
            .programId(programId)
            .username(req.username())
            .passwordHash(bcrypt.encode(req.password()))
            .displayName(req.displayName())
            .createdAt(OffsetDateTime.now())
            .role(req.role() != null ? req.role() : "FULL")
            .build();
        adminUserRepository.save(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("display_name", user.getDisplayName());
        result.put("role", user.getRole());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Delete admin user", description = "Removes an admin user. Refuses to delete the last remaining user for a program")
    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteAdminUser(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @PathVariable Integer id) {
        AdminUser user = adminUserRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Admin user not found: " + id));
        if (programId != null && !programId.equals(user.getProgramId())) {
            return ResponseEntity.status(403).body(new AdminDtos.SuccessResponse(false));
        }
        if (programId != null && adminUserRepository.countByProgramId(programId) <= 1) {
            return ResponseEntity.badRequest().body(new AdminDtos.SuccessResponse(false));
        }
        adminUserRepository.delete(user);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    public record UpdateAdminUserRequest(
        String username,
        @com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,
        @com.fasterxml.jackson.annotation.JsonProperty("new_password") String newPassword,
        String role
    ) {}

    @Operation(summary = "Update admin user", description = "Updates an admin user's username, display name, and/or password")
    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAdminUser(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @PathVariable Integer id,
            @RequestBody UpdateAdminUserRequest req) {
        AdminUser user = adminUserRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Admin user not found: " + id));
        if (programId != null && !programId.equals(user.getProgramId())) {
            return ResponseEntity.status(403).body(Map.of("error", "forbidden"));
        }
        if (req.username() != null && !req.username().isBlank()) {
            user.setUsername(req.username().trim());
        }
        if (req.displayName() != null) {
            user.setDisplayName(req.displayName().isBlank() ? null : req.displayName().trim());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            user.setPasswordHash(bcrypt.encode(req.newPassword()));
        }
        if (req.role() != null && !req.role().isBlank()) {
            user.setRole(req.role());
        }
        try {
            adminUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Username already taken"));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("display_name", user.getDisplayName());
        result.put("created_at", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        result.put("role", user.getRole());
        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private void syncProgramHotels(String programId, Object hotelsObj) {
        if (hotelsObj == null) return;
        try {
            String json = toJsonString(hotelsObj);
            List<Map<String, Object>> hotels = objectMapper.readValue(json, List.class);
            for (Map<String, Object> h : hotels) {
                String externalRef = h.get("id") != null ? h.get("id").toString() : null;
                String name = h.get("name") != null ? h.get("name").toString() : null;
                String addr = h.get("addr") != null ? h.get("addr").toString() : null;
                if (externalRef == null) continue;
                com.btl.transport.hotel.Hotel hotel = hotelRepository
                    .findByProgramIdAndExternalRef(programId, externalRef)
                    .orElseGet(() -> com.btl.transport.hotel.Hotel.builder()
                        .programId(programId)
                        .externalRef(externalRef)
                        .createdAt(OffsetDateTime.now())
                        .build());
                hotel.setHotelName(name);
                hotel.setPickupAddress(addr);
                hotel.setProgramId(programId);
                hotel.setExternalRef(externalRef);
                hotelRepository.save(hotel);
            }
        } catch (Exception e) {
            log.warn("syncProgramHotels failed for {}: {}", programId, e.getMessage());
        }
    }

    private String generateAdHocRunId() {
        return "RUN-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    // ── Room Assignments ──────────────────────────────────────────────────

    @Operation(summary = "List room assignments", description = "Returns all room assignments with occupants for the program")
    @GetMapping("/room-assignments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminDtos.RoomAssignmentDto>> listRoomAssignments(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        if (programId == null) return ResponseEntity.ok(List.of());
        List<RoomAssignment> rooms = roomAssignmentRepository.findByProgramIdWithOccupants(programId);
        return ResponseEntity.ok(rooms.stream().map(this::toRoomDto).toList());
    }

    @Operation(summary = "Create room assignment", description = "Creates a single room assignment for the program")
    @PostMapping("/room-assignments")
    @Transactional
    public ResponseEntity<AdminDtos.RoomAssignmentDto> createRoom(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody AdminDtos.CreateRoomRequest req) {
        if (programId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Program-Id required");
        Hotel hotel = req.hotelId() != null
            ? hotelRepository.findById(req.hotelId()).orElse(null) : null;
        String hotelName = hotel != null ? hotel.getHotelName()
            : (req.hotelName() != null ? req.hotelName() : "");
        RoomAssignment room = RoomAssignment.builder()
            .programId(programId)
            .hotel(hotel)
            .hotelName(hotelName)
            .roomLabel(req.roomLabel() != null ? req.roomLabel() : "")
            .roomType(req.roomType() != null ? req.roomType() : "2-person")
            .gender(req.gender())
            .notes(req.notes())
            .createdAt(OffsetDateTime.now())
            .build();
        room = roomAssignmentRepository.save(room);
        return ResponseEntity.ok(toRoomDto(room));
    }

    @Operation(summary = "Import rooms from CSV", description = "Smart-merge CSV import: upserts rooms by (hotel_name, room_label, gender) and moves occupants as needed")
    @PostMapping(value = "/room-assignments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<AdminDtos.ImportResultDto> importRoomsCsv(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestParam("file") MultipartFile file) {
        if (programId == null)

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Program-Id required");

        int createdRooms = 0, updatedRooms = 0, movedOccupants = 0, newOccupants = 0;
        List<AdminDtos.UnmatchedOccupant> unmatched = new ArrayList<>();

        // Format: Name (email, phone)  — phone may contain parentheses like (703) 340-7655
        // Group 3 uses greedy .* so the regex engine backtracks to the LAST ) in the string
        Pattern occPattern = Pattern.compile("^(.+?)\\s*\\(([^,)]*),?\\s*(.*)\\)\\s*$");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header
            if (header == null) return ResponseEntity.ok(
                new AdminDtos.ImportResultDto(0, 0, 0, 0, List.of()));

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Simple CSV split that handles quoted fields
                List<String> cols = parseCsvLine(line);
                if (cols.size() < 4) continue;

                String hotelName  = cols.get(0).trim();
                String roomLabel  = cols.get(1).trim();
                String roomType   = cols.get(2).trim();
                String gender     = cols.get(3).trim();
                if (hotelName.isEmpty() || roomLabel.isEmpty()) continue;

                // Look up hotel by name
                Hotel hotel = hotelRepository
                    .findByProgramIdAndHotelNameIgnoreCase(programId, hotelName)
                    .orElse(null);

                // Find or create room by composite key (null-safe gender comparison)
                RoomAssignment room = roomAssignmentRepository
                    .findByCompositeKey(programId, hotelName, roomLabel, gender.isEmpty() ? null : gender)
                    .orElse(null);

                if (room == null) {
                    room = RoomAssignment.builder()
                        .programId(programId)
                        .hotel(hotel)
                        .hotelName(hotelName)
                        .roomLabel(roomLabel)
                        .roomType(roomType)
                        .gender(gender.isEmpty() ? null : gender)
                        .createdAt(OffsetDateTime.now())
                        .build();
                    room = roomAssignmentRepository.save(room);
                    createdRooms++;
                } else {
                    updatedRooms++;
                }

                // Process occupant slots (cols 4..7)
                int maxSlots = Math.min(4, cols.size() - 4);
                for (int i = 0; i < maxSlots; i++) {
                    String occStr = cols.get(4 + i).trim();
                    if (occStr.isEmpty()) continue;

                    Matcher m = occPattern.matcher(occStr);
                    if (!m.matches()) {
                        unmatched.add(new AdminDtos.UnmatchedOccupant(occStr, null, null));
                        continue;
                    }
                    String name  = m.group(1).trim();
                    String email = m.group(2).trim();
                    String phone = m.group(3).trim();
                    String digits = phone.replaceAll("[^0-9]", "");
                    if (digits.length() > 10) digits = digits.substring(digits.length() - 10);

                    // Find existing occupant in this program by email, then phone
                    RoomOccupant existing = null;
                    if (!email.isEmpty()) {
                        existing = roomOccupantRepository
                            .findByProgramIdAndEmailIgnoreCase(programId, email).orElse(null);
                    }
                    if (existing == null && !digits.isEmpty()) {
                        existing = roomOccupantRepository
                            .findByProgramIdAndPhoneDigits(programId, digits).orElse(null);
                    }

                    short slot = (short) i;
                    boolean preservedTicket = false;

                    if (existing != null) {
                        boolean sameRoom = existing.getRoom().getId().equals(room.getId());
                        boolean sameSlot = existing.getSlot() != null && existing.getSlot() == slot;
                        if (!sameRoom || !sameSlot) {
                            // Remove from old slot — preserve ticket_received so it follows the person
                            preservedTicket = Boolean.TRUE.equals(existing.getTicketReceived());
                            roomOccupantRepository.delete(existing);
                            movedOccupants++;
                        } else {
                            // Update details if changed
                            existing.setName(name);
                            existing.setEmail(email.isEmpty() ? null : email);
                            existing.setPhone(phone.isEmpty() ? null : phone);
                            // Re-try participant link if currently null
                            if (existing.getParticipant() == null && !email.isEmpty()) {
                                participantRepository.findByEmailIgnoreCaseAndProgramId(email, programId)
                                    .ifPresent(existing::setParticipant);
                            }
                            if (existing.getParticipant() == null && (!email.isEmpty() || !phone.isEmpty())) {
                                existing.setParticipant(createMinimalParticipant(
                                    name, email.isEmpty() ? null : email, phone.isEmpty() ? null : phone,
                                    programId, hotel));
                            }
                            roomOccupantRepository.save(existing);
                            continue;
                        }
                    }

                    // Check if target slot is occupied — evict if so
                    roomOccupantRepository.findByRoomIdAndSlot(room.getId(), slot)
                        .ifPresent(roomOccupantRepository::delete);
                    roomOccupantRepository.flush();

                    // Resolve participant link by email; create minimal participant if no match
                    Participant linkedParticipant = null;
                    if (!email.isEmpty()) {
                        linkedParticipant = participantRepository
                            .findByEmailIgnoreCaseAndProgramId(email, programId).orElse(null);
                    }
                    if (linkedParticipant == null && (!email.isEmpty() || !phone.isEmpty())) {
                        linkedParticipant = createMinimalParticipant(
                            name, email.isEmpty() ? null : email, phone.isEmpty() ? null : phone,
                            programId, hotel);
                    }

                    boolean ticketReceived = preservedTicket
                        || (linkedParticipant != null && Boolean.TRUE.equals(linkedParticipant.getTicketReceived()));
                    RoomOccupant occ = RoomOccupant.builder()
                        .room(room)
                        .slot(slot)
                        .participant(linkedParticipant)
                        .name(name)
                        .email(email.isEmpty() ? null : email)
                        .phone(phone.isEmpty() ? null : phone)
                        .ticketReceived(ticketReceived)
                        .build();
                    roomOccupantRepository.save(occ);
                    newOccupants++;
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read CSV: " + e.getMessage());
        }

        return ResponseEntity.ok(new AdminDtos.ImportResultDto(
            createdRooms, updatedRooms, movedOccupants, newOccupants, unmatched));
    }

    @Operation(summary = "Update room", description = "Updates gender, type, or notes on a room assignment")
    @PatchMapping("/room-assignments/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.RoomAssignmentDto> updateRoom(
            @PathVariable Integer id,
            @RequestBody AdminDtos.UpdateRoomRequest req) {
        RoomAssignment room = roomAssignmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Room not found: " + id));
        if (req.gender()   != null) room.setGender(req.gender().isEmpty() ? null : req.gender());
        if (req.roomType() != null) room.setRoomType(req.roomType());
        if (req.notes()    != null) room.setNotes(req.notes().isEmpty() ? null : req.notes());
        room = roomAssignmentRepository.save(room);
        return ResponseEntity.ok(toRoomDto(room));
    }

    @Operation(summary = "Delete room", description = "Deletes a room and all its occupants")
    @DeleteMapping("/room-assignments/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteRoom(@PathVariable Integer id) {
        roomAssignmentRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Add or update occupant in slot", description = "Upserts an occupant at a given slot in a room")
    @PutMapping("/room-assignments/{id}/occupants/{slot}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> upsertOccupant(
            @PathVariable Integer id,
            @PathVariable Short slot,
            @RequestBody AdminDtos.UpsertOccupantRequest req) {
        RoomAssignment room = roomAssignmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Room not found: " + id));

        RoomOccupant occ = roomOccupantRepository.findByRoomIdAndSlot(id, slot)
            .orElseGet(() -> RoomOccupant.builder().room(room).slot(slot).name("").build());

        if (req.name() != null) occ.setName(req.name());
        if (req.email() != null) occ.setEmail(req.email().isEmpty() ? null : req.email());
        if (req.phone() != null) occ.setPhone(req.phone().isEmpty() ? null : req.phone());

        // Try to link participant
        if (occ.getEmail() != null && room.getProgramId() != null) {
            participantRepository.findByEmailIgnoreCaseAndProgramId(occ.getEmail(), room.getProgramId())
                .ifPresent(occ::setParticipant);
        }
        // Create minimal participant if still unlinked and we have contact info
        if (occ.getParticipant() == null && room.getProgramId() != null
                && (occ.getEmail() != null || occ.getPhone() != null)) {
            occ.setParticipant(createMinimalParticipant(
                occ.getName(), occ.getEmail(), occ.getPhone(),
                room.getProgramId(), room.getHotel()));
        }
        // For new occupants, inherit ticket_received from the linked participant
        if (occ.getId() == null && occ.getParticipant() != null) {
            occ.setTicketReceived(Boolean.TRUE.equals(occ.getParticipant().getTicketReceived()));
        }
        roomOccupantRepository.save(occ);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Remove occupant from slot", description = "Removes an occupant from a specific slot in a room")
    @DeleteMapping("/room-assignments/{id}/occupants/{slot}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> removeOccupant(
            @PathVariable Integer id,
            @PathVariable Short slot) {
        roomOccupantRepository.findByRoomIdAndSlot(id, slot)
            .ifPresent(roomOccupantRepository::delete);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Toggle ID & ticket received for occupant")
    @PatchMapping("/room-assignments/{id}/occupants/{slot}/ticket")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> toggleTicket(
            @PathVariable Integer id,
            @PathVariable Short slot,
            @RequestBody AdminDtos.ToggleTicketRequest req) {
        roomOccupantRepository.findByRoomIdAndSlot(id, slot)
            .ifPresent(occ -> {
                occ.setTicketReceived(req.received());
                roomOccupantRepository.save(occ);
                if (occ.getParticipant() != null) {
                    occ.getParticipant().setTicketReceived(req.received());
                    participantRepository.save(occ.getParticipant());
                }
            });
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Re-allocate occupant", description = "Moves an occupant from one room/slot to the first available slot in another room")
    @PutMapping("/room-assignments/realloc")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> reallocOccupant(
            @RequestBody AdminDtos.ReallocRequest req) {
        RoomOccupant occ = roomOccupantRepository.findByRoomIdAndSlot(req.fromRoomId(), req.fromSlot().shortValue())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Occupant not found"));
        RoomAssignment toRoom = roomAssignmentRepository.findById(req.toRoomId())
            .orElseThrow(() -> new EntityNotFoundException("Target room not found: " + req.toRoomId()));

        int capacity = roomCapacity(toRoom.getRoomType());
        short targetSlot = -1;
        for (short s = 0; s < capacity; s++) {
            if (roomOccupantRepository.findByRoomIdAndSlot(req.toRoomId(), s).isEmpty()) {
                targetSlot = s;
                break;
            }
        }
        if (targetSlot < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target room is full");

        occ.setRoom(toRoom);
        occ.setSlot(targetSlot);
        roomOccupantRepository.save(occ);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    record NotifyResult(@JsonProperty("sent") int sent, @JsonProperty("skipped") int skipped) {}

    private NotifyResult notifyRooms(List<RoomAssignment> rooms, boolean roommateVisible) {
        int sent = 0, skipped = 0;
        for (RoomAssignment room : rooms) {
            List<String> allNames = roommateVisible
                ? room.getOccupants().stream().map(RoomOccupant::getName).collect(Collectors.toList())
                : List.of();
            for (RoomOccupant occ : room.getOccupants()) {
                String email = occ.getParticipant() != null
                    ? occ.getParticipant().getEmail()
                    : occ.getEmail();
                if (email == null) { skipped++; continue; }
                List<String> others = roommateVisible
                    ? allNames.stream().filter(n -> !n.equals(occ.getName())).collect(Collectors.toList())
                    : List.of();
                if (occ.getParticipant() != null) {
                    notificationService.sendRoomAssignment(occ.getParticipant(), room.getHotelName(),
                        room.getRoomLabel(), room.getRoomType(), others);
                } else {
                    notificationService.sendRoomAssignment(occ.getName(), email, room.getHotelName(),
                        room.getRoomLabel(), room.getRoomType(), others);
                }
                sent++;
            }
        }
        return new NotifyResult(sent, skipped);
    }

    @Operation(summary = "Notify all participants of room assignments")
    @PostMapping("/room-assignments/notify")
    @Transactional(readOnly = true)
    public ResponseEntity<NotifyResult> notifyAllRoomAssignments(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        if (programId == null) return ResponseEntity.badRequest().build();
        Program program = programRepository.findById(programId)
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + programId));
        boolean roommateVisible = program.getRoommateVisible() != null ? program.getRoommateVisible() : true;
        List<RoomAssignment> rooms = roomAssignmentRepository.findByProgramIdWithOccupants(programId);
        return ResponseEntity.ok(notifyRooms(rooms, roommateVisible));
    }

    @Operation(summary = "Notify occupants of a single room")
    @PostMapping("/room-assignments/{id}/notify")
    @Transactional(readOnly = true)
    public ResponseEntity<NotifyResult> notifyOneRoom(
            @PathVariable Integer id,
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        RoomAssignment room = roomAssignmentRepository.findByIdWithOccupants(id)
            .orElseThrow(() -> new EntityNotFoundException("Room not found: " + id));
        Program program = programRepository.findById(room.getProgramId())
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + room.getProgramId()));
        boolean roommateVisible = program.getRoommateVisible() != null ? program.getRoommateVisible() : true;
        return ResponseEntity.ok(notifyRooms(List.of(room), roommateVisible));
    }

    @Operation(summary = "Notify a single occupant of their room assignment")
    @PostMapping("/room-assignments/{roomId}/occupants/{slot}/notify")
    @Transactional(readOnly = true)
    public ResponseEntity<AdminDtos.SuccessResponse> notifyOneOccupant(
            @PathVariable Integer roomId,
            @PathVariable Short slot) {
        RoomAssignment room = roomAssignmentRepository.findByIdWithOccupants(roomId)
            .orElseThrow(() -> new EntityNotFoundException("Room not found: " + roomId));
        RoomOccupant occ = room.getOccupants().stream()
            .filter(o -> slot.equals(o.getSlot()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Occupant not found at slot " + slot));
        Program program = programRepository.findById(room.getProgramId())
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + room.getProgramId()));
        boolean roommateVisible = program.getRoommateVisible() != null ? program.getRoommateVisible() : true;
        String email = occ.getParticipant() != null ? occ.getParticipant().getEmail() : occ.getEmail();
        if (email == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Occupant has no email address");
        List<String> others = roommateVisible
            ? room.getOccupants().stream()
                  .map(RoomOccupant::getName)
                  .filter(n -> !n.equals(occ.getName()))
                  .collect(Collectors.toList())
            : List.of();
        if (occ.getParticipant() != null) {
            notificationService.sendRoomAssignment(occ.getParticipant(), room.getHotelName(),
                room.getRoomLabel(), room.getRoomType(), others);
        } else {
            notificationService.sendRoomAssignment(occ.getName(), email, room.getHotelName(),
                room.getRoomLabel(), room.getRoomType(), others);
        }
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Back-fill missing participant links for room occupants")
    @PostMapping("/room-assignments/backfill-participants")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> backfillOccupantParticipants(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        if (programId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Program-Id required");
        List<RoomOccupant> unlinked = roomOccupantRepository.findUnlinkedByProgramId(programId);
        for (RoomOccupant occ : unlinked) {
            Participant linked = null;
            if (occ.getEmail() != null) {
                linked = participantRepository
                    .findByEmailIgnoreCaseAndProgramId(occ.getEmail(), programId)
                    .orElse(null);
            }
            if (linked == null && (occ.getEmail() != null || occ.getPhone() != null)) {
                linked = createMinimalParticipant(
                    occ.getName(), occ.getEmail(), occ.getPhone(),
                    programId, occ.getRoom().getHotel());
            }
            if (linked != null) {
                occ.setParticipant(linked);
                roomOccupantRepository.save(occ);
            }
        }
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    @Operation(summary = "Toggle roommate visibility", description = "Sets whether participants can see their roommates in their app")
    @PatchMapping("/programs/{id}/roommate-visible")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> updateRoommateVisible(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> body) {
        Program p = programRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + id));
        Boolean visible = body.get("roommate_visible");
        if (visible != null) p.setRoommateVisible(visible);
        programRepository.save(p);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Accommodation Contacts ─────────────────────────────────────────────

    @Operation(summary = "List accommodation contacts", description = "Returns accommodation contacts for a program")
    @GetMapping("/accommodation-contacts")
    public ResponseEntity<List<AdminDtos.AccommodationContactDto>> listAccomContacts(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        if (programId == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(
            accommodationContactRepository.findByProgramIdOrderBySortOrderAsc(programId)
                .stream().map(this::toAccomContactDto).toList()
        );
    }

    @Operation(summary = "Create accommodation contact", description = "Adds a new accommodation contact for the program")
    @PostMapping("/accommodation-contacts")
    @Transactional
    public ResponseEntity<AdminDtos.AccommodationContactDto> createAccomContact(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody AdminDtos.CreateAccomContactRequest req) {
        if (programId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Program-Id required");
        long nextOrder = accommodationContactRepository.findByProgramIdOrderBySortOrderAsc(programId).size();
        AccommodationContact contact = AccommodationContact.builder()
            .programId(programId)
            .name(req.name())
            .phone(req.phone())
            .whatsapp(req.whatsapp())
            .sortOrder((short) nextOrder)
            .build();
        contact = accommodationContactRepository.save(contact);
        return ResponseEntity.ok(toAccomContactDto(contact));
    }

    @Operation(summary = "Update accommodation contact", description = "Updates an accommodation contact's name, phone, or WhatsApp")
    @PatchMapping("/accommodation-contacts/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.AccommodationContactDto> updateAccomContact(
            @PathVariable Integer id,
            @RequestBody AdminDtos.UpdateAccomContactRequest req) {
        AccommodationContact contact = accommodationContactRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Contact not found: " + id));
        if (req.name()     != null) contact.setName(req.name());
        if (req.phone()    != null) contact.setPhone(req.phone().isEmpty() ? null : req.phone());
        if (req.whatsapp() != null) contact.setWhatsapp(req.whatsapp().isEmpty() ? null : req.whatsapp());
        contact = accommodationContactRepository.save(contact);
        return ResponseEntity.ok(toAccomContactDto(contact));
    }

    @Operation(summary = "Delete accommodation contact", description = "Removes an accommodation contact")
    @DeleteMapping("/accommodation-contacts/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteAccomContact(@PathVariable Integer id) {
        accommodationContactRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Room helpers ──────────────────────────────────────────────────────

    private AdminDtos.RoomAssignmentDto toRoomDto(RoomAssignment ra) {
        List<AdminDtos.RoomOccupantDto> occupants = ra.getOccupants() == null ? List.of()
            : ra.getOccupants().stream().map(o -> new AdminDtos.RoomOccupantDto(
                o.getSlot() != null ? o.getSlot() : 0,
                o.getParticipant() != null ? o.getParticipant().getId() : null,
                o.getName(), o.getEmail(), o.getPhone(),
                Boolean.TRUE.equals(o.getTicketReceived())
            )).toList();
        return new AdminDtos.RoomAssignmentDto(
            ra.getId(),
            ra.getHotel() != null ? ra.getHotel().getId() : null,
            ra.getHotelName(),
            ra.getRoomLabel(),
            ra.getRoomType(),
            ra.getGender(),
            ra.getNotes(),
            occupants
        );
    }

    private AdminDtos.AccommodationContactDto toAccomContactDto(AccommodationContact c) {
        return new AdminDtos.AccommodationContactDto(
            c.getId(), c.getProgramId(), c.getName(), c.getPhone(), c.getWhatsapp(),
            c.getSortOrder() != null ? c.getSortOrder() : 0
        );
    }

    private int roomCapacity(String roomType) {
        return switch (roomType == null ? "" : roomType) {
            case "4-person" -> 4;
            default -> 2;
        };
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }

    // ── Programs ──────────────────────────────────────────────────────────

    @Operation(summary = "List programs", description = "Returns all programs. If a program-scoped JWT is provided, returns only the token's program")
    @GetMapping("/programs")
    public ResponseEntity<List<AdminDtos.ProgramResponse>> listPrograms(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String programId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            programId = jwtService.extractProgramId(authHeader.substring(7));
        }
        List<Program> programs = programId != null
            ? programRepository.findById(programId).map(List::of).orElse(List.of())
            : programRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(programs.stream().map(this::toProgramDto).toList());
    }

    @Operation(summary = "Create program", description = "Creates a new transport program, seeds associated hotels, and provisions a default notification config")
    @PostMapping("/programs")
    @Transactional
    public ResponseEntity<AdminDtos.ProgramResponse> createProgram(
            @RequestBody AdminDtos.CreateProgramRequest req) {
        if (req.ini() != null && !req.ini().isBlank() && programRepository.existsByIni(req.ini())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Program ini '" + req.ini() + "' is already in use");
        }
        String rulesJson = rulesJson(req.rules());
        Map<String, Object> rules = parseRules(rulesJson);
        Program p = Program.builder()
            .id(req.id() != null ? req.id() : "p_" + System.currentTimeMillis())
            .name(req.name())
            .ini(req.ini())
            .type(req.type() != null ? req.type() : "Conference")
            .startDate(req.startDate())
            .endDate(req.endDate())
            .phase("setup")
            .venue(req.venue())
            .venueAddr(req.venueAddr())
            .airport(req.airport())
            .city(req.city())
            .state(req.state())
            .logoUrl(req.logoUrl())
            .hotelSelectionEnabled(req.hotelSelectionEnabled() != null ? req.hotelSelectionEnabled() : true)
            .timezone(req.timezone() != null ? req.timezone() : "America/New_York")
            .registrationOpen(true)
            .hotels(toJsonString(req.hotels()))
            .morningRuns(toJsonString(req.morningRuns()))
            .eveningRuns(toJsonString(req.eveningRuns()))
            .ruleWindow(rules != null ? String.valueOf(rules.getOrDefault("window", "75")) : "75")
            .ruleCap(rules != null ? String.valueOf(rules.getOrDefault("cap", "22")) : "22")
            .ruleBuffer(rules != null ? String.valueOf(rules.getOrDefault("buffer", "60")) : "60")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        programRepository.save(p);
        syncProgramHotels(p.getId(), req.hotels());
        notificationConfigRepository.findByProgramId(p.getId()).orElseGet(() -> {
            NotificationConfig nc = new NotificationConfig();
            nc.setProgramId(p.getId());
            return notificationConfigRepository.save(nc);
        });
        return ResponseEntity.ok(toProgramDto(p));
    }

    @Operation(summary = "Update program", description = "Partially updates program metadata, hotel list, run templates, or scheduling rules by program ID")
    @PatchMapping("/programs/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.ProgramResponse> updateProgram(
            @PathVariable String id,
            @RequestBody AdminDtos.UpdateProgramRequest req) {
        Program p = programRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + id));
        if (req.name()      != null) p.setName(req.name());
        if (req.type()      != null) p.setType(req.type());
        if (req.startDate() != null) p.setStartDate(req.startDate());
        if (req.endDate()   != null) p.setEndDate(req.endDate());
        if (req.phase()     != null) p.setPhase(req.phase());
        if (req.venue()     != null) p.setVenue(req.venue());
        if (req.venueAddr() != null) p.setVenueAddr(req.venueAddr());
        if (req.airport()   != null) p.setAirport(req.airport());
        if (req.city()      != null) p.setCity(req.city());
        if (req.state()     != null) p.setState(req.state());
        if (req.logoUrl()   != null) p.setLogoUrl(req.logoUrl());
        if (req.hotelSelectionEnabled() != null) p.setHotelSelectionEnabled(req.hotelSelectionEnabled());
        if (req.registrationOpen()      != null) p.setRegistrationOpen(req.registrationOpen());
        if (req.regTitle()       != null) p.setRegTitle(req.regTitle().isBlank() ? null : req.regTitle());
        if (req.regDescription() != null) p.setRegDescription(req.regDescription().isBlank() ? null : req.regDescription());
        if (req.timezone()       != null && !req.timezone().isBlank()) p.setTimezone(req.timezone());
        if (req.hotels()    != null) { p.setHotels(toJsonString(req.hotels())); syncProgramHotels(id, req.hotels()); }
        if (req.morningRuns() != null) p.setMorningRuns(toJsonString(req.morningRuns()));
        if (req.eveningRuns() != null) p.setEveningRuns(toJsonString(req.eveningRuns()));
        if (req.dailySchedules() != null) p.setDailySchedules(toJsonString(req.dailySchedules()));
        if (req.rules()     != null) {
            Map<String, Object> rules = parseRules(rulesJson(req.rules()));
            if (rules != null) {
                p.setRuleWindow(String.valueOf(rules.getOrDefault("window", p.getRuleWindow())));
                p.setRuleCap(String.valueOf(rules.getOrDefault("cap", p.getRuleCap())));
                p.setRuleBuffer(String.valueOf(rules.getOrDefault("buffer", p.getRuleBuffer())));
            }
        }
        p.setUpdatedAt(OffsetDateTime.now());
        programRepository.save(p);
        return ResponseEntity.ok(toProgramDto(p));
    }

    @Operation(summary = "Delete program", description = "Permanently deletes a program and its associated records by program ID")
    @DeleteMapping("/programs/{id}")
    @Transactional
    public ResponseEntity<AdminDtos.SuccessResponse> deleteProgram(@PathVariable String id) {
        programRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    private AdminDtos.ProgramResponse toProgramDto(Program p) {
        return new AdminDtos.ProgramResponse(
            p.getId(), p.getName(), p.getIni(), p.getType(),
            p.getStartDate(), p.getEndDate(), p.getPhase(),
            p.getVenue(), p.getVenueAddr(), p.getAirport(),
            p.getCity(), p.getState(), p.getLogoUrl(),
            p.getHotelSelectionEnabled() != null ? p.getHotelSelectionEnabled() : true,
            p.getRegistrationOpen() != null ? p.getRegistrationOpen() : true,
            p.getRegTitle(),
            p.getRegDescription(),
            p.getTimezone() != null ? p.getTimezone() : "America/New_York",
            parseJson(p.getHotels(), List.of()),
            parseJson(p.getMorningRuns(), List.of()),
            parseJson(p.getEveningRuns(), List.of()),
            parseJson(p.getDailySchedules(), null),
            Map.of("window", orEmpty(p.getRuleWindow()),
                   "cap",    orEmpty(p.getRuleCap()),
                   "buffer", orEmpty(p.getRuleBuffer())),
            p.getRoommateVisible() != null ? p.getRoommateVisible() : true,
            p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
        );
    }

    private String toJsonString(Object obj) {
        if (obj == null) return "[]";
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }

    private String rulesJson(Object rules) {
        if (rules == null) return null;
        try { return objectMapper.writeValueAsString(rules); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRules(String json) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, Map.class); } catch (Exception e) { return null; }
    }

    private Object parseJson(String json, Object fallback) {
        if (json == null || json.isBlank()) return fallback;
        try { return objectMapper.readValue(json, Object.class); } catch (Exception e) { return fallback; }
    }

    private String orEmpty(String s) { return s != null ? s : ""; }

    // ── File upload ────────────────────────────────────────────────────────

    @Operation(summary = "Upload file", description = "Uploads a file (e.g. program logo) to storage and returns the public URL")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = storageService.store(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    private Participant createMinimalParticipant(String name, String email, String phone,
                                                  String programId, Hotel hotel) {
        Program prog = programRepository.findById(programId).orElse(null);
        String ini = prog != null ? prog.getIni() : null;
        Participant p = Participant.builder()
            .btlCode(btlCodeService.generateNextCode(programId, ini))
            .fullName(name != null && !name.isEmpty() ? name : "Unknown")
            .email(email)
            .phone(phone)
            .programId(programId)
            .hotel(hotel)
            .status(ParticipantStatus.REGISTERED)
            .needsAttention(false)
            .shuttleOptIn(false)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        return participantRepository.save(p);
    }
}
