package com.btl.transport.souvenir;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** A participant's chosen option for an item and whether they've received it. */
@Entity
@Table(name = "participant_souvenirs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantSouvenir {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "participant_id", nullable = false)
    private Integer participantId;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "choice")
    private String choice;

    @Column(name = "received", nullable = false)
    @Builder.Default
    private Boolean received = false;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "received_by")
    private String receivedBy;
}
