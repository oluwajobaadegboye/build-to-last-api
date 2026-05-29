package com.btl.transport.driver;

import com.btl.transport.participant.Participant;
import com.btl.transport.participant.ParticipantRepository;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunParticipant;
import com.btl.transport.run.RunParticipantRepository;
import com.btl.transport.run.RunRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Driver", description = "Public driver-facing endpoints authenticated by personal token link")
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
public class DriverController {

    private final DriverRepository driverRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final ParticipantRepository participantRepository;

    record PassengerSummary(
        @JsonProperty("btl_code")  String btlCode,
        @JsonProperty("full_name") String fullName,
        Boolean boarded
    ) {}

    record RunSummary(
        @JsonProperty("run_id")           String runId,
        String direction,
        @JsonProperty("run_type")         String runType,
        @JsonProperty("conference_day")   String conferenceDay,
        @JsonProperty("conference_date")  String conferenceDate,
        @JsonProperty("depart_time")      String departTime,
        @JsonProperty("pickup_location")  String pickupLocation,
        @JsonProperty("dropoff_location") String dropoffLocation,
        @JsonProperty("seats_total")      int seatsTotal,
        @JsonProperty("seats_filled")     int seatsFilled,
        String status,
        List<PassengerSummary> passengers
    ) {}

    record DriverMeResponse(
        @JsonProperty("driver_id")  String driverId,
        String name,
        String phone,
        @JsonProperty("program_id") String programId,
        List<RunSummary> runs
    ) {}

    @Operation(summary = "Get driver schedule", description = "Returns driver identity and all assigned runs with passenger lists. Authenticated by personal login token from email link.")
    @GetMapping("/me")
    public ResponseEntity<DriverMeResponse> me(@RequestParam String token) {
        Driver driver = driverRepository.findByLoginToken(token)
            .orElseThrow(() -> new EntityNotFoundException("Invalid or expired driver link."));

        List<Run> runs = runRepository.findByDriverIdOrderByConferenceDateAscDepartTimeAsc(driver.getId());

        List<RunSummary> runSummaries = runs.stream().map(run -> {
            List<RunParticipant> rps = runParticipantRepository.findByIdRunId(run.getId());
            List<Integer> pids = rps.stream().map(rp -> rp.getId().getParticipantId()).toList();
            List<PassengerSummary> passengers = pids.isEmpty()
                ? List.of()
                : participantRepository.findAllById(pids).stream()
                    .map(p -> {
                        RunParticipant rp = rps.stream()
                            .filter(r -> r.getId().getParticipantId().equals(p.getId()))
                            .findFirst().orElse(null);
                        return new PassengerSummary(p.getBtlCode(), p.getFullName(),
                            rp != null ? Boolean.TRUE.equals(rp.getBoarded()) : false);
                    })
                    .toList();
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
                passengers
            );
        }).toList();

        return ResponseEntity.ok(new DriverMeResponse(
            String.valueOf(driver.getId()),
            driver.getName(),
            driver.getPhone(),
            driver.getProgramId(),
            runSummaries
        ));
    }
}
