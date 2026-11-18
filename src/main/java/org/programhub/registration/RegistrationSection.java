package org.programhub.registration;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** An extra step of registration questions. Questions without a section sit in the first step. */
@Entity
@Table(name = "registration_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
