package com.btl.transport.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class AdminDtos {

    private AdminDtos() {}

    record AdminStatsResponse(
        @JsonProperty("total_participants") long totalParticipants,
        @JsonProperty("todays_runs")        long todaysRuns,
        @JsonProperty("active_alerts")      long activeAlerts,
        @JsonProperty("flights_monitored")  long flightsMonitored,
        @JsonProperty("next_run_time")      String nextRunTime
    ) {}

    record HotelAdminDto(
        @JsonProperty("hotel_id")              String hotelId,
        @JsonProperty("hotel_name")            String hotelName,
        @JsonProperty("pickup_address")        String pickupAddress,
        @JsonProperty("drive_to_church_mins")  int driveToChurchMins,
        @JsonProperty("drive_to_airport_mins") int driveToAirportMins,
        @JsonProperty("leg4_cutoff_time")      String leg4CutoffTime,
        @JsonProperty("shuttle_stop_order")    int shuttleStopOrder
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FlightAdminDto(
        @JsonProperty("flight_id")          String flightId,
        @JsonProperty("btl_code")           String btlCode,
        String direction,
        String airline,
        @JsonProperty("flight_number")      String flightNumber,
        @JsonProperty("submitted_datetime") String submittedDatetime,
        @JsonProperty("live_eta")           String liveEta,
        @JsonProperty("flight_status")      String flightStatus,
        @JsonProperty("delay_mins")         int delayMins,
        @JsonProperty("airport_code")       String airportCode,
        @JsonProperty("polling_active")     boolean pollingActive,
        @JsonProperty("leg4_pickup_from")   String leg4PickupFrom
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ParticipantAdminResponse(
        @JsonProperty("btl_code")         String btlCode,
        @JsonProperty("full_name")        String fullName,
        @JsonProperty("phone_whatsapp")   String phoneWhatsapp,
        String email,
        String state,
        HotelAdminDto hotel,
        @JsonProperty("shuttle_opt_in")   boolean shuttleOptIn,
        String status,
        @JsonProperty("needs_attention")  boolean needsAttention,
        @JsonProperty("attention_reason") String attentionReason,
        @JsonProperty("registered_at")    String registeredAt,
        @JsonProperty("flight_arrival")   FlightAdminDto flightArrival,
        @JsonProperty("flight_departure") FlightAdminDto flightDeparture
    ) {}

    record VehicleAdminDto(
        @JsonProperty("vehicle_id") String vehicleId,
        String label,
        String type,
        int capacity
    ) {}

    record DriverAdminDto(
        @JsonProperty("driver_id") String driverId,
        String name,
        String phone,
        String whatsapp,
        String email
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RunAdminResponse(
        @JsonProperty("run_id")           String runId,
        @JsonProperty("run_type")         String runType,
        String direction,
        @JsonProperty("conference_day")   String conferenceDay,
        @JsonProperty("conference_date")  String conferenceDate,
        @JsonProperty("depart_time")      String departTime,
        @JsonProperty("pickup_location")  String pickupLocation,
        @JsonProperty("dropoff_location") String dropoffLocation,
        @JsonProperty("seats_total")      int seatsTotal,
        @JsonProperty("seats_filled")     int seatsFilled,
        @JsonProperty("seats_left")       int seatsLeft,
        String status,
        VehicleAdminDto vehicle,
        DriverAdminDto driver,
        List<ParticipantAdminResponse> participants,
        @JsonProperty("manifest_sent")    boolean manifestSent,
        @JsonProperty("completed_at")     String completedAt,
        @JsonProperty("updated_at")       String updatedAt
    ) {}

    record ManifestResponse(
        DriverAdminDto driver,
        List<RunAdminResponse> runs
    ) {}

    record ManifestSendResponse(
        boolean success,
        boolean sent,
        String reason
    ) {}

    record ShuttleConfigResponse(
        @JsonProperty("config_id")         String configId,
        @JsonProperty("conference_day")    String conferenceDay,
        String direction,
        @JsonProperty("window_start")      String windowStart,
        @JsonProperty("window_end")        String windowEnd,
        @JsonProperty("interval_mins")     Integer intervalMins,
        @JsonProperty("max_vehicles")      Integer maxVehicles,
        @JsonProperty("seats_per_vehicle") Integer seatsPerVehicle,
        boolean active
    ) {}

    record AirportConfigResponse(
        @JsonProperty("leg4_cutoff_default")        String leg4CutoffDefault,
        @JsonProperty("per_hotel_cutoff")           boolean perHotelCutoff,
        @JsonProperty("polling_start")              String pollingStart,
        @JsonProperty("polling_end")                String pollingEnd,
        @JsonProperty("grouping_window_mins")       Integer groupingWindowMins,
        @JsonProperty("advance_pickup_buffer_mins") Integer advancePickupBufferMins
    ) {}

    record NotificationConfigResponse(
        @JsonProperty("admin_phone_1")        String adminPhone1,
        @JsonProperty("admin_phone_2")        String adminPhone2,
        @JsonProperty("coordinator_name_1")   String coordinatorName1,
        @JsonProperty("coordinator_name_2")   String coordinatorName2,
        @JsonProperty("whatsapp_link_1")      String whatsappLink1,
        @JsonProperty("whatsapp_link_2")      String whatsappLink2,
        @JsonProperty("reminder_before_mins") Integer reminderBeforeMins,
        @JsonProperty("sms_registration")     String smsRegistration,
        @JsonProperty("sms_pickup_confirmed") String smsPickupConfirmed,
        @JsonProperty("sms_shuttle_reminder") String smsShuttleReminder,
        @JsonProperty("sms_delay_minor")      String smsDelayMinor,
        @JsonProperty("sms_delay_major")      String smsDelayMajor,
        @JsonProperty("sms_cancelled")        String smsCancelled
    ) {}

    record SuccessResponse(boolean success) {}

    record ProgramResponse(
        String id,
        String name,
        String ini,
        String type,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date")   String endDate,
        String phase,
        String venue,
        @JsonProperty("venue_addr") String venueAddr,
        String airport,
        String city,
        String state,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("hotel_selection_enabled") Boolean hotelSelectionEnabled,
        @JsonProperty("registration_open") Boolean registrationOpen,
        Object hotels,
        @JsonProperty("morning_runs") Object morningRuns,
        @JsonProperty("evening_runs") Object eveningRuns,
        Object rules,
        @JsonProperty("created_at") String createdAt
    ) {}

    record CreateProgramRequest(
        String id,
        String name,
        String ini,
        String type,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date")   String endDate,
        String venue,
        @JsonProperty("venue_addr") String venueAddr,
        String airport,
        String city,
        String state,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("hotel_selection_enabled") Boolean hotelSelectionEnabled,
        Object hotels,
        @JsonProperty("morning_runs") Object morningRuns,
        @JsonProperty("evening_runs") Object eveningRuns,
        Object rules
    ) {}

    record UpdateProgramRequest(
        String name,
        String type,
        @JsonProperty("start_date") String startDate,
        @JsonProperty("end_date")   String endDate,
        String phase,
        String venue,
        @JsonProperty("venue_addr") String venueAddr,
        String airport,
        String city,
        String state,
        @JsonProperty("logo_url") String logoUrl,
        @JsonProperty("hotel_selection_enabled") Boolean hotelSelectionEnabled,
        @JsonProperty("registration_open") Boolean registrationOpen,
        Object hotels,
        @JsonProperty("morning_runs") Object morningRuns,
        @JsonProperty("evening_runs") Object eveningRuns,
        Object rules
    ) {}
}
