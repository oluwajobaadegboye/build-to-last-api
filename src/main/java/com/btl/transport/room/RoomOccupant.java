package com.btl.transport.room;

import com.btl.transport.participant.Participant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "room_occupants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomOccupant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    @ToString.Exclude
    private RoomAssignment room;

    private Short slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    @ToString.Exclude
    private Participant participant;

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    @Column(name = "ticket_received", nullable = false)
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonProperty("ticket_received")
    private Boolean ticketReceived = false;
}
