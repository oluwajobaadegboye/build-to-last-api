package com.btl.transport.participant;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

@ValidFlightPairs
public record RegisterRequest(

    /** Older clients send only full_name; current ones send first_name + last_name (full_name is then built from them). */
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    @JsonProperty("full_name")
    String fullName,

    @Size(max = PersonName.MAX_PART, message = "First name must not exceed 120 characters")
    @JsonProperty("first_name")
    String firstName,

    @Size(max = PersonName.MAX_PART, message = "Last name must not exceed 120 characters")
    @JsonProperty("last_name")
    String lastName,

    /** "male" | "female"; required when the program asks for gender (and the client sends first/last name). */
    String gender,

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
    LocalDateTime arrivalDatetime,

    @JsonProperty("departure_airline")
    String departureAirline,

    @JsonProperty("departure_flight_number")
    String departureFlightNumber,

    @JsonProperty("departure_datetime")
    LocalDateTime departureDatetime,

    @JsonProperty("program_id")
    String programId,

    @Size(max = 2, message = "State must be a 2-letter US state code")
    String state,

    /** Answers to the program's custom questions, keyed by question key. Optional. */
    @JsonProperty("custom_answers")
    java.util.Map<String, Object> customAnswers,

    /** Souvenir options chosen at registration (e.g. T-shirt size), keyed by souvenir item id. Optional. */
    @JsonProperty("souvenir_choices")
    java.util.Map<String, String> souvenirChoices
) {}
