package com.btl.transport.scheduler;

import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.flight.FlightService;
import com.btl.transport.infrastructure.AviationStackClient;
import com.btl.transport.notification.AirportConfig;
import com.btl.transport.notification.AirportConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlightPollingJob {

    private final FlightRepository flightRepository;
    private final AirportConfigRepository airportConfigRepository;
    private final FlightService flightService;
    private final AviationStackClient aviationStackClient;

    @Scheduled(fixedDelayString = "${btl.polling.interval-ms:1800000}")
    public void pollFlights() {
        AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);
        if (config == null) {
            log.warn("No airport_config found — skipping poll");
            return;
        }

        // Guard: close polling window if past end datetime
        if (!isWithinPollingWindow(config)) {
            flightService.closePollingWindow();
            return;
        }

        List<Flight> flights = flightRepository.findByPollingActiveTrue();
        log.info("Polling {} active flights", flights.size());

        for (Flight flight : flights) {
            try {
                aviationStackClient.fetchFlight(flight.getFlightNumber())
                    .ifPresent(data -> flightService.processFlightUpdate(flight, data, config));
            } catch (Exception e) {
                log.warn("Failed to poll flight {}: {}", flight.getFlightNumber(), e.getMessage());
                // Graceful degradation — skip this flight, retry next cycle
            }
        }
    }

    private boolean isWithinPollingWindow(AirportConfig config) {
        OffsetDateTime end = config.getPollingEndAsOffsetDateTime();
        if (end == null) return false;
        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("America/Indiana/Indianapolis"));
        return now.isBefore(end);
    }
}
