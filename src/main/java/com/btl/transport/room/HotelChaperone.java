package com.btl.transport.room;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** A registered attendee who looks after everyone (or one gender) staying at a hostel/hotel. */
@Entity
@Table(name = "hotel_chaperones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelChaperone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    /** Matched to rooms and attendees by name (case-insensitive), as rooms are. */
    @Column(name = "hotel_name", nullable = false)
    private String hotelName;

    @Column(name = "participant_id", nullable = false)
    private Integer participantId;

    /** "male" / "female" = shown only to attendees of that gender; null = everyone. */
    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
