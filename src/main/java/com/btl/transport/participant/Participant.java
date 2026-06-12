package com.btl.transport.participant;

import com.btl.transport.common.PostgresEnumType;
import com.btl.transport.common.enums.ParticipantStatus;
import com.btl.transport.hotel.Hotel;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "btl_code", nullable = false, unique = true)
    private String btlCode;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Type(PostgresEnumType.ParticipantStatusPgType.class)
    @Column(name = "status", columnDefinition = "participant_status")
    private ParticipantStatus status;

    @Column(name = "needs_attention")
    private Boolean needsAttention;

    @Column(name = "shuttle_opt_in")
    private Boolean shuttleOptIn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(name = "notes")
    private String notes;

    @Column(name = "attention_reason")
    private String attentionReason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "ticket_received")
    @Builder.Default
    private Boolean ticketReceived = false;

    @Column(name = "breakout_group")
    private Integer breakoutGroup;
}
