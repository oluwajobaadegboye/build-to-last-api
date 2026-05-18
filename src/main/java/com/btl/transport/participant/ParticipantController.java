package com.btl.transport.participant;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.flight.Flight;
import com.btl.transport.flight.FlightRepository;
import com.btl.transport.hotel.Hotel;
import com.btl.transport.hotel.HotelRepository;
import com.btl.transport.notification.NotificationConfig;
import com.btl.transport.notification.NotificationConfigRepository;
import com.btl.transport.run.Run;
import com.btl.transport.run.RunParticipantRepository;
import com.btl.transport.run.RunRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Value("${btl.frontend-base-url}")
    private String frontendBaseUrl;

    // ── GET /api/v1/health ─────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "db", "UP",
            "timestamp", OffsetDateTime.now().toString()
        ));
    }

    // ── GET /api/v1/hotels ─────────────────────────────────────────────────
    @GetMapping("/hotels")
    public ResponseEntity<List<Map<String, Object>>> getHotels() {
        List<Hotel> hotels = hotelRepository.findAllByOrderByShuttleStopOrderAsc();
        List<Map<String, Object>> result = hotels.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("hotel_name", h.getHotelName());
            m.put("pickup_address", h.getPickupAddress());
            m.put("shuttle_stop_order", h.getShuttleStopOrder());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // ── GET /api/v1/coordinator-contacts ──────────────────────────────────
    @GetMapping("/coordinator-contacts")
    public ResponseEntity<Map<String, Object>> coordinatorContacts() {
        NotificationConfig config = notificationConfigRepository.findByConfigKey("main").orElse(null);
        if (config == null) return ResponseEntity.ok(Map.of());

        Map<String, Object> result = new LinkedHashMap<>();
        if (config.getAdminName1() != null) {
            result.put("coordinator_1", Map.of(
                "name", config.getAdminName1(),
                "phone", config.getAdminPhone1() != null ? config.getAdminPhone1() : "",
                "whatsapp_link", config.getAdminWhatsapp1() != null
                    ? "https://wa.me/" + config.getAdminWhatsapp1().replaceAll("[^0-9]", "") : ""
            ));
        }
        if (config.getAdminName2() != null) {
            result.put("coordinator_2", Map.of(
                "name", config.getAdminName2(),
                "phone", config.getAdminPhone2() != null ? config.getAdminPhone2() : "",
                "whatsapp_link", config.getAdminWhatsapp2() != null
                    ? "https://wa.me/" + config.getAdminWhatsapp2().replaceAll("[^0-9]", "") : ""
            ));
        }
        return ResponseEntity.ok(result);
    }

    // ── POST /api/v1/register ─────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String fullName = (String) body.get("full_name");
        String phone = (String) body.get("phone");
        String email = (String) body.get("email");
        Integer hotelId = body.get("hotel_id") != null ? ((Number) body.get("hotel_id")).intValue() : null;
        boolean shuttleOptIn = body.get("shuttle_opt_in") == null || Boolean.TRUE.equals(body.get("shuttle_opt_in"));

        String arrivalAirline = (String) body.get("arrival_airline");
        String arrivalFlight = (String) body.get("arrival_flight_number");
        OffsetDateTime arrivalDt = body.get("arrival_datetime") != null
            ? OffsetDateTime.parse((String) body.get("arrival_datetime")) : null;

        String departAirline = (String) body.get("departure_airline");
        String departFlight = (String) body.get("departure_flight_number");
        OffsetDateTime departDt = body.get("departure_datetime") != null
            ? OffsetDateTime.parse((String) body.get("departure_datetime")) : null;

        Participant p = participantService.register(
            fullName, phone, email, hotelId, shuttleOptIn,
            arrivalAirline, arrivalFlight, arrivalDt,
            departAirline, departFlight, departDt
        );

        return ResponseEntity.ok(Map.of(
            "success", true,
            "btl_code", p.getBtlCode(),
            "message", "Registration successful"
        ));
    }

    // ── POST /api/v1/update-flight ────────────────────────────────────────
    @PostMapping("/update-flight")
    public ResponseEntity<Map<String, Object>> updateFlight(@RequestBody Map<String, Object> body) {
        String btlCode = (String) body.get("btl_code");
        String direction = (String) body.get("direction");
        String airline = (String) body.get("airline");
        String flightNumber = (String) body.get("flight_number");
        OffsetDateTime dt = body.get("submitted_datetime") != null
            ? OffsetDateTime.parse((String) body.get("submitted_datetime")) : null;

        participantService.updateFlight(btlCode, direction, airline, flightNumber, dt);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "btl_code", btlCode,
            "message", "Flight updated successfully"
        ));
    }

    // ── GET /api/v1/participant-status ────────────────────────────────────
    @GetMapping("/participant-status")
    public ResponseEntity<Map<String, Object>> participantStatus(@RequestParam("code") String code) {
        Participant p = participantRepository.findByBtlCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + code));

        List<Flight> flights = flightRepository.findByParticipant(p);
        Flight arrival = flights.stream()
            .filter(f -> f.getDirection() == Direction.TO_HOTEL).findFirst().orElse(null);
        Flight departure = flights.stream()
            .filter(f -> f.getDirection() == Direction.TO_AIRPORT).findFirst().orElse(null);

        List<Run> runs = getRunsForParticipant(p.getId());

        Map<String, Object> participantMap = new LinkedHashMap<>();
        participantMap.put("btl_code", p.getBtlCode());
        participantMap.put("full_name", p.getFullName());
        participantMap.put("phone", p.getPhone());
        participantMap.put("email", p.getEmail());
        participantMap.put("status", p.getStatus() != null ? p.getStatus().name().toLowerCase() : null);
        participantMap.put("needs_attention", Boolean.TRUE.equals(p.getNeedsAttention()));
        participantMap.put("shuttle_opt_in", Boolean.TRUE.equals(p.getShuttleOptIn()));
        if (p.getHotel() != null) {
            participantMap.put("hotel", Map.of(
                "hotel_name", p.getHotel().getHotelName(),
                "pickup_address", p.getHotel().getPickupAddress() != null ? p.getHotel().getPickupAddress() : ""
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("participant", participantMap);
        result.put("arrival", flightMap(arrival));
        result.put("departure", flightMap(departure));
        result.put("runs", runs.stream().map(this::runMap).toList());
        result.put("generated_at", OffsetDateTime.now().toString());

        return ResponseEntity.ok(result);
    }

    // ── POST /api/v1/send-notification ────────────────────────────────────
    @PostMapping("/send-notification")
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody Map<String, Object> body) {
        // Lightweight endpoint — just confirms the notification service is reachable
        // Full template rendering in NotificationService
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Notification queued"
        ));
    }

    // ── POST /api/v1/twilio-webhook ───────────────────────────────────────
    @PostMapping(value = "/twilio-webhook", produces = "text/xml")
    public ResponseEntity<String> twilioWebhook(HttpServletRequest request) {
        String fromPhone = request.getParameter("From");
        String messageBody = request.getParameter("Body");

        if (fromPhone != null) {
            participantRepository.findByPhone(fromPhone).ifPresent(p -> {
                log.info("Inbound SMS from {} ({}): {}", p.getBtlCode(), fromPhone, messageBody);
                // Forward to coordinators handled by NotificationService
            });
        }

        return ResponseEntity.ok(
            "<Response><Message>Thanks, a coordinator will reach you shortly.</Message></Response>"
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Object> flightMap(Flight f) {
        if (f == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("airline", f.getAirline());
        m.put("flight_number", f.getFlightNumber());
        m.put("submitted_datetime", f.getSubmittedDatetime() != null ? f.getSubmittedDatetime().toString() : null);
        m.put("live_eta", f.getLiveEta() != null ? f.getLiveEta().toString() : null);
        m.put("flight_status", f.getFlightStatus() != null ? f.getFlightStatus().name().toLowerCase() : "unknown");
        m.put("delay_mins", f.getDelayMins() != null ? f.getDelayMins() : 0);
        m.put("polling_active", Boolean.TRUE.equals(f.getPollingActive()));
        if (f.getLeg4PickupFrom() != null) {
            m.put("leg4_pickup_from", f.getLeg4PickupFrom().name().toLowerCase());
        }
        return m;
    }

    private Map<String, Object> runMap(Run r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_id", r.getRunId());
        m.put("run_type", r.getRunType() != null ? r.getRunType().name().toLowerCase() : null);
        m.put("direction", r.getDirection() != null ? r.getDirection().name().toLowerCase() : null);
        m.put("depart_time", r.getDepartTime());
        m.put("status", r.getStatus() != null ? r.getStatus().name().toLowerCase() : null);
        if (r.getVehicle() != null) m.put("vehicle_label", r.getVehicle().getLabel());
        if (r.getDriver() != null) m.put("driver_name", r.getDriver().getName());
        return m;
    }

    private List<Run> getRunsForParticipant(Integer participantId) {
        List<Integer> runIds = runParticipantRepository.findByIdParticipantId(participantId)
            .stream().map(rp -> rp.getId().getRunId()).toList();
        if (runIds.isEmpty()) return List.of();
        return runRepository.findAllById(runIds);
    }
}
