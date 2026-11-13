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
import com.btl.transport.program.ProgramDates;
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
        return register(fullName, phone, email, hotelId, shuttleOptIn,
            arrivalAirline, arrivalFlightNumber, arrivalDatetime,
            departureAirline, departureFlightNumber, departureDatetime,
            programId, state, null);
    }

    /**
     * Throws when the program isn't taking registrations (paused, past its closing time, or full).
     * People already on the list (e.g. imported for rooms) can still complete their registration when it's full.
     */
    public void assertRegistrationOpen(Program program, String email) {
        if (program == null) return;
        String regState = com.btl.transport.program.RegistrationRules.state(
            program, participantRepository.countByProgramId(program.getId()), OffsetDateTime.now());
        if (regState.equals(com.btl.transport.program.RegistrationRules.OPEN)) return;
        boolean alreadyListed = email != null && participantRepository.findByEmailIgnoreCaseAndProgramId(email, program.getId()).isPresent();
        if (regState.equals(com.btl.transport.program.RegistrationRules.FULL) && alreadyListed) return;
        throw new RegistrationClosedException(com.btl.transport.program.RegistrationRules.message(regState));
    }

    @Transactional
    public Participant register(
        String fullName, String phone, String email,
        Integer hotelId, boolean shuttleOptIn,
        String arrivalAirline, String arrivalFlightNumber, LocalDateTime arrivalDatetime,
        String departureAirline, String departureFlightNumber, LocalDateTime departureDatetime,
        String programId, String state, String customAnswersJson
    ) {
        return register(fullName, null, null, null, phone, email, hotelId, shuttleOptIn,
            arrivalAirline, arrivalFlightNumber, arrivalDatetime,
            departureAirline, departureFlightNumber, departureDatetime,
            programId, state, customAnswersJson);
    }

    /**
     * @param fullName          "First Last" (callers build it from first/last when they have them)
     * @param firstName         null for older clients that only send a full name
     * @param gender            already-validated "male" / "female", or null
     * @param customAnswersJson already-validated answers JSON, or null to leave answers untouched
     */
    @Transactional
    public Participant register(
        String fullName, String firstName, String lastName, String gender,
        String phone, String email,
        Integer hotelId, boolean shuttleOptIn,
        String arrivalAirline, String arrivalFlightNumber, LocalDateTime arrivalDatetime,
        String departureAirline, String departureFlightNumber, LocalDateTime departureDatetime,
        String programId, String state, String customAnswersJson
    ) {
        Program program = programId != null ? programRepository.findById(programId).orElse(null) : null;
        // With a capacity, lock the program row so simultaneous sign-ups are counted one at a time.
        if (program != null && program.getMaxRegistrations() != null) programRepository.findByIdForUpdate(program.getId());
        assertRegistrationOpen(program, email);
        // No flights are recorded while the program doesn't offer airport pickups, whatever the client sends.
        // (Shuttle opt-in is left as sent: turning the shuttle off only hides it, so attendees who
        // register meanwhile must still be opted in if it is turned back on.)
        if (program != null && Boolean.FALSE.equals(program.getAirportPickupsEnabled())) {
            arrivalFlightNumber = null;   arrivalDatetime = null;
            departureFlightNumber = null; departureDatetime = null;
        }

        Hotel hotel = hotelId != null
            ? hotelRepository.findById(hotelId).orElse(null)
            : null;

        Participant participant;

        if (programId != null) {
            int numGroups = program != null && program.getBreakoutNumGroups() != null ? program.getBreakoutNumGroups() : 0;
            Participant stub = participantRepository
                .findByEmailIgnoreCaseAndProgramId(email, programId).orElse(null);
            if (stub != null) {
                if (!flightRepository.findByParticipant(stub).isEmpty()) {
                    throw new AlreadyRegisteredException("You have already registered for this program.");
                }
                // Stub was created by CSV room import — complete the registration in-place
                stub.setFullName(fullName);
                stub.setFirstName(firstName);
                stub.setLastName(lastName);
                stub.setGender(gender);
                stub.setPhone(phone);
                stub.setShuttleOptIn(shuttleOptIn);
                stub.setHotel(hotel);
                stub.setState(state);
                if (customAnswersJson != null) stub.setCustomAnswers(customAnswersJson);
                stub.setStatus(ParticipantStatus.REGISTERED);
                stub.setNeedsAttention(false);
                stub.setUpdatedAt(OffsetDateTime.now());
                if (stub.getBreakoutGroup() == null && numGroups > 0) {
                    stub.setBreakoutGroup(assignNextBreakoutGroup(programId, numGroups));
                }
                participant = participantRepository.save(stub);
            } else {
                String ini = program != null ? program.getIni() : null;
                String btlCode = btlCodeService.generateNextCode(programId, ini);
                participant = participantRepository.save(Participant.builder()
                    .btlCode(btlCode)
                    .fullName(fullName)
                    .firstName(firstName)
                    .lastName(lastName)
                    .gender(gender)
                    .email(email)
                    .phone(phone)
                    .status(ParticipantStatus.REGISTERED)
                    .needsAttention(false)
                    .shuttleOptIn(shuttleOptIn)
                    .hotel(hotel)
                    .programId(programId)
                    .state(state)
                    .customAnswers(customAnswersJson)
                    .breakoutGroup(numGroups > 0 ? assignNextBreakoutGroup(programId, numGroups) : null)
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

        if (arrivalFlightNumber != null && arrivalDatetime != null) {
            OffsetDateTime arrivalOdt = arrivalDatetime.atOffset(ZoneOffset.UTC);
            boolean polling = ProgramDates.isTrackable(program, arrivalDatetime.toLocalDate(), OffsetDateTime.now());
            Flight arrival = Flight.builder()
                .participant(participant)
                .airline(arrivalAirline)
                .flightNumber(arrivalFlightNumber)
                .direction(Direction.TO_HOTEL)
                .submittedDatetime(arrivalOdt)
                .flightStatus(FlightStatusType.UNKNOWN)
                .pollingActive(polling)
                .airportCode(ProgramDates.airportCode(program))
                .delayMins(0)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
            flightRepository.save(arrival);
        }

        if (departureFlightNumber != null && departureDatetime != null) {
            LocalTime departTime = departureDatetime.toLocalTime();
            // Only programs using the "leave from the venue" rule classify departures.
            LocalTime venueCutoff = ProgramDates.venueDepartureCutoff(program);
            var leg4From = venueCutoff == null ? null : leg4Calculator.calculate(departTime, hotel, venueCutoff);

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
                .airportCode(ProgramDates.airportCode(program))
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
                .airportCode(ProgramDates.airportCode(participant.getProgramId() != null ? programRepository.findById(participant.getProgramId()).orElse(null) : null))
                .flightStatus(FlightStatusType.UNKNOWN)
                .delayMins(0)
                .createdAt(OffsetDateTime.now())
                .build());

        flight.setAirline(airline);
        flight.setFlightNumber(flightNumber);
        flight.setSubmittedDatetime(submittedDatetime);
        flight.setUpdatedAt(OffsetDateTime.now());

        Program prog = participant.getProgramId() != null
            ? programRepository.findById(participant.getProgramId()).orElse(null) : null;

        if (dir == Direction.TO_HOTEL) {
            flight.setPollingActive(ProgramDates.isTrackable(prog, submittedDatetime.toLocalDate(), OffsetDateTime.now()));
        }

        if (dir == Direction.TO_AIRPORT) {
            LocalTime departTime = submittedDatetime.atZoneSameInstant(programZone(prog)).toLocalTime();
            LocalTime venueCutoff = ProgramDates.venueDepartureCutoff(prog);
            flight.setLeg4PickupFrom(venueCutoff == null ? null
                : leg4Calculator.calculate(departTime, participant.getHotel(), venueCutoff));
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

    private int assignNextBreakoutGroup(String programId, int numGroups) {
        int minCount = Integer.MAX_VALUE;
        int minGroup = 1;
        for (int g = 1; g <= numGroups; g++) {
            long count = participantRepository.countByBreakoutGroupAndProgramId(g, programId);
            if (count < minCount) {
                minCount = (int) count;
                minGroup = g;
            }
        }
        return minGroup;
    }
}
