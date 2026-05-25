package com.btl.transport.admin;

import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
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
import com.btl.transport.notification.SendGridService;
import com.btl.transport.notification.TwilioService;
import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.run.*;
import com.btl.transport.infrastructure.StorageService;
import com.btl.transport.vehicle.Vehicle;
import com.btl.transport.vehicle.VehicleRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

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
    private final ProgramRepository programRepository;
    private final HotelRepository hotelRepository;
    private final ObjectMapper objectMapper;
    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final StorageService storageService;
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    // ── Dashboard ──────────────────────────────────────────────────────────
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
        List<Map<String, Object>> items = filtered.subList(fromIdx, toIdx).stream()
            .map(this::participantSummary).toList();

        return ResponseEntity.ok(Map.of(
            "content", items,
            "total_elements", (long) total,
            "total_pages", size > 0 ? (int) Math.ceil((double) total / size) : 0,
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
        @RequestBody UpdateHotelRequest req
    ) {
        Participant p = participantRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + id));
        // Hotel lookup — simplified; full impl would fetch hotel by req.hotelId()
        p.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(p);
        return ResponseEntity.ok(Map.of("success", true));
    }

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
    @GetMapping("/drivers")
    public ResponseEntity<List<AdminDtos.DriverAdminDto>> listDrivers(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        List<Driver> drivers = programId != null
            ? driverRepository.findByProgramId(programId)
            : driverRepository.findAll();
        return ResponseEntity.ok(drivers.stream().map(this::toDriverDto).toList());
    }

    @PostMapping("/drivers")
    public ResponseEntity<AdminDtos.DriverAdminDto> createDriver(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody Driver driver) {
        driver.setCreatedAt(OffsetDateTime.now());
        driver.setProgramId(programId);
        Driver saved = driverRepository.save(driver);
        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            try {
                String body = "Hi " + saved.getName() + ",\n\n"
                    + "Your driver login code is: " + saved.getId() + "\n\n"
                    + "Use this code to access your driver app and view your assigned runs.";
                sendGridService.sendEmail(saved.getEmail(), saved.getName(), "Your Driver Login Code", body);
            } catch (Exception e) {
                log.warn("Failed to send driver welcome email to {}: {}", saved.getEmail(), e.getMessage());
            }
        }
        return ResponseEntity.ok(toDriverDto(saved));
    }

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

    @DeleteMapping("/drivers/{id}")
    public ResponseEntity<AdminDtos.SuccessResponse> deleteDriver(@PathVariable Integer id) {
        driverRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Resend code ──────────────────────────────────────────────────────────
    @PostMapping("/participants/{btlCode}/resend-code")
    public ResponseEntity<AdminDtos.SuccessResponse> resendCode(@PathVariable String btlCode) {
        Participant p = participantRepository.findByBtlCode(btlCode)
            .orElseThrow(() -> new EntityNotFoundException("Not found: " + btlCode));
        if (p.getEmail() != null && !p.getEmail().isBlank()) {
            String body = "Your BTL Transport code is: " + p.getBtlCode()
                + "\n\nCheck your transport status at: "
                + "https://btl.transport/status?code=" + p.getBtlCode();
            sendGridService.sendEmail(p.getEmail(), p.getFullName(),
                "Your BTL Transport Code", body);
        }
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Vehicles ──────────────────────────────────────────────────────────
    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> listVehicles(
            @RequestHeader(value = "X-Program-Id", required = false) String programId) {
        List<Vehicle> vehicles = programId != null
            ? vehicleRepository.findByProgramId(programId)
            : vehicleRepository.findAll();
        return ResponseEntity.ok(vehicles);
    }

    @PostMapping("/vehicles")
    public ResponseEntity<Vehicle> createVehicle(
            @RequestHeader(value = "X-Program-Id", required = false) String programId,
            @RequestBody Vehicle vehicle) {
        vehicle.setCreatedAt(OffsetDateTime.now());
        vehicle.setProgramId(programId);
        return ResponseEntity.ok(vehicleRepository.save(vehicle));
    }

    @PatchMapping("/vehicles/{id}")
    public ResponseEntity<Vehicle> updateVehicle(@PathVariable Integer id, @RequestBody Vehicle updates) {
        Vehicle v = vehicleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + id));
        if (updates.getLabel()    != null) v.setLabel(updates.getLabel());
        if (updates.getCapacity() != null) v.setCapacity(updates.getCapacity());
        if (updates.getType()     != null) v.setType(updates.getType());
        return ResponseEntity.ok(vehicleRepository.save(v));
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<AdminDtos.SuccessResponse> deleteVehicle(@PathVariable Integer id) {
        vehicleRepository.deleteById(id);
        return ResponseEntity.ok(new AdminDtos.SuccessResponse(true));
    }

    // ── Stats ─────────────────────────────────────────────────────────────

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

    @GetMapping("/participant/{code}")
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

    @PatchMapping("/participant/{code}")
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

    // ── Alert (singular path alias) ────────────────────────────────────────

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

    @PatchMapping("/run/{runId}")
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

    @PostMapping("/run")
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

    @GetMapping("/manifest/{driverId}")
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
            c.getTemplateDelayMajor(), c.getTemplateCancellation()
        ));
    }

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
        if (req.reminderBeforeMins() != null) c.setReminderBeforeMins(req.reminderBeforeMins());
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
            p.getBtlCode(), p.getFullName(), p.getPhone(), p.getEmail(),
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

    private AdminDtos.DriverAdminDto toDriverDto(Driver d) {
        if (d == null) return null;
        return new AdminDtos.DriverAdminDto(
            String.valueOf(d.getId()), d.getName(), d.getPhone(), d.getWhatsapp(), d.getEmail()
        );
    }

    private AdminDtos.RunAdminResponse toRunAdminResponse(
            Run r, List<AdminDtos.ParticipantAdminResponse> participants) {
        return new AdminDtos.RunAdminResponse(
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
            r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null
        );
    }

    // ── Admin Users ────────────────────────────────────────────────────────

    public record CreateAdminUserRequest(
        String username,
        String password,
        @com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName
    ) {}

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
                return m;
            }).toList());
    }

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
            .build();
        adminUserRepository.save(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("display_name", user.getDisplayName());
        return ResponseEntity.ok(result);
    }

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
        long count = runRepository.count() + 1;
        return String.format("RUN-%03d", count);
    }

    // ── Programs ──────────────────────────────────────────────────────────

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

    @PostMapping("/programs")
    @Transactional
    public ResponseEntity<AdminDtos.ProgramResponse> createProgram(
            @RequestBody AdminDtos.CreateProgramRequest req) {
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
        if (req.hotels()    != null) { p.setHotels(toJsonString(req.hotels())); syncProgramHotels(id, req.hotels()); }
        if (req.morningRuns() != null) p.setMorningRuns(toJsonString(req.morningRuns()));
        if (req.eveningRuns() != null) p.setEveningRuns(toJsonString(req.eveningRuns()));
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
            parseJson(p.getHotels(), List.of()),
            parseJson(p.getMorningRuns(), List.of()),
            parseJson(p.getEveningRuns(), List.of()),
            Map.of("window", orEmpty(p.getRuleWindow()),
                   "cap",    orEmpty(p.getRuleCap()),
                   "buffer", orEmpty(p.getRuleBuffer())),
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = storageService.store(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
