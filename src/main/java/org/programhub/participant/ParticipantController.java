package org.programhub.participant;

import org.programhub.common.enums.Direction;
import org.programhub.common.enums.RunType;
import org.programhub.flight.Flight;
import org.programhub.flight.FlightRepository;
import org.programhub.hotel.Hotel;
import org.programhub.hotel.HotelRepository;
import org.programhub.notification.NotificationConfig;
import org.programhub.notification.NotificationConfigRepository;
import org.programhub.room.AccommodationContact;
import org.programhub.room.AccommodationContactRepository;
import org.programhub.room.RoomAssignment;
import org.programhub.room.RoomOccupant;
import org.programhub.room.RoomOccupantRepository;
import org.programhub.notification.NotificationService;
import org.programhub.notification.SendGridService;
import org.programhub.program.Program;
import org.programhub.program.ProgramRepository;
import org.programhub.run.Run;
import org.programhub.run.RunParticipantRepository;
import org.programhub.run.RunRepository;
import org.programhub.run.RunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.programhub.infrastructure.StorageService;
import static org.programhub.participant.ParticipantDtos.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.programhub.admin.AdminAccess;
import org.programhub.admin.AuditLog;
import org.programhub.admin.AuditLogRepository;
import org.programhub.announcement.AnnouncementService;
import org.programhub.payment.PaymentService;
import org.programhub.program.FeeRules;
import org.programhub.program.ProgramDates;
import org.programhub.program.ProgramPaymentQrRepository;
import org.programhub.program.RegistrationRules;
import org.programhub.program.RoomVisibility;
import org.programhub.registration.RegistrationFieldService;
import org.programhub.registration.RegistrationSectionController;
import org.programhub.registration.RegistrationSectionRepository;
import org.programhub.room.ChaperoneService;
import org.programhub.souvenir.SouvenirService;
import org.springframework.http.CacheControl;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Participant", description = "Public-facing participant registration, status, and flight update endpoints")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController {

    @Value("${btl.uploads.dir:./uploads}")
    private String uploadsDir;

    private final StorageService storageService;
    private final SouvenirService souvenirService;
    private final PaymentService paymentService;
    private final ParticipantService participantService;
    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final RunService runService;
    private final NotificationConfigRepository notificationConfigRepository;
    private final ProgramRepository programRepository;
    private final SendGridService sendGridService;
    private final NotificationService notificationService;
    private final RoomOccupantRepository roomOccupantRepository;
    private final AccommodationContactRepository accommodationContactRepository;
    private final RegistrationFieldService registrationFieldService;
    private final RegistrationSectionRepository registrationSectionRepository;
    private final ChaperoneService chaperoneService;
    private final AuditLogRepository auditLogRepository;
    private final ProgramPaymentQrRepository paymentQrRepository;
    private final AdminAccess adminAccess;
    private final AnnouncementService announcementService;

    // ── GET /api/v1/health ─────────────────────────────────────────────────
    @Operation(summary = "Health check", description = "Returns the current API service health status")
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "UP", OffsetDateTime.now().toString()));
    }

    public record ResendCodeRequest(
        String email,
        @JsonProperty("program_id") String programId
    ) {}

    // ── GET /api/v1/hotels ─────────────────────────────────────────────────
    @Operation(summary = "List hotels", description = "Returns all hotels ordered by shuttle stop sequence")
    @GetMapping("/hotels")
    public ResponseEntity<List<HotelResponse>> getHotels(
            @RequestParam(name = "program_id", required = false) String programId) {
        List<Hotel> hotels = programId != null
            ? hotelRepository.findByProgramIdOrderByShuttleStopOrderAsc(programId)
            : hotelRepository.findAllByOrderByShuttleStopOrderAsc();
        return ResponseEntity.ok(
            hotels.stream()
                .map(h -> new HotelResponse(h.getId(), h.getHotelName(), h.getPickupAddress(), h.getShuttleStopOrder()))
                .toList()
        );
    }

    // ── GET /api/v1/programs/{programId} ─────────────────────────────────
    @Operation(summary = "Get program details", description = "Returns public program information and its associated hotel list")
    @GetMapping("/programs/{programId}")
    public ResponseEntity<Map<String, Object>> getProgramPublic(@PathVariable String programId) {
        // Accept either the program id or its short code (ini), so shareable links like /p/BL work.
        Program program = programRepository.findById(programId)
            .or(() -> programRepository.findFirstByIniIgnoreCase(programId))
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + programId));
        List<Hotel> hotels = hotelRepository.findByProgramIdOrderByShuttleStopOrderAsc(program.getId());
        List<Map<String, Object>> hotelList = hotels.stream()
            .map(h -> Map.<String, Object>of(
                "hotel_id", h.getId(),
                "hotel_name", h.getHotelName() != null ? h.getHotelName() : "",
                "pickup_address", h.getPickupAddress() != null ? h.getPickupAddress() : ""
            ))
            .toList();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", program.getId());
        resp.put("name", program.getName());
        resp.put("ini", program.getIni() != null ? program.getIni() : "");
        resp.put("registration_fields", registrationFieldService.activeFieldDtos(program.getId()));
        // Extra question steps after the first ("questions_title"); souvenir_step says where souvenir questions go.
        resp.put("registration_sections", registrationSectionRepository.findByProgramIdOrderBySortOrderAscIdAsc(program.getId())
            .stream().map(RegistrationSectionController::toDto).toList());
        resp.put("souvenir_step", program.getSouvenirStep());
        resp.put("souvenirs", souvenirService.registrationItems(program.getId()));
        resp.put("daily_shuttle_enabled", !Boolean.FALSE.equals(program.getDailyShuttleEnabled()));
        resp.put("airport_pickups_enabled", !Boolean.FALSE.equals(program.getAirportPickupsEnabled()));
        resp.put("lodging_type", Program.normalizeLodgingType(program.getLodgingType()));
        resp.put("venue", program.getVenue() != null ? program.getVenue() : "");
        // "Leave from the venue" departure rule: null cutoff when the program doesn't use it.
        var venueCutoff = ProgramDates.venueDepartureCutoff(program);
        resp.put("venue_departure_cutoff", venueCutoff != null ? venueCutoff.toString() : null);
        resp.put("phase", program.getPhase() != null ? program.getPhase() : "setup");
        resp.put("city", program.getCity() != null ? program.getCity() : "");
        resp.put("state", program.getState() != null ? program.getState() : "");
        resp.put("logo_url", program.getLogoUrl() != null ? storageService.presign(program.getLogoUrl()) : "");
        resp.put("banner_url", program.getBannerUrl() != null ? storageService.presign(program.getBannerUrl()) : "");
        resp.put("timezone", program.getTimezone() != null ? program.getTimezone() : "America/New_York");
        resp.put("start_date", program.getStartDate() != null ? program.getStartDate() : "");
        resp.put("end_date", program.getEndDate() != null ? program.getEndDate() : "");
        resp.put("hotel_selection_enabled", program.getHotelSelectionEnabled() != null ? program.getHotelSelectionEnabled() : true);
        resp.put("registration_open", program.getRegistrationOpen() != null ? program.getRegistrationOpen() : true);
        // open | paused | closed (past its closing time) | full (capacity reached)
        String regState = RegistrationRules.state(
            program, participantRepository.countByProgramIdAndWaitlistedAtIsNull(program.getId()), OffsetDateTime.now());
        resp.put("registration_state", regState);
        // Full, but new sign-ups can join the waitlist.
        resp.put("waitlist_open", RegistrationRules.FULL.equals(regState) && Boolean.TRUE.equals(program.getWaitlistEnabled()));
        resp.put("registration_closes_at", program.getRegistrationClosesAt());
        resp.put("reg_title",       program.getRegTitle());
        resp.put("reg_description", program.getRegDescription());
        resp.put("page_theme",      program.getPageTheme());
        resp.put("landing_style",   program.getLandingStyle());
        resp.put("questions_title", program.getQuestionsTitle());
        resp.put("ask_gender",      !Boolean.FALSE.equals(program.getAskGender()));
        resp.put("ask_phone",       !Boolean.FALSE.equals(program.getAskPhone()));
        // The fee for the registration page: only when the program shows it and has a fee; else null.
        Map<String, Object> landingFee = null;
        boolean feesByAnswer = FeeRules.byAnswer(program);
        // When fees differ by answer there's no single fee to show up front; the form shows theirs.
        if (Boolean.TRUE.equals(program.getShowFeeOnLanding()) && !feesByAnswer
                && program.getRegistrationFee() != null && program.getRegistrationFee().signum() > 0) {
            landingFee = new LinkedHashMap<>();
            landingFee.put("amount", program.getRegistrationFee());
            landingFee.put("currency", program.getFeeCurrency() != null ? program.getFeeCurrency() : "USD");
            landingFee.put("label", program.getFeeLabel() != null && !program.getFeeLabel().isBlank() ? program.getFeeLabel() : "Registration fee");
        }
        resp.put("landing_fee", landingFee);
        // Fee details for the form ("Your registration fee: ...") and the success screen's "How to pay".
        Map<String, Object> fees = new LinkedHashMap<>();
        fees.put("base", program.getRegistrationFee() != null && program.getRegistrationFee().signum() > 0 ? program.getRegistrationFee() : null);
        fees.put("currency", program.getFeeCurrency() != null ? program.getFeeCurrency() : "USD");
        fees.put("label", program.getFeeLabel() != null && !program.getFeeLabel().isBlank() ? program.getFeeLabel() : "Registration fee");
        fees.put("question_key", feesByAnswer ? program.getFeeQuestionKey() : null);
        fees.put("by_option", feesByAnswer ? FeeRules.optionFees(program) : Map.of());
        resp.put("fees", fees);
        resp.put("payment_instructions", program.getPaymentInstructions());
        resp.put("has_payment_qr", paymentQrRepository.existsById(program.getId()));
        resp.put("hotels", hotelList);
        return ResponseEntity.ok(resp);
    }

    // ── GET /api/v1/programs/{id}/payment-qr ──────────────────────────────
    @Operation(summary = "Payment QR code", description = "The program's payment QR image (for the registration and status pages)")
    @GetMapping("/programs/{id}/payment-qr")
    public ResponseEntity<byte[]> paymentQr(@PathVariable String id) {
        return paymentQrRepository.findById(id)
            .map(q -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(q.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
                .eTag("\"" + q.getUpdatedAt().toEpochSecond() + "\"")
                .body(q.getData()))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/v1/coordinator-contacts ──────────────────────────────────
    @Operation(summary = "Get coordinator contacts", description = "Returns coordinator phone and WhatsApp contact details, optionally scoped to a program")
    @GetMapping("/coordinator-contacts")
    public ResponseEntity<CoordinatorContactsResponse> coordinatorContacts(
            @RequestParam(name = "program_id", required = false) String programId) {
        // Only the program's own contacts: never another program's (the global default was BTL's).
        NotificationConfig cfg = programId != null
            ? notificationConfigRepository.findByProgramId(programId).orElse(null)
            : null;
        if (cfg == null) return ResponseEntity.ok(new CoordinatorContactsResponse(null, null));

        return ResponseEntity.ok(new CoordinatorContactsResponse(
            toCoordinatorDto(cfg.getAdminName1(), cfg.getAdminPhone1(), cfg.getAdminWhatsapp1()),
            toCoordinatorDto(cfg.getAdminName2(), cfg.getAdminPhone2(), cfg.getAdminWhatsapp2())
        ));
    }

    // ── GET /api/v1/accommodation-contacts ───────────────────────────────
    @Operation(summary = "Get accommodation contacts", description = "Returns accommodation support contacts from the accommodation_contacts table, scoped to a program")
    @GetMapping("/accommodation-contacts")
    public ResponseEntity<List<CoordinatorDto>> accommodationContacts(
            @RequestParam(name = "program_id", required = false) String programId) {
        if (programId == null) return ResponseEntity.ok(List.of());
        List<AccommodationContact> contacts =
            accommodationContactRepository.findByProgramIdOrderBySortOrderAsc(programId);
        List<CoordinatorDto> result = contacts.stream()
            .map(c -> toCoordinatorDto(c.getName(), c.getPhone(), c.getWhatsapp()))
            .toList();
        return ResponseEntity.ok(result);
    }

    // ── POST /api/v1/resend-code ──────────────────────────────────────────
    @Operation(summary = "Resend registration code", description = "Re-sends the participant's registration code to their registered email address")
    @PostMapping("/resend-code")
    public ResponseEntity<Map<String, Boolean>> resendCode(@RequestBody ResendCodeRequest req) {
        if (req.email() == null || req.programId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        participantRepository.findByEmailIgnoreCaseAndProgramId(req.email(), req.programId())
            .ifPresent(p -> {
                if (p.getEmail() != null && !p.getEmail().isBlank()) {
                    notificationService.sendRegistrationConfirmation(p);
                }
            });
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── POST /api/v1/register ─────────────────────────────────────────────
    @Operation(summary = "Register participant", description = "Registers a new participant with flight and hotel preferences. Returns a unique registration code on success")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        boolean shuttleOptIn = req.shuttleOptIn() == null || Boolean.TRUE.equals(req.shuttleOptIn());
        Program regProgram = req.programId() != null ? programRepository.findById(req.programId()).orElse(null) : null;
        // Closed / full comes before answer checks, so people aren't asked to fix answers for a closed program.
        try {
            participantService.assertRegistrationOpen(regProgram, req.email());
        } catch (RegistrationClosedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "registration_closed", "message", e.getMessage()));
        }
        // Validate custom answers before anything is written. Programs without questions get null (no change).
        String answersJson;
        Map<Integer, String> souvenirChoices;
        Map<String, String> answerErrors = new LinkedHashMap<>();
        Map<String, Object> validAnswers = Map.of();
        // Name: first + last from current clients; older clients send only full_name.
        boolean splitName = req.firstName() != null || req.lastName() != null;
        String firstName = PersonName.clean(req.firstName()), lastName = PersonName.clean(req.lastName());
        String fullName = splitName ? PersonName.join(firstName, lastName) : PersonName.clean(req.fullName());
        if (splitName) {
            if (firstName == null) answerErrors.put("first_name", "First name is required");
            if (lastName == null)  answerErrors.put("last_name", "Last name is required");
        } else if (fullName == null) {
            answerErrors.put("full_name", "Full name is required");
        }
        // Gender: only when the program asks. Required from current clients, so an older client
        // still loaded in someone's browser during a deploy can finish registering.
        boolean askGender = regProgram == null || !Boolean.FALSE.equals(regProgram.getAskGender());
        String gender = null;
        if (askGender) {
            try { gender = PersonName.gender(req.gender()); }
            catch (IllegalArgumentException e) { answerErrors.put("gender", e.getMessage()); }
            if (gender == null && splitName && !answerErrors.containsKey("gender")) answerErrors.put("gender", "Please choose Male or Female");
        }
        // Phone: not stored when the program doesn't ask for it.
        boolean askPhone = regProgram == null || !Boolean.FALSE.equals(regProgram.getAskPhone());
        String phone = askPhone ? req.phone() : null;
        try {
            validAnswers = registrationFieldService.validate(req.programId(), req.customAnswers(), true);
        } catch (RegistrationFieldService.InvalidAnswersException e) {
            answerErrors.putAll(e.getErrors());
        }
        // Souvenir options (e.g. T-shirt size) are checked alongside the questions; errors use "souvenir:{id}" keys.
        try {
            souvenirChoices = souvenirService.validateChoices(req.programId(), req.souvenirChoices());
        } catch (RegistrationFieldService.InvalidAnswersException e) {
            answerErrors.putAll(e.getErrors());
            souvenirChoices = Map.of();
        }
        if (!answerErrors.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_answers", "message", "Please check your answers", "fields", answerErrors));
        }
        answersJson = registrationFieldService.toJson(validAnswers);
        try {
            Participant p = participantService.register(
                fullName, firstName, lastName, gender, phone, req.email(), req.hotelId(), shuttleOptIn,
                req.arrivalAirline(), req.arrivalFlightNumber(), req.arrivalDatetime(),
                req.departureAirline(), req.departureFlightNumber(), req.departureDatetime(),
                req.programId(), req.state(), answersJson
            );
            souvenirService.saveChoices(p, souvenirChoices);
            if (p.getWaitlistedAt() != null) {
                return ResponseEntity.ok(Map.of("success", true, "reg_code", p.getRegCode(), "message", "Added to the waitlist", "waitlisted", true));
            }
            return ResponseEntity.ok(new RegisterResponse(true, p.getRegCode(), "Registration successful"));
        } catch (AlreadyRegisteredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "already_registered", "message", e.getMessage()));
        } catch (RegistrationClosedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "registration_closed", "message", e.getMessage()));
        }
    }

    // ── POST /api/v1/update-flight ────────────────────────────────────────
    @Operation(summary = "Update flight details", description = "Updates arrival or departure flight information for an existing participant by registration code")
    @PostMapping("/update-flight")
    public ResponseEntity<UpdateFlightResponse> updateFlight(@RequestBody UpdateFlightRequest req) {
        participantService.updateFlight(
            req.regCode(), req.direction(), req.airline(), req.flightNumber(), req.submittedDatetime()
        );
        return ResponseEntity.ok(new UpdateFlightResponse(true, req.regCode(), "Flight updated successfully"));
    }

    /** The program may hide breakout groups from attendees (they stay visible to admins). */
    private boolean showsBreakoutGroup(String programId) {
        if (programId == null) return true;
        return programRepository.findById(programId)
            .map(prog -> !Boolean.FALSE.equals(prog.getShowBreakoutGroup()))
            .orElse(true);
    }

    // ── GET /api/v1/participant-status ────────────────────────────────────
    @Operation(summary = "Get participant status", description = "Returns full transport status for a participant — hotel, flights, assigned runs — identified by their registration code")
    @GetMapping("/participant-status")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ResponseEntity<ParticipantStatusResponse> participantStatus(@RequestParam("code") String code) {
        Participant p = participantRepository.findByRegCodeWithHotel(code)
            .orElseThrow(() -> new EntityNotFoundException("Participant not found: " + code));
        return ResponseEntity.ok(buildStatus(p, false));
    }

    // ── GET /api/v1/admin/participants/{code}/attendee-view ───────────────
    @Operation(summary = "Attendee view (admin lookup)", description = "Everything the attendee would see on their page — room always shown — plus check-in, for helping people without a phone")
    @GetMapping("/admin/participants/{code}/attendee-view")
    @Transactional(readOnly = true)
    public Map<String, Object> attendeeViewForAdmin(@PathVariable String code) {
        Participant p = participantRepository.findByRegCodeWithHotel(code)
            .orElseThrow(() -> new EntityNotFoundException("Participant not found: " + code));
        adminAccess.requireProgram(p.getProgramId());
        Program prog = p.getProgramId() != null ? programRepository.findById(p.getProgramId()).orElse(null) : null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", buildStatus(p, true));
        out.put("email", p.getEmail());
        out.put("gender", p.getGender());
        out.put("checked_in_at", p.getCheckedInAt() != null ? p.getCheckedInAt().toString() : null);
        out.put("checked_in_by", p.getCheckedInBy());
        out.put("breakout_group", p.getBreakoutGroup());
        Map<String, Object> pay = paymentService.summary(p);
        out.put("payment", PaymentService.canSeeAmounts(prog, adminAccess.permissions())
            ? pay : PaymentService.withoutAmounts(pay));
        out.put("permissions", adminAccess.permissions());
        return out;
    }

    /** Everything on the attendee's page. {@code adminView}: rooms are never held back and self-edit details are left out. */
    @SuppressWarnings("unchecked")
    public ParticipantStatusResponse buildStatus(Participant p, boolean adminView) {

        List<Flight> flights = flightRepository.findByParticipant(p);
        Flight arrival   = flights.stream().filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
        Flight departure = flights.stream().filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);

        HotelDto hotelDto = p.getHotel() == null ? null : new HotelDto(
            p.getHotel().getHotelName(),
            p.getHotel().getPickupAddress() != null ? p.getHotel().getPickupAddress() : ""
        );

        boolean boardedArrival = !runParticipantRepository
            .findBoardedArrivalParticipantIds(List.of(p.getId()), p.getProgramId()).isEmpty();
        boolean boardedDeparture = !runParticipantRepository
            .findBoardedDepartureParticipantIds(List.of(p.getId()), p.getProgramId()).isEmpty();

        ParticipantDto participantDto = new ParticipantDto(
            p.getRegCode(),
            p.getFullName(),
            p.getPhone(),
            p.getEmail(),
            p.getStatus() != null ? p.getStatus().name().toLowerCase() : null,
            Boolean.TRUE.equals(p.getNeedsAttention()),
            Boolean.TRUE.equals(p.getShuttleOptIn()),
            hotelDto,
            p.getProgramId(),
            boardedArrival,
            boardedDeparture,
            showsBreakoutGroup(p.getProgramId()) ? p.getBreakoutGroup() : null
        );

        List<Run> participantRuns = getRunsForParticipant(p.getId());

        String arrivalPickupTime = participantRuns.stream()
            .filter(r -> r.getRunType() == RunType.AIRPORT && r.getDirection() == Direction.TO_HOTEL)
            .map(Run::getDepartTime)
            .findFirst().orElse(null);

        String departurePickupTime = participantRuns.stream()
            .filter(r -> r.getRunType() == RunType.AIRPORT && r.getDirection() == Direction.TO_AIRPORT)
            .map(Run::getDepartTime)
            .findFirst().orElse(null);

        List<RunDto> runs = participantRuns.stream().map(this::toRunDto).toList();

        ProgramInfoDto programInfo = null;
        if (p.getProgramId() != null) {
            programInfo = programRepository.findById(p.getProgramId())
                .map(prog -> new ProgramInfoDto(
                    prog.getId(), prog.getName(), prog.getIni(),
                    prog.getCity(), prog.getState(), prog.getLogoUrl(),
                    prog.getStartDate(), prog.getEndDate(),
                    prog.getAirport(), prog.getVenue(),
                    !Boolean.FALSE.equals(prog.getDailyShuttleEnabled()),
                    !Boolean.FALSE.equals(prog.getAirportPickupsEnabled()),
                    Program.normalizeLodgingType(prog.getLodgingType()),
                    prog.getTimezone()
                )).orElse(null);
        }

        Program prog = p.getProgramId() != null ? programRepository.findById(p.getProgramId()).orElse(null) : null;
        // The program may hold rooms back until check-in and/or a date (admins always see them).
        Map<String, Object> roomLocked = adminView || prog == null ? null
            : RoomVisibility.lock(prog, p.getCheckedInAt(), OffsetDateTime.now());
        RoomDto roomDto = roomLocked != null ? null : buildRoomDto(p, programInfo);

        Object[] chaperoneView = chaperoneService.attendeeView(p);
        List<Map<String, Object>> chaperones = (List<Map<String, Object>>) chaperoneView[0];
        if (roomLocked != null) {
            // Don't give the room away through its chaperone's label.
            chaperones = chaperones.stream().map(c -> {
                if (!"room".equals(c.get("role"))) return c;
                Map<String, Object> m = new LinkedHashMap<>(c);
                m.put("label", "Room chaperone");
                return m;
            }).toList();
        }
        return new ParticipantStatusResponse(
            participantDto,
            toFlightDto(arrival, arrivalPickupTime),
            toFlightDto(departure, departurePickupTime),
            runs,
            programInfo,
            roomDto,
            OffsetDateTime.now().toString(),
            souvenirService.attendeeView(p),
            prog != null && Boolean.TRUE.equals(prog.getShowPaymentToAttendees()) ? paymentService.attendeeSummary(p) : null,
            chaperones,
            (List<Map<String, Object>>) chaperoneView[1],
            p.getWaitlistedAt() != null,
            adminView ? null : programRepository.findById(p.getProgramId() != null ? p.getProgramId() : "")
                .filter(pr -> Boolean.TRUE.equals(pr.getAttendeeSelfEdit()))
                .map(pr -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("first_name", p.getFirstName());
                    d.put("last_name", p.getLastName());
                    d.put("phone", p.getPhone());
                    d.put("ask_phone", !Boolean.FALSE.equals(pr.getAskPhone()));
                    d.put("custom_answers", registrationFieldService.parseAnswers(p.getCustomAnswers()));
                    // Questions the attendee can see but not change (the one that sets their fee).
                    d.put("locked", lockedKeys(pr));
                    return d;
                }).orElse(null),
            roomLocked,
            paymentInfo(prog, p),
            announcementService.forAttendee(p.getId())
        );
    }

    /**
     * "How to pay" for the attendee's page: their fee, the program's instructions and whether there's a QR code.
     * Null when there's nothing to say, they're on the waitlist, or they've paid in full.
     */
    private Map<String, Object> paymentInfo(Program prog, Participant p) {
        if (prog == null || p.getWaitlistedAt() != null) return null;
        if (PaymentService.PAID.equals(PaymentService.normalizeStatus(p.getPaymentStatus()))) return null;
        BigDecimal fee = FeeRules.feeFor(prog, p);
        String text = prog.getPaymentInstructions();
        boolean qr = paymentQrRepository.existsById(prog.getId());
        if ((text == null || text.isBlank()) && !qr) return null;
        // What's still owed: their fee less what's been recorded (so someone part paid sees the rest, not the full fee).
        BigDecimal paid = paymentService.totalPaid(p);
        BigDecimal due = fee == null ? null : fee.subtract(paid).max(BigDecimal.ZERO);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instructions", text);
        m.put("has_qr", qr);
        m.put("fee", fee);
        m.put("due", due);
        m.put("paid_some", paid.signum() > 0);
        m.put("currency", prog.getFeeCurrency() != null ? prog.getFeeCurrency() : "USD");
        return m;
    }

    /** Answers attendees can't change themselves: the fee question, when fees differ by answer. */
    private static List<String> lockedKeys(Program prog) {
        return FeeRules.byAnswer(prog) ? List.of(prog.getFeeQuestionKey()) : List.of();
    }

    // ── PATCH /api/v1/my-details ──────────────────────────────────────────
    public record MyDetailsRequest(
        String code,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String phone,
        @JsonProperty("custom_answers") Map<String, Object> customAnswers
    ) {}

    /**
     * An attendee corrects their own details, when the program allows it. Only name, phone and answers to the
     * program's questions: email (how they're found and emailed) and gender (rooms) stay with the admins.
     * Each change is written to the activity log so admins can see what changed.
     */
    @Operation(summary = "Update my details", description = "Attendee self-edit (program setting): first/last name, phone and question answers")
    @PatchMapping("/my-details")
    @Transactional
    public ResponseEntity<?> updateMyDetails(@RequestBody MyDetailsRequest req) {
        if (req.code() == null || req.code().isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Missing code"));
        Participant p = participantRepository.findByRegCode(req.code().trim()).orElse(null);
        if (p == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Registration not found"));
        Program prog = p.getProgramId() != null ? programRepository.findById(p.getProgramId()).orElse(null) : null;
        if (prog == null || !Boolean.TRUE.equals(prog.getAttendeeSelfEdit())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "This program doesn't allow changes here — contact the organisers"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> changed = new ArrayList<>();
        String first = req.firstName() != null ? PersonName.clean(req.firstName()) : p.getFirstName();
        String last  = req.lastName()  != null ? PersonName.clean(req.lastName())  : p.getLastName();
        if (req.firstName() != null && first == null) errors.put("first_name", "First name is required");
        if (req.lastName()  != null && last  == null) errors.put("last_name", "Last name is required");
        String phone = null;
        boolean askPhone = !Boolean.FALSE.equals(prog.getAskPhone());
        if (req.phone() != null && askPhone) {
            phone = req.phone().trim();
            if (phone.replaceAll("\\D", "").length() < 7) errors.put("phone", "Enter a phone number with country code");
        }
        Map<String, Object> answers = null;
        if (req.customAnswers() != null) {
            Map<String, Object> saved = registrationFieldService.parseAnswers(p.getCustomAnswers());
            Map<String, Object> merged = new LinkedHashMap<>(saved);
            merged.putAll(req.customAnswers());
            // The fee question stays as it was: changing it would change what they owe, so only admins can.
            for (String key : lockedKeys(prog)) {
                if (saved.containsKey(key)) merged.put(key, saved.get(key)); else merged.remove(key);
            }
            try { answers = registrationFieldService.validate(p.getProgramId(), merged, false); }
            catch (RegistrationFieldService.InvalidAnswersException e) { errors.putAll(e.getErrors()); }
        }
        if (!errors.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "invalid_answers", "message", "Please check your details", "fields", errors));

        if ((req.firstName() != null || req.lastName() != null)
            && !(Objects.equals(first, p.getFirstName()) && Objects.equals(last, p.getLastName()))) {
            changed.add("name: " + p.getFullName() + " → " + PersonName.join(first, last));
            p.setFirstName(first); p.setLastName(last); p.setFullName(PersonName.join(first, last));
        }
        if (phone != null && !phone.equals(p.getPhone())) { changed.add("phone"); p.setPhone(phone); }
        if (answers != null) {
            String json = registrationFieldService.toJson(answers);
            if (!Objects.equals(json, p.getCustomAnswers())) { changed.add("answers"); p.setCustomAnswers(json); }
        }
        if (!changed.isEmpty()) {
            p.setUpdatedAt(OffsetDateTime.now());
            participantRepository.save(p);
            if (changed.contains("answers")) paymentService.recheck(p);  // an answer can set their fee
            auditLogRepository.save(AuditLog.builder()
                .programId(p.getProgramId()).username("attendee").action("Updated their own details")
                .category("participants").target(p.getRegCode()).targetName(p.getFullName())
                .method("PATCH").path("/api/v1/my-details")
                .detail(String.join(" · ", changed)).build());
        }
        return ResponseEntity.ok(Map.of("updated", !changed.isEmpty()));
    }

    public record SelfBoardRequest(
        @JsonProperty("run_id") String runId,
        Boolean boarded
    ) {}

    // ── PATCH /api/v1/participant/{regCode}/board-run ──────────────────────
    @Operation(summary = "Self-report boarding", description = "Participant marks themselves as boarded on their active run, identified by registration code")
    @PatchMapping("/participant/{regCode}/board-run")
    public ResponseEntity<Map<String, Boolean>> selfBoard(
            @PathVariable String regCode,
            @RequestBody SelfBoardRequest req) {
        Participant p = participantRepository.findByRegCode(regCode)
            .orElseThrow(() -> new EntityNotFoundException("Participant not found: " + regCode));
        Run run = runRepository.findByRunId(req.runId())
            .orElseThrow(() -> new EntityNotFoundException("Run not found: " + req.runId()));
        List<Integer> runIds = runParticipantRepository.findByIdParticipantId(p.getId())
            .stream().map(rp -> rp.getId().getRunId()).toList();
        if (!runIds.contains(run.getId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Participant is not assigned to this run.");
        }
        runService.markBoarded(run.getId(), p.getId(), Boolean.TRUE.equals(req.boarded()));
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── POST /api/v1/twilio-webhook ───────────────────────────────────────
    @Operation(summary = "Twilio SMS webhook", description = "Receives inbound SMS messages forwarded by Twilio and logs them against the matching participant")
    @PostMapping(value = "/twilio-webhook", produces = "text/xml")
    public ResponseEntity<String> twilioWebhook(HttpServletRequest request) {
        String fromPhone   = request.getParameter("From");
        String messageBody = request.getParameter("Body");

        if (fromPhone != null) {
            participantRepository.findByPhone(fromPhone).ifPresent(p ->
                log.info("Inbound SMS from {} ({}): {}", p.getRegCode(), fromPhone, messageBody)
            );
        }

        return ResponseEntity.ok(
            "<Response><Message>Thanks, a coordinator will reach you shortly.</Message></Response>"
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private RoomDto buildRoomDto(Participant p, ProgramInfoDto programInfo) {
        RoomOccupant occ = roomOccupantRepository.findFirstByParticipantId(p.getId()).orElse(null);
        // Fallback: participants who registered before the CSV was imported won't have participant_id set
        if (occ == null && p.getEmail() != null && p.getProgramId() != null) {
            occ = roomOccupantRepository
                .findByProgramIdAndEmailIgnoreCase(p.getProgramId(), p.getEmail())
                .orElse(null);
        }
        if (occ == null) return null;

        RoomAssignment room = occ.getRoom();
        if (room.getProgramId() != null && p.getProgramId() != null
                && !room.getProgramId().equals(p.getProgramId())) return null;
        int capacity = switch (room.getRoomType() == null ? "" : room.getRoomType()) {
            case "4-person" -> 4;
            default -> 2;
        };
        long guests = room.getOccupants().stream().filter(o -> o.getName() != null && !o.getName().isBlank()).count();

        // Check roommate visibility from program
        boolean roommateVisible = true;
        if (programInfo != null) {
            Program prog = programRepository.findById(programInfo.id()).orElse(null);
            if (prog != null && prog.getRoommateVisible() != null) {
                roommateVisible = prog.getRoommateVisible();
            }
        }

        List<RoommateSummary> roommates = null;
        if (roommateVisible) {
            roommates = room.getOccupants().stream()
                .filter(o -> {
                    if (o.getParticipant() != null) return !o.getParticipant().getId().equals(p.getId());
                    if (p.getEmail() != null && p.getEmail().equalsIgnoreCase(o.getEmail())) return false;
                    return true;
                })
                .filter(o -> o.getName() != null && !o.getName().isBlank())
                .map(o -> new RoommateSummary(o.getName(), o.getEmail(), o.getPhone()))
                .toList();
        }

        return new RoomDto(room.getRoomLabel(), room.getRoomType(), room.getHotelName(),
            (int) guests, capacity, room.getGender(), roommates);
    }

    private CoordinatorDto toCoordinatorDto(String name, String phone, String whatsapp) {
        if (name == null || name.isBlank()) return null;
        return new CoordinatorDto(
            name,
            phone != null ? phone : "",
            whatsapp != null ? "https://wa.me/" + whatsapp.replaceAll("[^0-9]", "") : ""
        );
    }

    private FlightDto toFlightDto(Flight f, String pickupTime) {
        if (f == null) return null;
        return new FlightDto(
            f.getAirline(),
            f.getFlightNumber(),
            f.getSubmittedDatetime() != null ? f.getSubmittedDatetime().toString() : null,
            f.getLiveEta() != null ? f.getLiveEta().toString() : null,
            f.getFlightStatus() != null ? f.getFlightStatus().name().toLowerCase() : "unknown",
            f.getDelayMins() != null ? f.getDelayMins() : 0,
            Boolean.TRUE.equals(f.getPollingActive()),
            f.getLeg4PickupFrom() != null ? f.getLeg4PickupFrom().name().toLowerCase() : null,
            pickupTime
        );
    }

    private RunDto toRunDto(Run r) {
        return new RunDto(
            r.getRunId(),
            r.getRunType() != null ? r.getRunType().name().toLowerCase() : null,
            r.getDirection() != null ? r.getDirection().name().toLowerCase() : null,
            r.getDepartTime(),
            r.getStatus() != null ? r.getStatus().name().toLowerCase() : null,
            r.getVehicle() != null ? r.getVehicle().getLabel() : null,
            r.getDriver() != null ? r.getDriver().getName() : null,
            r.getDriver() != null ? r.getDriver().getPhone() : null,
            r.getHotel() != null ? r.getHotel().getHotelName() : r.getPickupLocation()
        );
    }

    private List<Run> getRunsForParticipant(Integer participantId) {
        List<Integer> runIds = runParticipantRepository.findByIdParticipantId(participantId)
            .stream().map(rp -> rp.getId().getRunId()).toList();
        if (runIds.isEmpty()) return List.of();
        return runRepository.findAllByIdWithDetails(runIds);
    }

    // ── Static file serving (local uploads) ──────────────────────────────
    @Operation(summary = "Serve uploaded file", description = "Serves an image or binary file from the local uploads directory by filename")
    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        try {
            Path file = Paths.get(uploadsDir).resolve(filename).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = filename.toLowerCase().endsWith(".png") ? "image/png"
                : filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg") ? "image/jpeg"
                : "application/octet-stream";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
