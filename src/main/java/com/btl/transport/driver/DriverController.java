package com.btl.transport.driver;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.RunStatusEnum;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.participant.Participant;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunParticipant;
import com.btl.transport.run.RunParticipantRepository;
import com.btl.transport.run.RunRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Driver", description = "Public driver-facing endpoints authenticated by driver code")
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverController {

    private final DriverRepository driverRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final NotificationConfigRepository notificationConfigRepository;
    private final ProgramRepository programRepository;

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record AdminContact(
        @JsonProperty("name")     String name,
        @JsonProperty("phone")    String phone,
        @JsonProperty("whatsapp") String whatsapp
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PassengerSummary(
        @JsonProperty("btl_code")       String btlCode,
        @JsonProperty("full_name")      String fullName,
        @JsonProperty("boarded")        Boolean boarded,
        @JsonProperty("hotel_name")     String hotelName,
        @JsonProperty("flight_airline") String flightAirline,
        @JsonProperty("flight_number")  String flightNumber,
        @JsonProperty("flight_time")    String flightTime
    ) {}

    record RunSummary(
        @JsonProperty("run_id")           String runId,
        @JsonProperty("direction")        String direction,
        @JsonProperty("run_type")         String runType,
        @JsonProperty("conference_day")   String conferenceDay,
        @JsonProperty("conference_date")  String conferenceDate,
        @JsonProperty("depart_time")      String departTime,
        @JsonProperty("pickup_location")  String pickupLocation,
        @JsonProperty("dropoff_location") String dropoffLocation,
        @JsonProperty("seats_total")      int seatsTotal,
        @JsonProperty("seats_filled")     int seatsFilled,
        @JsonProperty("status")           String status,
        @JsonProperty("completed_at")     String completedAt,
        @JsonProperty("passengers")       List<PassengerSummary> passengers
    ) {}

    record DriverMeResponse(
        @JsonProperty("driver_id")          String driverId,
        @JsonProperty("name")               String name,
        @JsonProperty("phone")              String phone,
        @JsonProperty("whatsapp")           String whatsapp,
        @JsonProperty("driver_code")        String driverCode,
        @JsonProperty("program_id")         String programId,
        @JsonProperty("program_name")       String programName,
        @JsonProperty("program_start")      String programStart,
        @JsonProperty("program_end")        String programEnd,
        @JsonProperty("program_logo_url")   String programLogoUrl,
        @JsonProperty("vehicle_label")      String vehicleLabel,
        @JsonProperty("vehicle_capacity")   Integer vehicleCapacity,
        @JsonProperty("admins")             List<AdminContact> admins,
        @JsonProperty("runs")               List<RunSummary> runs
    ) {}

    record UpdateStatusRequest(String status) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @Operation(summary = "Get driver schedule", description = "Returns driver identity, assigned runs, and admin contacts. Authenticated by driver code.")
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<DriverMeResponse> me(@RequestParam String code) {
        Driver driver = driverRepository.findByDriverCode(code.toUpperCase())
            .orElseThrow(() -> new EntityNotFoundException("Code not found. Check your setup email and try again."));

        List<Run> runs = runRepository.findByDriverIdOrderByConferenceDateAscDepartTimeAsc(driver.getId());

        // Batch-load all participants and their flights for this driver's runs
        List<RunParticipant> allRps = runs.stream()
            .flatMap(r -> runParticipantRepository.findByIdRunId(r.getId()).stream())
            .toList();
        List<Integer> allPids = allRps.stream().map(rp -> rp.getId().getParticipantId()).distinct().toList();
        Map<Integer, Participant> participantMap = allPids.isEmpty()
            ? Map.of()
            : participantRepository.findAllById(allPids).stream()
                .collect(Collectors.toMap(Participant::getId, p -> p));
        List<Flight> allFlights = allPids.isEmpty()
            ? List.of()
            : flightRepository.findByParticipantIn(
                participantMap.values().stream().toList());
        Map<Integer, List<Flight>> flightsByParticipant = allFlights.stream()
            .collect(Collectors.groupingBy(f -> f.getParticipant().getId()));

        List<RunSummary> runSummaries = runs.stream().map(run -> {
            List<RunParticipant> rps = allRps.stream()
                .filter(rp -> rp.getId().getRunId().equals(run.getId())).toList();
            List<PassengerSummary> passengers = rps.stream().map(rp -> {
                Participant p = participantMap.get(rp.getId().getParticipantId());
                if (p == null) return null;
                String hotelName = p.getHotel() != null ? p.getHotel().getHotelName() : null;
                // Pick the relevant flight: arrival flight for to_hotel runs, departure for to_airport
                Flight flight = null;
                List<Flight> pFlights = flightsByParticipant.getOrDefault(p.getId(), List.of());
                if (run.getDirection() == Direction.TO_HOTEL) {
                    flight = pFlights.stream().filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
                } else if (run.getDirection() == Direction.TO_AIRPORT) {
                    flight = pFlights.stream().filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);
                }
                return new PassengerSummary(
                    p.getBtlCode(), p.getFullName(),
                    Boolean.TRUE.equals(rp.getBoarded()),
                    hotelName,
                    flight != null ? flight.getAirline() : null,
                    flight != null ? flight.getFlightNumber() : null,
                    flight != null && flight.getSubmittedDatetime() != null ? flight.getSubmittedDatetime().toString() : null
                );
            }).filter(ps -> ps != null).toList();

            return new RunSummary(
                run.getRunId(),
                run.getDirection() != null ? run.getDirection().name().toLowerCase() : null,
                run.getRunType() != null ? run.getRunType().name().toLowerCase() : null,
                run.getConferenceDay() != null ? run.getConferenceDay().name().toLowerCase() : null,
                run.getConferenceDate() != null ? run.getConferenceDate().toString() : null,
                run.getDepartTime(),
                run.getPickupLocation(),
                run.getDropoffLocation(),
                run.getSeatsTotal() != null ? run.getSeatsTotal() : 0,
                run.getSeatsFilled() != null ? run.getSeatsFilled() : 0,
                run.getStatus() != null ? run.getStatus().name().toLowerCase() : null,
                run.getCompletedAt() != null ? run.getCompletedAt().toString() : null,
                passengers
            );
        }).toList();

        // Load admin contacts from notification config
        NotificationConfig cfg = notificationConfigRepository.findByConfigKey("main").orElse(null);
        List<AdminContact> admins = buildAdminContacts(cfg);

        // Load program info
        Program program = driver.getProgramId() != null
            ? programRepository.findById(driver.getProgramId()).orElse(null) : null;

        // Derive vehicle from first run that has one assigned
        var vehicleRun = runs.stream().filter(r -> r.getVehicle() != null).findFirst();
        String vehicleLabel    = vehicleRun.map(r -> r.getVehicle().getLabel()).orElse(null);
        Integer vehicleCapacity = vehicleRun.map(r -> r.getVehicle().getCapacity()).orElse(null);

        return ResponseEntity.ok(new DriverMeResponse(
            String.valueOf(driver.getId()),
            driver.getName(),
            driver.getPhone(),
            driver.getWhatsapp(),
            driver.getDriverCode(),
            driver.getProgramId(),
            program != null ? program.getName() : null,
            program != null ? program.getStartDate() : null,
            program != null ? program.getEndDate() : null,
            program != null ? program.getLogoUrl() : null,
            vehicleLabel,
            vehicleCapacity,
            admins,
            runSummaries
        ));
    }

    @Operation(summary = "Update run status", description = "Driver updates a run to en_route or completed.")
    @PatchMapping("/run/{runId}/status")
    @Transactional
    public ResponseEntity<Map<String, String>> updateRunStatus(
            @PathVariable String runId,
            @RequestParam String code,
            @RequestBody UpdateStatusRequest req) {
        Driver driver = driverRepository.findByDriverCode(code.toUpperCase())
            .orElseThrow(() -> new EntityNotFoundException("Invalid driver code."));
        Run run = runRepository.findByRunId(runId)
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + runId));
        if (run.getDriver() == null || !run.getDriver().getId().equals(driver.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This run is not assigned to you.");
        }
        RunStatusEnum newStatus;
        try {
            newStatus = RunStatusEnum.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + req.status());
        }
        run.setStatus(newStatus);
        if (newStatus == RunStatusEnum.COMPLETED) {
            run.setCompletedAt(OffsetDateTime.now());
        }
        run.setUpdatedAt(OffsetDateTime.now());
        runRepository.save(run);
        return ResponseEntity.ok(Map.of("status", newStatus.name().toLowerCase()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<AdminContact> buildAdminContacts(NotificationConfig cfg) {
        if (cfg == null) return List.of();
        List<AdminContact> list = new java.util.ArrayList<>();
        if (cfg.getAdminName1() != null) {
            list.add(new AdminContact(cfg.getAdminName1(), cfg.getAdminPhone1(), cfg.getAdminWhatsapp1()));
        }
        if (cfg.getAdminName2() != null) {
            list.add(new AdminContact(cfg.getAdminName2(), cfg.getAdminPhone2(), cfg.getAdminWhatsapp2()));
        }
        return list;
    }
}
