package com.btl.transport.registration;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** A custom question a program asks attendees at registration. */
@Entity
@Table(name = "registration_fields")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "type", nullable = false)
    private String type;

    /** JSON array of option strings (select / multiselect only). */
    @Column(name = "options")
    private String options;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Column(name = "help_text")
    private String helpText;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
