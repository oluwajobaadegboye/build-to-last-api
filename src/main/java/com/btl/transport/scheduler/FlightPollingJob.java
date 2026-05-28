package com.btl.transport.scheduler;

import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.flight.FlightService;
import com.btl.transport.infrastructure.AviationStackClient;
import com.btl.transport.notification.AirportConfig;
import com.btl.transport.notification.AirportConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightPollingJob {

    private final FlightRepository flightRepository;
    private final AirportConfigRepository airportConfigRepository;
    private final FlightService flightService;
    private final AviationStackClient aviationStackClient;

    @Value("${btl.polling.pre-arrival-hours:6}")
    private int preArrivalHours;

    @Value("${btl.polling.post-arrival-hours:2}")
    private int postArrivalHours;

    @Scheduled(fixedDelayString = "${btl.polling.interval-ms:1800000}")
    public void pollFlights() {
        AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);
        if (config == null) {
            log.warn("No airport_config found — skipping poll");
            return;
        }

        if (!isWithinPollingWindow(config)) {
            flightService.closePollingWindow();
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();

        // Deactivate any flights whose scheduled day has passed without a rescheduled date
        OffsetDateTime startOfToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        flightService.deactivatePastDayFlights(startOfToday);

        List<Flight> flights = flightRepository.findActiveFlightsInWindow(
            now.minusHours(postArrivalHours),
            now.plusHours(preArrivalHours)
        );

        // Deduplicate: one API call per unique flight number regardless of how many participants share it
        Map<String, List<Flight>> byNumber = flights.stream()
            .collect(Collectors.groupingBy(Flight::getFlightNumber));

        log.info("Polling {} unique flight numbers ({} total rows in window)", byNumber.size(), flights.size());

        for (Map.Entry<String, List<Flight>> entry : byNumber.entrySet()) {
            String flightNumber = entry.getKey();
            List<Flight> group = entry.getValue();
            try {
                aviationStackClient.fetchFlight(flightNumber)
                    .ifPresent(data -> group.forEach(f -> flightService.processFlightUpdate(f, data, config)));
            } catch (Exception e) {
                log.warn("Failed to poll flight {}: {}", flightNumber, e.getMessage());
            }
        }
    }

    private boolean isWithinPollingWindow(AirportConfig config) {
        OffsetDateTime end = config.getPollingEndAsOffsetDateTime();
        if (end == null) return false;
        OffsetDateTime now = OffsetDateTime.now();
        return now.isBefore(end);
    }
}
