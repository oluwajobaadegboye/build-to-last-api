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

import java.time.Duration;
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

        // Detect reschedule: AviationStack's scheduled time has shifted >60 min from what was submitted
        OffsetDateTime oldScheduled = flight.getSubmittedDatetime();
        OffsetDateTime newScheduled = null;
        boolean isRescheduled = false;
        if (data.arrival() != null && data.arrival().scheduled() != null) {
            try {
                newScheduled = OffsetDateTime.parse(data.arrival().scheduled());
                long diffMins = Math.abs(Duration.between(newScheduled, oldScheduled).toMinutes());
                if (diffMins > 60) {
                    flight.setSubmittedDatetime(newScheduled);
                    isRescheduled = true;
                    log.info("Flight {} rescheduled: {} → {}", flight.getFlightNumber(), oldScheduled, newScheduled);
                }
            } catch (Exception e) {
                log.warn("Could not parse scheduled time for reschedule check on {}", flight.getFlightNumber());
            }
        }

        flight.setFlightStatus(newStatus);
        flight.setDelayMins(newDelayMins);
        flight.setLastPolledAt(OffsetDateTime.now());

        if (isRescheduled) {
            // Keep polling active so the rescheduled flight stays in the window on its new date
            flight.setPollingActive(true);
        } else if (newStatus == FlightStatusType.ACTIVE
                || newStatus == FlightStatusType.LANDED
                || newStatus == FlightStatusType.CANCELLED
                || newStatus == FlightStatusType.DIVERTED) {
            flight.setPollingActive(false);
        }

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
        if (isRescheduled) {
            p.setNeedsAttention(true);
            p.setAttentionReason("Flight " + flight.getFlightNumber() + " rescheduled to " + newScheduled);
            notificationService.sendRescheduleNotification(p, flight.getFlightNumber(), oldScheduled, newScheduled);
        } else if (newStatus == FlightStatusType.CANCELLED && previousStatus != FlightStatusType.CANCELLED) {
            p.setNeedsAttention(true);
            p.setAttentionReason("Flight " + flight.getFlightNumber() + " cancelled");
            notificationService.sendCancellationNotification(p, flight.getFlightNumber());
        } else if (newDelayMins > previousDelayMins) {
            double delayHrs = newDelayMins / 60.0;
            boolean major = delayHrs >= config.getMajorDelayThresholdHrs().doubleValue();
            notificationService.sendDelayNotification(p, flight.getFlightNumber(), newDelayMins, major);
        }

        log.debug("Updated flight {} status={} delayMins={} rescheduled={}",
            flight.getFlightNumber(), newStatus, newDelayMins, isRescheduled);
    }

    @Transactional
    public void closePollingWindow() {
        List<Flight> active = flightRepository.findByPollingActiveTrue();
        active.forEach(f -> f.setPollingActive(false));
        flightRepository.saveAll(active);
        log.info("Polling window closed — {} flights deactivated", active.size());
    }

    @Transactional
    public void deactivatePastDayFlights(OffsetDateTime startOfToday) {
        List<Flight> stale = flightRepository.findStaleActiveFlights(startOfToday);
        if (!stale.isEmpty()) {
            stale.forEach(f -> f.setPollingActive(false));
            flightRepository.saveAll(stale);
            log.info("Deactivated {} flights from previous days", stale.size());
        }
    }

    private FlightStatusType parseStatus(String status) {
        if (status == null) return FlightStatusType.UNKNOWN;
        return switch (status.toLowerCase()) {
            case "scheduled" -> FlightStatusType.SCHEDULED;
            case "active", "en-route" -> FlightStatusType.ACTIVE;
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
            long mins = Duration.between(sched, est).toMinutes();
            return (int) Math.max(0, mins);
        } catch (Exception e) {
            return 0;
        }
    }
}
