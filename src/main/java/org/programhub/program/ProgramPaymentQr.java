package org.programhub.program;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** A program's payment QR code image (e.g. Zelle), kept apart from Program so it's only loaded when needed. */
@Entity
@Table(name = "program_payment_qr")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramPaymentQr {

    @Id
    @Column(name = "program_id")
    private String programId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
