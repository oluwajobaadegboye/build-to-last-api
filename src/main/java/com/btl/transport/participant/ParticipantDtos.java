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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccommodationContactsResponse(
        @JsonProperty("contact_1") CoordinatorDto contact1,
        @JsonProperty("contact_2") CoordinatorDto contact2
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
        HotelDto hotel,
        @JsonProperty("program_id") String programId,
        @JsonProperty("boarded_arrival") boolean boardedArrival,
        @JsonProperty("boarded_departure") boolean boardedDeparture
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
        @JsonProperty("leg4_pickup_from") String leg4PickupFrom,
        @JsonProperty("pickup_time") String pickupTime
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
        @JsonProperty("driver_phone") String driverPhone,
        @JsonProperty("pickup_location") String pickupLocation
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProgramInfoDto(
        String id,
        String name,
        String ini,
        String city,
        String state,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date") String endDate
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RoommateSummary(
        String name,
        String email,
        String phone
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RoomDto(
        @JsonProperty("room_label") String roomLabel,
        @JsonProperty("room_type")  String roomType,
        @JsonProperty("hotel_name") String hotelName,
        int guests,
        int capacity,
        String gender,
        List<RoommateSummary> roommates
    ) {}

    record ParticipantStatusResponse(
        ParticipantDto participant,
        FlightDto arrival,
        FlightDto departure,
        List<RunDto> runs,
        @JsonProperty("program") ProgramInfoDto program,
        @JsonProperty("room") RoomDto room,
        @JsonProperty("generated_at") String generatedAt
    ) {}
}
