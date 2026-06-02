package com.btl.transport.notification;

import com.btl.transport.participant.Participant;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class SheetsWebhookService {

    @Value("${btl.sheets.webhook-url:}")
    private String webhookUrl;

    @Value("${btl.sheets.enabled:true}")
    private boolean enabled;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Async
    public void appendRegistration(
            Participant p,
            String arrivalAirline, String arrivalFlightNumber, LocalDateTime arrivalDatetime,
            String departureAirline, String departureFlightNumber, LocalDateTime departureDatetime,
            String programName) {

        if (!enabled || webhookUrl == null || webhookUrl.isBlank()) return;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("registeredAt",            p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        body.put("btlCode",                 p.getBtlCode());
        body.put("fullName",                p.getFullName());
        body.put("email",                   p.getEmail() != null ? p.getEmail() : "");
        body.put("phone",                   p.getPhone() != null ? p.getPhone() : "");
        body.put("hotel",                   p.getHotel() != null ? p.getHotel().getHotelName() : "");
        body.put("program",                 programName != null ? programName : (p.getProgramId() != null ? p.getProgramId() : ""));
        body.put("shuttleOptIn",            Boolean.TRUE.equals(p.getShuttleOptIn()));
        body.put("arrivalAirline",          arrivalAirline != null ? arrivalAirline : "");
        body.put("arrivalFlightNumber",     arrivalFlightNumber != null ? arrivalFlightNumber : "");
        body.put("arrivalDatetime",         arrivalDatetime != null ? arrivalDatetime.toString() : "");
        body.put("departureAirline",        departureAirline != null ? departureAirline : "");
        body.put("departureFlightNumber",   departureFlightNumber != null ? departureFlightNumber : "");
        body.put("departureDatetime",       departureDatetime != null ? departureDatetime.toString() : "");

        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Sheets row appended for {} — HTTP {}", p.getBtlCode(), resp.statusCode());
        } catch (Exception e) {
            log.warn("Sheets webhook failed for {}: {}", p.getBtlCode(), e.getMessage());
        }
    }
}
