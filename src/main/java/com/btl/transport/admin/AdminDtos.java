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
        @JsonProperty("btl_code")          String btlCode,
        @JsonProperty("full_name")         String fullName,
        @JsonProperty("phone_whatsapp")    String phoneWhatsapp,
        String email,
        String state,
        HotelAdminDto hotel,
        @JsonProperty("shuttle_opt_in")    boolean shuttleOptIn,
        String status,
        @JsonProperty("needs_attention")   boolean needsAttention,
        @JsonProperty("attention_reason")  String attentionReason,
        @JsonProperty("registered_at")     String registeredAt,
        @JsonProperty("flight_arrival")    FlightAdminDto flightArrival,
        @JsonProperty("flight_departure")  FlightAdminDto flightDeparture,
        @JsonProperty("boarded_arrival")   boolean boardedArrival,
        @JsonProperty("boarded_departure") boolean boardedDeparture
    ) {}

    record VehicleAdminDto(
        @JsonProperty("vehicle_id") String vehicleId,
        String label,
        String type,
        int capacity
    ) {}

    record DriverAdminDto(
        @JsonProperty("driver_id")       String   driverId,
        String name,
        String phone,
        String whatsapp,
        String email,
        @JsonProperty("login_token")     String   loginToken,
        @JsonProperty("driver_code")     String   driverCode,
        @JsonProperty("available_dates") String[] availableDates,
        @JsonProperty("active_from")     String   activeFrom,
        @JsonProperty("created_at")      String   createdAt
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RunAdminResponse(
        Integer id,
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
        @JsonProperty("manifest_sent")       boolean manifestSent,
        @JsonProperty("completed_at")        String completedAt,
        @JsonProperty("updated_at")          String updatedAt,
        @JsonProperty("whatsapp_group_link") String whatsappGroupLink,
        @JsonProperty("boarded_count")       long boardedCount,
        @JsonProperty("hotel")               HotelAdminDto hotel
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
        @JsonProperty("admin_phone_1")              String adminPhone1,
        @JsonProperty("admin_phone_2")              String adminPhone2,
        @JsonProperty("coordinator_name_1")         String coordinatorName1,
        @JsonProperty("coordinator_name_2")         String coordinatorName2,
        @JsonProperty("whatsapp_link_1")            String whatsappLink1,
        @JsonProperty("whatsapp_link_2")            String whatsappLink2,
        @JsonProperty("reminder_before_mins")       Integer reminderBeforeMins,
        @JsonProperty("sms_registration")           String smsRegistration,
        @JsonProperty("sms_pickup_confirmed")       String smsPickupConfirmed,
        @JsonProperty("sms_shuttle_reminder")       String smsShuttleReminder,
        @JsonProperty("sms_delay_minor")            String smsDelayMinor,
        @JsonProperty("sms_delay_major")            String smsDelayMajor,
        @JsonProperty("sms_cancelled")              String smsCancelled,
        @JsonProperty("accommodation_name_1")       String accommodationName1,
        @JsonProperty("accommodation_phone_1")      String accommodationPhone1,
        @JsonProperty("accommodation_whatsapp_1")   String accommodationWhatsapp1,
        @JsonProperty("accommodation_name_2")       String accommodationName2,
        @JsonProperty("accommodation_phone_2")      String accommodationPhone2,
        @JsonProperty("accommodation_whatsapp_2")   String accommodationWhatsapp2
    ) {}

    record SuccessResponse(boolean success) {}

    // ── Room ──────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RoomOccupantDto(
        int slot,
        @JsonProperty("participant_id") Integer participantId,
        String name,
        String email,
        String phone,
        @JsonProperty("ticket_received") Boolean ticketReceived
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RoomAssignmentDto(
        int id,
        @JsonProperty("hotel_id")   Integer hotelId,
        @JsonProperty("hotel_name") String hotelName,
        @JsonProperty("room_label") String roomLabel,
        @JsonProperty("room_type")  String roomType,
        String gender,
        String notes,
        List<RoomOccupantDto> occupants
    ) {}

    record AccommodationContactDto(
        int id,
        @JsonProperty("program_id") String programId,
        String name,
        String phone,
        String whatsapp,
        @JsonProperty("sort_order") int sortOrder
    ) {}

    record ImportResultDto(
        @JsonProperty("created_rooms")       int createdRooms,
        @JsonProperty("updated_rooms")       int updatedRooms,
        @JsonProperty("moved_occupants")     int movedOccupants,
        @JsonProperty("new_occupants")       int newOccupants,
        @JsonProperty("unmatched_occupants") List<UnmatchedOccupant> unmatchedOccupants
    ) {}

    record UnmatchedOccupant(String name, String email, String phone) {}

    record CreateRoomRequest(
        @JsonProperty("hotel_id")   Integer hotelId,
        @JsonProperty("hotel_name") String hotelName,
        @JsonProperty("room_label") String roomLabel,
        @JsonProperty("room_type")  String roomType,
        String gender,
        String notes
    ) {}

    record UpdateRoomRequest(
        String gender,
        @JsonProperty("room_type") String roomType,
        @JsonProperty("room_label") String roomLabel,
        String notes
    ) {}

    record UpsertOccupantRequest(
        String name,
        String email,
        String phone
    ) {}

    record ToggleTicketRequest(boolean received) {}

    record ReallocRequest(
        @JsonProperty("from_room_id") Integer fromRoomId,
        @JsonProperty("from_slot")    Integer fromSlot,
        @JsonProperty("to_room_id")   Integer toRoomId
    ) {}

    record CreateAccomContactRequest(
        String name,
        String phone,
        String whatsapp
    ) {}

    record UpdateAccomContactRequest(
        String name,
        String phone,
        String whatsapp
    ) {}

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
        @JsonProperty("reg_title") String regTitle,
        @JsonProperty("reg_description") String regDescription,
        String timezone,
        Object hotels,
        @JsonProperty("morning_runs") Object morningRuns,
        @JsonProperty("evening_runs") Object eveningRuns,
        @JsonProperty("daily_schedules") Object dailySchedules,
        Object rules,
        @JsonProperty("roommate_visible") Boolean roommateVisible,
        @JsonProperty("show_upload_csv") Boolean showUploadCsv,
        @JsonProperty("show_download_template") Boolean showDownloadTemplate,
        @JsonProperty("show_fix_unlinked") Boolean showFixUnlinked,
        @JsonProperty("show_notify_participants") Boolean showNotifyParticipants,
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
        String timezone,
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
        @JsonProperty("reg_title") String regTitle,
        @JsonProperty("reg_description") String regDescription,
        String timezone,
        Object hotels,
        @JsonProperty("morning_runs") Object morningRuns,
        @JsonProperty("evening_runs") Object eveningRuns,
        @JsonProperty("daily_schedules") Object dailySchedules,
        Object rules
    ) {}
}
