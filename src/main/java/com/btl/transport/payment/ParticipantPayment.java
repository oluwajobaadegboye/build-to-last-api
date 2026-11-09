package com.btl.transport.payment;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One payment received from a participant (payments happen outside the app; admins record them). */
@Entity
@Table(name = "participant_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "participant_id", nullable = false)
    private Integer participantId;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "note")
    private String note;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private OffsetDateTime recordedAt = OffsetDateTime.now();
}
