package com.btl.transport.participant;

import com.btl.transport.common.BtlCodeService;
import com.btl.transport.common.Leg4CalculatorService;
import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.FlightStatusType;
import com.btl.transport.common.enums.ParticipantStatus;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.hotel.Hotel;
import com.btl.transport.hotel.HotelRepository;
import com.btl.transport.notification.AirportConfig;
import com.btl.transport.notification.AirportConfigRepository;
import com.btl.transport.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final AirportConfigRepository airportConfigRepository;
    private final BtlCodeService btlCodeService;
    private final Leg4CalculatorService leg4Calculator;
    private final NotificationService notificationService;

//    @Value("${btl.frontend-base-url}")
//    private String frontendBaseUrl;

    @Transactional
    public Participant register(
        String fullName, String phone, String email,
        Integer hotelId, boolean shuttleOptIn,
        String arrivalAirline, String arrivalFlightNumber, OffsetDateTime arrivalDatetime,
        String departureAirline, String departureFlightNumber, OffsetDateTime departureDatetime
    ) {
        if (participantRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with this email address already exists");
        }

        String btlCode = btlCodeService.generateNextCode();

        Hotel hotel = hotelId != null
            ? hotelRepository.findById(hotelId).orElse(null)
            : null;

        Participant participant = Participant.builder()
            .btlCode(btlCode)
            .fullName(fullName)
            .email(email)
            .phone(phone)
            .status(ParticipantStatus.REGISTERED)
            .needsAttention(false)
            .shuttleOptIn(shuttleOptIn)
            .hotel(hotel)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        participant = participantRepository.save(participant);

        AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);

        if (arrivalFlightNumber != null && arrivalDatetime != null) {
            boolean polling = isWithinPollingWindow(config, arrivalDatetime.toLocalDate());
            Flight arrival = Flight.builder()
                .participant(participant)
                .airline(arrivalAirline)
                .flightNumber(arrivalFlightNumber)
                .direction(Direction.TO_HOTEL)
                .submittedDatetime(arrivalDatetime)
                .flightStatus(FlightStatusType.UNKNOWN)
                .pollingActive(polling)
                .airportCode("IND")
                .delayMins(0)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
            flightRepository.save(arrival);
        }

        if (departureFlightNumber != null && departureDatetime != null) {
            LocalTime departTime = departureDatetime.atZoneSameInstant(
                ZoneId.of("America/Indiana/Indianapolis")).toLocalTime();
            LocalTime defaultCutoff = config != null ? config.getLeg4DefaultCutoffAsLocalTime() : null;
            var leg4From = leg4Calculator.calculate(departTime, hotel, defaultCutoff);

            Flight departure = Flight.builder()
                .participant(participant)
                .airline(departureAirline)
                .flightNumber(departureFlightNumber)
                .direction(Direction.TO_AIRPORT)
                .submittedDatetime(departureDatetime)
                .flightStatus(FlightStatusType.UNKNOWN)
                .pollingActive(false)
                .leg4PickupFrom(leg4From)
                .airportCode("IND")
                .delayMins(0)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
            flightRepository.save(departure);
        }

        try {
            notificationService.sendRegistrationConfirmation(participant);
        } catch (Exception e) {
            log.warn("Notification failed for {} — registration still succeeded: {}", btlCode, e.getMessage());
        }

        return participant;
    }

    @Transactional
    public Flight updateFlight(String btlCode, String direction,
                               String airline, String flightNumber,
                               OffsetDateTime submittedDatetime) {
        Participant participant = participantRepository.findByBtlCode(btlCode)
            .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + btlCode));

        Direction dir = "departure".equalsIgnoreCase(direction)
            ? Direction.TO_AIRPORT : Direction.TO_HOTEL;

        Flight flight = flightRepository.findByParticipantAndDirection(participant, dir)
            .orElseGet(() -> Flight.builder()
                .participant(participant)
                .direction(dir)
                .airportCode("IND")
                .flightStatus(FlightStatusType.UNKNOWN)
                .delayMins(0)
                .createdAt(OffsetDateTime.now())
                .build());

        flight.setAirline(airline);
        flight.setFlightNumber(flightNumber);
        flight.setSubmittedDatetime(submittedDatetime);
        flight.setUpdatedAt(OffsetDateTime.now());

        if (dir == Direction.TO_HOTEL) {
            AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);
            boolean polling = isWithinPollingWindow(config, submittedDatetime.toLocalDate());
            flight.setPollingActive(polling);
        }

        if (dir == Direction.TO_AIRPORT) {
            AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);
            LocalTime departTime = submittedDatetime.atZoneSameInstant(
                ZoneId.of("America/Indiana/Indianapolis")).toLocalTime();
            LocalTime defaultCutoff = config != null ? config.getLeg4DefaultCutoffAsLocalTime() : null;
            flight.setLeg4PickupFrom(leg4Calculator.calculate(departTime, participant.getHotel(), defaultCutoff));
        }

        participant.setNeedsAttention(false);
        participant.setAttentionReason(null);
        participant.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(participant);

        return flightRepository.save(flight);
    }

    private boolean isWithinPollingWindow(AirportConfig config, LocalDate flightDate) {
        if (config == null) return false;
        LocalDate startDate = config.getPollingStartAsLocalDate();
        OffsetDateTime endDatetime = config.getPollingEndAsOffsetDateTime();
        if (startDate == null || endDatetime == null) return false;
        return !flightDate.isBefore(startDate) && OffsetDateTime.now().isBefore(endDatetime);
    }
}
