package com.btl.transport.hotel;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "hotels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "hotel_name")
    private String hotelName;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "drive_to_church_mins")
    private Integer driveToChurchMins;

    @Column(name = "drive_to_airport_mins")
    private Integer driveToAirportMins;

    @Column(name = "leg4_cutoff_time")
    private String leg4CutoffTime;

    @Column(name = "shuttle_stop_order")
    private Integer shuttleStopOrder;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "external_ref")
    private String externalRef;

    private static final DateTimeFormatter AM_PM_FORMAT =
        DateTimeFormatter.ofPattern("hh:mm a");

    // Handles both "HH:MM" (spec format) and "HH:MM AM"/"HH:MM PM" (legacy Supabase rows)
    public LocalTime getLeg4CutoffTimeAsLocalTime() {
        if (leg4CutoffTime == null) return null;
        try {
            return LocalTime.parse(leg4CutoffTime);
        } catch (Exception e) {
            return LocalTime.parse(leg4CutoffTime.trim().toUpperCase(), AM_PM_FORMAT);
        }
    }
}
