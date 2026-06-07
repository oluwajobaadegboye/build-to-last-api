package com.btl.transport.participant;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.hotel.Hotel;
import com.btl.transport.hotel.HotelRepository;
import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.room.AccommodationContact;
import com.btl.transport.room.AccommodationContactRepository;
import com.btl.transport.room.RoomAssignment;
import com.btl.transport.room.RoomOccupant;
import com.btl.transport.room.RoomOccupantRepository;
import com.btl.transport.notification.NotificationService;
import com.btl.transport.notification.SendGridService;
import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramRepository;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunParticipantRepository;
import com.btl.transport.run.RunRepository;
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
import com.btl.transport.infrastructure.StorageService;
import static com.btl.transport.participant.ParticipantDtos.*;

@Tag(name = "Participant", description = "Public-facing participant registration, status, and flight update endpoints")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController {

    @Value("${btl.uploads.dir:./uploads}")
    private String uploadsDir;

    private final StorageService storageService;
    private final ParticipantService participantService;
    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final NotificationConfigRepository notificationConfigRepository;
    private final ProgramRepository programRepository;
    private final SendGridService sendGridService;
    private final NotificationService notificationService;
    private final RoomOccupantRepository roomOccupantRepository;
    private final AccommodationContactRepository accommodationContactRepository;

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
        Program program = programRepository.findById(programId)
            .orElseThrow(() -> new EntityNotFoundException("Program not found: " + programId));
        List<Hotel> hotels = hotelRepository.findByProgramIdOrderByShuttleStopOrderAsc(programId);
        List<Map<String, Object>> hotelList = hotels.stream()
            .map(h -> Map.<String, Object>of(
                "hotel_id", h.getId(),
                "hotel_name", h.getHotelName() != null ? h.getHotelName() : "",
                "pickup_address", h.getPickupAddress() != null ? h.getPickupAddress() : ""
            ))
            .toList();
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("id", program.getId());
        resp.put("name", program.getName());
        resp.put("ini", program.getIni() != null ? program.getIni() : "");
        resp.put("phase", program.getPhase() != null ? program.getPhase() : "setup");
        resp.put("city", program.getCity() != null ? program.getCity() : "");
        resp.put("state", program.getState() != null ? program.getState() : "");
        resp.put("logo_url", program.getLogoUrl() != null ? storageService.presign(program.getLogoUrl()) : "");
        resp.put("start_date", program.getStartDate() != null ? program.getStartDate() : "");
        resp.put("end_date", program.getEndDate() != null ? program.getEndDate() : "");
        resp.put("hotel_selection_enabled", program.getHotelSelectionEnabled() != null ? program.getHotelSelectionEnabled() : true);
        resp.put("registration_open", program.getRegistrationOpen() != null ? program.getRegistrationOpen() : true);
        resp.put("reg_title",       program.getRegTitle());
        resp.put("reg_description", program.getRegDescription());
        resp.put("hotels", hotelList);
        return ResponseEntity.ok(resp);
    }

    // ── GET /api/v1/coordinator-contacts ──────────────────────────────────
    @Operation(summary = "Get coordinator contacts", description = "Returns coordinator phone and WhatsApp contact details, optionally scoped to a program")
    @GetMapping("/coordinator-contacts")
    public ResponseEntity<CoordinatorContactsResponse> coordinatorContacts(
            @RequestParam(name = "program_id", required = false) String programId) {
        NotificationConfig cfg = null;
        if (programId != null) {
            cfg = notificationConfigRepository.findByProgramId(programId).orElse(null);
        }
        if (cfg == null) {
            cfg = notificationConfigRepository.findByConfigKey("main").orElse(null);
        }
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
    @Operation(summary = "Resend registration code", description = "Re-sends the participant's BTL transport code to their registered email address")
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
    @Operation(summary = "Register participant", description = "Registers a new participant with flight and hotel preferences. Returns a unique BTL code on success")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        boolean shuttleOptIn = req.shuttleOptIn() == null || Boolean.TRUE.equals(req.shuttleOptIn());
        try {
            Participant p = participantService.register(
                req.fullName(), req.phone(), req.email(), req.hotelId(), shuttleOptIn,
                req.arrivalAirline(), req.arrivalFlightNumber(), req.arrivalDatetime(),
                req.departureAirline(), req.departureFlightNumber(), req.departureDatetime(),
                req.programId(), req.state()
            );
            return ResponseEntity.ok(new RegisterResponse(true, p.getBtlCode(), "Registration successful"));
        } catch (AlreadyRegisteredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "already_registered", "message", e.getMessage()));
        } catch (RegistrationClosedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "registration_closed", "message", e.getMessage()));
        }
    }

    // ── POST /api/v1/update-flight ────────────────────────────────────────
    @Operation(summary = "Update flight details", description = "Updates arrival or departure flight information for an existing participant by BTL code")
    @PostMapping("/update-flight")
    public ResponseEntity<UpdateFlightResponse> updateFlight(@RequestBody UpdateFlightRequest req) {
        participantService.updateFlight(
            req.btlCode(), req.direction(), req.airline(), req.flightNumber(), req.submittedDatetime()
        );
        return ResponseEntity.ok(new UpdateFlightResponse(true, req.btlCode(), "Flight updated successfully"));
    }

    // ── GET /api/v1/participant-status ────────────────────────────────────
    @Operation(summary = "Get participant status", description = "Returns full transport status for a participant — hotel, flights, assigned runs — identified by their BTL code")
    @GetMapping("/participant-status")
    @Transactional(readOnly = true)
    public ResponseEntity<ParticipantStatusResponse> participantStatus(@RequestParam("code") String code) {
        Participant p = participantRepository.findByBtlCodeWithHotel(code)
            .orElseThrow(() -> new EntityNotFoundException("Participant not found: " + code));

        List<Flight> flights = flightRepository.findByParticipant(p);
        Flight arrival   = flights.stream().filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
        Flight departure = flights.stream().filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);

        HotelDto hotelDto = p.getHotel() == null ? null : new HotelDto(
            p.getHotel().getHotelName(),
            p.getHotel().getPickupAddress() != null ? p.getHotel().getPickupAddress() : ""
        );

        ParticipantDto participantDto = new ParticipantDto(
            p.getBtlCode(),
            p.getFullName(),
            p.getPhone(),
            p.getEmail(),
            p.getStatus() != null ? p.getStatus().name().toLowerCase() : null,
            Boolean.TRUE.equals(p.getNeedsAttention()),
            Boolean.TRUE.equals(p.getShuttleOptIn()),
            hotelDto,
            p.getProgramId()
        );

        List<RunDto> runs = getRunsForParticipant(p.getId()).stream().map(this::toRunDto).toList();

        ProgramInfoDto programInfo = null;
        if (p.getProgramId() != null) {
            programInfo = programRepository.findById(p.getProgramId())
                .map(prog -> new ProgramInfoDto(
                    prog.getId(), prog.getName(), prog.getIni(),
                    prog.getCity(), prog.getState(), prog.getLogoUrl(),
                    prog.getStartDate(), prog.getEndDate()
                )).orElse(null);
        }

        RoomDto roomDto = buildRoomDto(p, programInfo);

        return ResponseEntity.ok(new ParticipantStatusResponse(
            participantDto,
            toFlightDto(arrival),
            toFlightDto(departure),
            runs,
            programInfo,
            roomDto,
            OffsetDateTime.now().toString()
        ));
    }

    // ── POST /api/v1/send-notification ────────────────────────────────────
    @Operation(summary = "Send notification", description = "Queues a transport notification for delivery (stub — always returns success)")
    @PostMapping("/send-notification")
    public ResponseEntity<NotificationResponse> sendNotification() {
        return ResponseEntity.ok(new NotificationResponse(true, "Notification queued"));
    }

    // ── POST /api/v1/twilio-webhook ───────────────────────────────────────
    @Operation(summary = "Twilio SMS webhook", description = "Receives inbound SMS messages forwarded by Twilio and logs them against the matching participant")
    @PostMapping(value = "/twilio-webhook", produces = "text/xml")
    public ResponseEntity<String> twilioWebhook(HttpServletRequest request) {
        String fromPhone   = request.getParameter("From");
        String messageBody = request.getParameter("Body");

        if (fromPhone != null) {
            participantRepository.findByPhone(fromPhone).ifPresent(p ->
                log.info("Inbound SMS from {} ({}): {}", p.getBtlCode(), fromPhone, messageBody)
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
            (int) guests, capacity, roommates);
    }

    private CoordinatorDto toCoordinatorDto(String name, String phone, String whatsapp) {
        if (name == null) return null;
        return new CoordinatorDto(
            name,
            phone != null ? phone : "",
            whatsapp != null ? "https://wa.me/" + whatsapp.replaceAll("[^0-9]", "") : ""
        );
    }

    private FlightDto toFlightDto(Flight f) {
        if (f == null) return null;
        return new FlightDto(
            f.getAirline(),
            f.getFlightNumber(),
            f.getSubmittedDatetime() != null ? f.getSubmittedDatetime().toString() : null,
            f.getLiveEta() != null ? f.getLiveEta().toString() : null,
            f.getFlightStatus() != null ? f.getFlightStatus().name().toLowerCase() : "unknown",
            f.getDelayMins() != null ? f.getDelayMins() : 0,
            Boolean.TRUE.equals(f.getPollingActive()),
            f.getLeg4PickupFrom() != null ? f.getLeg4PickupFrom().name().toLowerCase() : null
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
            r.getDriver() != null ? r.getDriver().getPhone() : null
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
