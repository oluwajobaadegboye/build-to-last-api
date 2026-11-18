package org.programhub.scheduler;

import org.programhub.flight.Flight;
import org.programhub.flight.FlightRepository;
import org.programhub.flight.FlightService;
import org.programhub.infrastructure.AviationStackClient;
import org.programhub.notification.AirportConfig;
import org.programhub.notification.AirportConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.programhub.program.Program;
import org.programhub.program.ProgramDates;
import org.programhub.program.ProgramRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Duration;
import org.programhub.common.enums.Direction;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightPollingJob {

    private final FlightRepository flightRepository;
    private final AirportConfigRepository airportConfigRepository;
    private final FlightService flightService;
    private final AviationStackClient aviationStackClient;
    private final ProgramRepository programRepository;

    /** Only one server copy runs each round (absent in unit tests, which build the job directly). */
    @Autowired(required = false)
    private JobLock jobLock;

    @Value("${btl.polling.pre-arrival-hours:6}")
    private int preArrivalHours;

    @Value("${btl.polling.post-arrival-hours:2}")
    private int postArrivalHours;

    @Scheduled(fixedDelayString = "${btl.polling.interval-ms:1800000}")
    public void pollFlights() {
        if (jobLock != null && !jobLock.acquire("flight-polling", Duration.ofMinutes(20))) return;
        // Global settings now only supply platform defaults (delay thresholds). When flights are tracked
        // is per program: from the day before it starts to the day after it ends (ProgramDates.trackingWindow).
        AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);
        if (config == null) {
            log.warn("No airport_config found — skipping poll");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        // Deactivate any flights whose scheduled day has passed without a rescheduled date
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        flightService.deactivatePastDayFlights(startOfToday);

        List<Flight> candidates = flightRepository.findActiveFlightsInWindow(
            now.minusHours(postArrivalHours),
            now.plusHours(preArrivalHours)
        );

        // Keep flights whose program is still inside its tracking window; switch off the rest.
        Map<String, Program> programs = new HashMap<>();
        List<Flight> flights = new ArrayList<>();
        List<Flight> closed = new ArrayList<>();
        for (Flight f : candidates) {
            String pid = f.getParticipant().getProgramId();
            Program program = pid == null ? null
                : programs.computeIfAbsent(pid, id -> programRepository.findById(id).orElse(null));
            OffsetDateTime[] window = ProgramDates.trackingWindow(program);
            if (window != null && !now.isBefore(window[0]) && now.isBefore(window[1])) flights.add(f);
            else closed.add(f);
        }
        if (!closed.isEmpty()) flightService.stopTracking(closed);

        // Deduplicate: one API call per flight number + airport + direction, however many participants share it
        // (different programs can use different airports).
        Map<String, List<Flight>> byNumber = flights.stream()
            .collect(Collectors.groupingBy(f -> f.getFlightNumber() + "|" + f.getAirportCode() + "|" + f.getDirection()));

        log.info("Polling {} unique flight numbers ({} total rows in window)", byNumber.size(), flights.size());

        for (Map.Entry<String, List<Flight>> entry : byNumber.entrySet()) {
            List<Flight> group = entry.getValue();
            Flight first = group.get(0);
            String flightNumber = first.getFlightNumber();
            try {
                aviationStackClient.fetchFlight(flightNumber, first.getAirportCode(),
                        first.getDirection() != Direction.TO_AIRPORT)
                    .ifPresent(data -> group.forEach(f -> flightService.processFlightUpdate(f, data, config)));
            } catch (Exception e) {
                log.warn("Failed to poll flight {}: {}", flightNumber, e.getMessage());
            }
        }
    }
}
