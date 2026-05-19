package com.btl.transport.participant;

import com.btl.transport.common.enums.ParticipantStatus;
import com.btl.transport.hotel.Hotel;
import jakarta.persistence.*;
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
}
