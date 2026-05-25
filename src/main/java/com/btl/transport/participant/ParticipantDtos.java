package com.btl.transport.participant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public final class ParticipantDtos {

    private ParticipantDtos() {}

    // ── Requests ───────────────────────────────────────────────────────────

    record UpdateFlightRequest(
        @JsonProperty("btl_code") String btlCode,
        String direction,
        String airline,
        @JsonProperty("flight_number") String flightNumber,
        @JsonProperty("submitted_datetime") OffsetDateTime submittedDatetime
    ) {}

    // ── Responses ──────────────────────────────────────────────────────────

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
        @JsonProperty("driver_name") String driverName,
        @JsonProperty("driver_phone") String driverPhone
    ) {}

    record ParticipantStatusResponse(
        ParticipantDto participant,
        FlightDto arrival,
        FlightDto departure,
        List<RunDto> runs,
        @JsonProperty("generated_at") String generatedAt
    ) {}
}
