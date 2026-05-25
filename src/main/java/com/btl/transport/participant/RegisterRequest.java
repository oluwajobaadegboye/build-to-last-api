package com.btl.transport.participant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

@ValidFlightPairs
public record RegisterRequest(

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    @JsonProperty("full_name")
    String fullName,

    @Pattern(
        regexp = "^\\+[1-9]\\d{7,14}$",
        message = "Phone must be in E.164 format (e.g. +16418191032)"
    )
    String phone,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @Positive(message = "Hotel ID must be a positive number")
    @JsonProperty("hotel_id")
    Integer hotelId,

    @JsonProperty("shuttle_opt_in")
    Boolean shuttleOptIn,

    @JsonProperty("arrival_airline")
    String arrivalAirline,

    @JsonProperty("arrival_flight_number")
    String arrivalFlightNumber,

    @JsonProperty("arrival_datetime")
    OffsetDateTime arrivalDatetime,

    @JsonProperty("departure_airline")
    String departureAirline,

    @JsonProperty("departure_flight_number")
    String departureFlightNumber,

    @JsonProperty("departure_datetime")
    OffsetDateTime departureDatetime,

    @JsonProperty("program_id")
    String programId
) {}
