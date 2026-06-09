package com.btl.transport.room;

import com.btl.transport.hotel.Hotel;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "room_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    @ToString.Exclude
    private Hotel hotel;

    @Column(name = "hotel_name", nullable = false)
    private String hotelName;

    @Column(name = "room_label", nullable = false)
    private String roomLabel;

    @Column(name = "room_type", nullable = false)
    private String roomType;

    private String gender;
    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("slot ASC")
    @Builder.Default
    @ToString.Exclude
    private List<RoomOccupant> occupants = new ArrayList<>();
}
