package com.btl.transport.flight;

import com.btl.transport.common.Leg4CalculatorService;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.FlightStatusType;
import com.btl.transport.infrastructure.AviationStackClient;
import com.btl.transport.notification.AirportConfig;
import com.btl.transport.notification.NotificationService;
import com.btl.transport.participant.Participant;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightService {

    private final FlightRepository flightRepository;
    private final Leg4CalculatorService leg4Calculator;
    private final NotificationService notificationService;

    @Transactional
    public void processFlightUpdate(Flight flight, AviationStackClient.AviationStackFlightData data,
                                    AirportConfig config) {
        FlightStatusType previousStatus = flight.getFlightStatus();
        int previousDelayMins = flight.getDelayMins() != null ? flight.getDelayMins() : 0;

        FlightStatusType newStatus = parseStatus(data.flight_status());
        int newDelayMins = calculateDelayMins(data);

        flight.setFlightStatus(newStatus);
        flight.setDelayMins(newDelayMins);
        flight.setLastPolledAt(OffsetDateTime.now());

        if (data.arrival() != null) {
            String eta = data.arrival().estimated() != null
                ? data.arrival().estimated()
                : data.arrival().scheduled();
            if (eta != null) {
                try {
                    flight.setLiveEta(OffsetDateTime.parse(eta));
                } catch (Exception e) {
                    log.warn("Could not parse ETA '{}' for flight {}", eta, flight.getFlightNumber());
                }
            }
        }

        flightRepository.save(flight);

        Participant p = flight.getParticipant();
        if (newStatus == FlightStatusType.CANCELLED && previousStatus != FlightStatusType.CANCELLED) {
            p.setNeedsAttention(true);
            p.setAttentionReason("Flight " + flight.getFlightNumber() + " cancelled");
            notificationService.sendCancellationNotification(p, flight.getFlightNumber());
        } else if (newDelayMins > previousDelayMins) {
            double delayHrs = newDelayMins / 60.0;
            boolean major = delayHrs >= config.getMajorDelayThresholdHrs().doubleValue();
            notificationService.sendDelayNotification(p, flight.getFlightNumber(), newDelayMins, major);
        }

        log.debug("Updated flight {} status={} delayMins={}", flight.getFlightNumber(), newStatus, newDelayMins);
    }

    @Transactional
    public void closePollingWindow() {
        List<Flight> active = flightRepository.findByPollingActiveTrue();
        active.forEach(f -> f.setPollingActive(false));
        flightRepository.saveAll(active);
        log.info("Polling window closed — {} flights deactivated", active.size());
    }

    private FlightStatusType parseStatus(String status) {
        if (status == null) return FlightStatusType.UNKNOWN;
        return switch (status.toLowerCase()) {
            case "scheduled" -> FlightStatusType.SCHEDULED;
            case "active", "en-route" -> FlightStatusType.SCHEDULED;
            case "landed" -> FlightStatusType.LANDED;
            case "cancelled" -> FlightStatusType.CANCELLED;
            case "diverted" -> FlightStatusType.DIVERTED;
            case "delayed" -> FlightStatusType.DELAYED;
            default -> FlightStatusType.UNKNOWN;
        };
    }

    private int calculateDelayMins(AviationStackClient.AviationStackFlightData data) {
        if (data.arrival() == null) return 0;
        String scheduled = data.arrival().scheduled();
        String estimated = data.arrival().estimated();
        if (scheduled == null || estimated == null) return 0;
        try {
            OffsetDateTime sched = OffsetDateTime.parse(scheduled);
            OffsetDateTime est = OffsetDateTime.parse(estimated);
            long mins = java.time.Duration.between(sched, est).toMinutes();
            return (int) Math.max(0, mins);
        } catch (Exception e) {
            return 0;
        }
    }
}
