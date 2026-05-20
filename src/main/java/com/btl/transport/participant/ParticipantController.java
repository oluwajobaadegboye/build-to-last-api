package com.btl.transport.participant;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.hotel.HotelRepository;
import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunParticipantRepository;
import com.btl.transport.run.RunRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ParticipantController {

    private final ParticipantService participantService;
    private final ParticipantRepository participantRepository;
    private final FlightRepository flightRepository;
    private final HotelRepository hotelRepository;
    private final RunRepository runRepository;
    private final RunParticipantRepository runParticipantRepository;
    private final NotificationConfigRepository notificationConfigRepository;

    // ── Request records ────────────────────────────────────────────────────

    record UpdateFlightRequest(
        @JsonProperty("btl_code") String btlCode,
        String direction,
        String airline,
        @JsonProperty("flight_number") String flightNumber,
        @JsonProperty("submitted_datetime") OffsetDateTime submittedDatetime
    ) {}

    // ── Response records ───────────────────────────────────────────────────

    record HealthResponse(String status, String db, String timestamp) {}

    record HotelResponse(
        Integer id,
        @JsonProperty("hotel_name") String hotelName,
        @JsonProperty("pickup_address") String pickupAddress,
        @JsonProperty("shuttle_stop_order") Integer shuttleStopOrder
    ) {}

    record CoordinatorDto(
        String name,
        String phone,
        @JsonProperty("whatsapp_link") String whatsappLink
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CoordinatorContactsResponse(
        @JsonProperty("coordinator_1") CoordinatorDto coordinator1,
        @JsonProperty("coordinator_2") CoordinatorDto coordinator2
    ) {}

    record RegisterResponse(
        boolean success,
        @JsonProperty("btl_code") String btlCode,
        String message
    ) {}

    record UpdateFlightResponse(
        boolean success,
        @JsonProperty("btl_code") String btlCode,
        String message
    ) {}

    record NotificationResponse(boolean success, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HotelDto(
        @JsonProperty("hotel_name") String hotelName,
        @JsonProperty("pickup_address") String pickupAddress
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ParticipantDto(
        @JsonProperty("btl_code") String btlCode,
        @JsonProperty("full_name") String fullName,
        String phone,
        String email,
        String status,
        @JsonProperty("needs_attention") boolean needsAttention,
        @JsonProperty("shuttle_opt_in") boolean shuttleOptIn,
        HotelDto hotel
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FlightDto(
        String airline,
        @JsonProperty("flight_number") String flightNumber,
        @JsonProperty("submitted_datetime") String submittedDatetime,
        @JsonProperty("live_eta") String liveEta,
        @JsonProperty("flight_status") String flightStatus,
        @JsonProperty("delay_mins") Integer delayMins,
        @JsonProperty("polling_active") boolean pollingActive,
        @JsonProperty("leg4_pickup_from") String leg4PickupFrom
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RunDto(
        @JsonProperty("run_id") String runId,
        @JsonProperty("run_type") String runType,
        String direction,
        @JsonProperty("depart_time") String departTime,
        String status,
        @JsonProperty("vehicle_label") String vehicleLabel,
        @JsonProperty("driver_name") String driverName
    ) {}

    record ParticipantStatusResponse(
        ParticipantDto participant,
        FlightDto arrival,
        FlightDto departure,
        List<RunDto> runs,
        @JsonProperty("generated_at") String generatedAt
    ) {}

    // ── GET /api/v1/health ─────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "UP", OffsetDateTime.now().toString()));
    }

    // ── GET /api/v1/hotels ─────────────────────────────────────────────────
    @GetMapping("/hotels")
    public ResponseEntity<List<HotelResponse>> getHotels() {
        return ResponseEntity.ok(
            hotelRepository.findAllByOrderByShuttleStopOrderAsc().stream()
                .map(h -> new HotelResponse(h.getId(), h.getHotelName(), h.getPickupAddress(), h.getShuttleStopOrder()))
                .toList()
        );
    }

    // ── GET /api/v1/coordinator-contacts ──────────────────────────────────
    @GetMapping("/coordinator-contacts")
    public ResponseEntity<CoordinatorContactsResponse> coordinatorContacts() {
        NotificationConfig cfg = notificationConfigRepository.findByConfigKey("main").orElse(null);
        if (cfg == null) return ResponseEntity.ok(new CoordinatorContactsResponse(null, null));

        return ResponseEntity.ok(new CoordinatorContactsResponse(
            toCoordinatorDto(cfg.getAdminName1(), cfg.getAdminPhone1(), cfg.getAdminWhatsapp1()),
            toCoordinatorDto(cfg.getAdminName2(), cfg.getAdminPhone2(), cfg.getAdminWhatsapp2())
        ));
    }

    // ── POST /api/v1/register ─────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        boolean shuttleOptIn = req.shuttleOptIn() == null || Boolean.TRUE.equals(req.shuttleOptIn());
        Participant p = participantService.register(
            req.fullName(), req.phone(), req.email(), req.hotelId(), shuttleOptIn,
            req.arrivalAirline(), req.arrivalFlightNumber(), req.arrivalDatetime(),
            req.departureAirline(), req.departureFlightNumber(), req.departureDatetime()
        );
        return ResponseEntity.ok(new RegisterResponse(true, p.getBtlCode(), "Registration successful"));
    }

    // ── POST /api/v1/update-flight ────────────────────────────────────────
    @PostMapping("/update-flight")
    public ResponseEntity<UpdateFlightResponse> updateFlight(@RequestBody UpdateFlightRequest req) {
        participantService.updateFlight(
            req.btlCode(), req.direction(), req.airline(), req.flightNumber(), req.submittedDatetime()
        );
        return ResponseEntity.ok(new UpdateFlightResponse(true, req.btlCode(), "Flight updated successfully"));
    }

    // ── GET /api/v1/participant-status ────────────────────────────────────
    @GetMapping("/participant-status")
    public ResponseEntity<ParticipantStatusResponse> participantStatus(@RequestParam("code") String code) {
        Participant p = participantRepository.findByBtlCode(code)
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
            hotelDto
        );

        List<RunDto> runs = getRunsForParticipant(p.getId()).stream().map(this::toRunDto).toList();

        return ResponseEntity.ok(new ParticipantStatusResponse(
            participantDto,
            toFlightDto(arrival),
            toFlightDto(departure),
            runs,
            OffsetDateTime.now().toString()
        ));
    }

    // ── POST /api/v1/send-notification ────────────────────────────────────
    @PostMapping("/send-notification")
    public ResponseEntity<NotificationResponse> sendNotification() {
        return ResponseEntity.ok(new NotificationResponse(true, "Notification queued"));
    }

    // ── POST /api/v1/twilio-webhook ───────────────────────────────────────
    @PostMapping(value = "/twilio-webhook", produces = "text/xml")
    public ResponseEntity<String> twilioWebhook(HttpServletRequest request) {
        String fromPhone  = request.getParameter("From");
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
            r.getDriver() != null ? r.getDriver().getName() : null
        );
    }

    private List<Run> getRunsForParticipant(Integer participantId) {
        List<Integer> runIds = runParticipantRepository.findByIdParticipantId(participantId)
            .stream().map(rp -> rp.getId().getRunId()).toList();
        if (runIds.isEmpty()) return List.of();
        return runRepository.findAllById(runIds);
    }
}
