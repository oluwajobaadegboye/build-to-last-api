package org.programhub.room;

import org.programhub.participant.Participant;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    @JsonProperty("ticket_received")
    private Boolean ticketReceived = false;
}
