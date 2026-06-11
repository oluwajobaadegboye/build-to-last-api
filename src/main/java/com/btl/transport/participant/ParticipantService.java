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
import com.btl.transport.notification.SheetsWebhookService;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.room.RoomOccupantRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
    private final SheetsWebhookService sheetsWebhookService;
    private final ProgramRepository programRepository;
    private final RoomOccupantRepository roomOccupantRepository;

//    @Value("${btl.frontend-base-url}")
//    private String frontendBaseUrl;

    @Transactional
    public Participant register(
        String fullName, String phone, String email,
        Integer hotelId, boolean shuttleOptIn,
        String arrivalAirline, String arrivalFlightNumber, LocalDateTime arrivalDatetime,
        String departureAirline, String departureFlightNumber, LocalDateTime departureDatetime,
        String programId, String state
    ) {
        Program program = programId != null ? programRepository.findById(programId).orElse(null) : null;
        if (program != null && Boolean.FALSE.equals(program.getRegistrationOpen())) {
            throw new RegistrationClosedException("Registration for this program is currently paused.");
        }

        Hotel hotel = hotelId != null
            ? hotelRepository.findById(hotelId).orElse(null)
            : null;

        Participant participant;

        if (programId != null) {
            Participant stub = participantRepository
                .findByEmailIgnoreCaseAndProgramId(email, programId).orElse(null);
            if (stub != null) {
                if (!flightRepository.findByParticipant(stub).isEmpty()) {
                    throw new AlreadyRegisteredException("You have already registered for this program.");
                }
                // Stub was created by CSV room import — complete the registration in-place
                stub.setFullName(fullName);
                stub.setPhone(phone);
                stub.setShuttleOptIn(shuttleOptIn);
                stub.setHotel(hotel);
                stub.setState(state);
                stub.setStatus(ParticipantStatus.REGISTERED);
                stub.setNeedsAttention(false);
                stub.setUpdatedAt(OffsetDateTime.now());
                participant = participantRepository.save(stub);
            } else {
                String ini = program != null ? program.getIni() : null;
                String btlCode = btlCodeService.generateNextCode(programId, ini);
                participant = participantRepository.save(Participant.builder()
                    .btlCode(btlCode)
                    .fullName(fullName)
                    .email(email)
                    .phone(phone)
                    .status(ParticipantStatus.REGISTERED)
                    .needsAttention(false)
                    .shuttleOptIn(shuttleOptIn)
                    .hotel(hotel)
                    .programId(programId)
                    .state(state)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build());
                // Link to any existing room occupant imported before this participant registered
                final Participant saved = participant;
                roomOccupantRepository
                    .findByProgramIdAndEmailIgnoreCase(programId, email)
                    .ifPresent(occ -> {
                        if (occ.getParticipant() == null) {
                            occ.setParticipant(saved);
                            roomOccupantRepository.save(occ);
                        }
                    });
            }
        } else {
            // Legacy global check (no program scope)
            if (participantRepository.existsByEmailIgnoreCase(email)) {
                throw new IllegalArgumentException("An account with this email address already exists");
            }
            String ini = program != null ? program.getIni() : null;
            String btlCode = btlCodeService.generateNextCode("default", ini);
            participant = participantRepository.save(Participant.builder()
                .btlCode(btlCode)
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .status(ParticipantStatus.REGISTERED)
                .needsAttention(false)
                .shuttleOptIn(shuttleOptIn)
                .hotel(hotel)
                .programId(null)
                .state(state)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());
        }

        AirportConfig config = airportConfigRepository.findByConfigKey("main").orElse(null);

        if (arrivalFlightNumber != null && arrivalDatetime != null) {
            OffsetDateTime arrivalOdt = arrivalDatetime.atOffset(ZoneOffset.UTC);
            boolean polling = isWithinPollingWindow(config, arrivalDatetime.toLocalDate());
            Flight arrival = Flight.builder()
                .participant(participant)
                .airline(arrivalAirline)
                .flightNumber(arrivalFlightNumber)
                .direction(Direction.TO_HOTEL)
                .submittedDatetime(arrivalOdt)
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
            LocalTime departTime = departureDatetime.toLocalTime();
            LocalTime defaultCutoff = config != null ? config.getLeg4DefaultCutoffAsLocalTime() : null;
            var leg4From = leg4Calculator.calculate(departTime, hotel, defaultCutoff);

            OffsetDateTime departureOdt = departureDatetime.atOffset(ZoneOffset.UTC);
            Flight departure = Flight.builder()
                .participant(participant)
                .airline(departureAirline)
                .flightNumber(departureFlightNumber)
                .direction(Direction.TO_AIRPORT)
                .submittedDatetime(departureOdt)
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
            log.warn("Notification failed for {} — registration still succeeded: {}", participant.getBtlCode(), e.getMessage());
        }

        sheetsWebhookService.appendRegistration(
            participant,
            arrivalAirline, arrivalFlightNumber, arrivalDatetime,
            departureAirline, departureFlightNumber, departureDatetime,
            program != null ? program.getName() : null
        );

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
            Program prog = participant.getProgramId() != null
                ? programRepository.findById(participant.getProgramId()).orElse(null) : null;
            LocalTime departTime = submittedDatetime.atZoneSameInstant(programZone(prog)).toLocalTime();
            LocalTime defaultCutoff = config != null ? config.getLeg4DefaultCutoffAsLocalTime() : null;
            flight.setLeg4PickupFrom(leg4Calculator.calculate(departTime, participant.getHotel(), defaultCutoff));
        }

        participant.setNeedsAttention(false);
        participant.setAttentionReason(null);
        participant.setUpdatedAt(OffsetDateTime.now());
        participantRepository.save(participant);

        return flightRepository.save(flight);
    }

    private ZoneId programZone(Program program) {
        if (program != null && program.getTimezone() != null && !program.getTimezone().isBlank()) {
            try { return ZoneId.of(program.getTimezone()); } catch (Exception ignored) {}
        }
        return ZoneId.of("America/New_York");
    }

    private boolean isWithinPollingWindow(AirportConfig config, LocalDate flightDate) {
        if (config == null) return false;
        LocalDate startDate = config.getPollingStartAsLocalDate();
        OffsetDateTime endDatetime = config.getPollingEndAsOffsetDateTime();
        if (startDate == null || endDatetime == null) return false;
        return !flightDate.isBefore(startDate) && OffsetDateTime.now().isBefore(endDatetime);
    }
}
