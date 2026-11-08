package com.btl.transport.souvenir;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Something a program gives attendees (T-shirt, cap, …), optionally with options such as sizes. */
@Entity
@Table(name = "souvenir_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SouvenirItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @Column(name = "name", nullable = false)
    private String name;

    /** JSON array of options (e.g. sizes); null/empty means one size. */
    @Column(name = "options")
    private String options;

    /** Ask attendees to choose an option when they register (only for items with options). */
    @Column(name = "ask_at_registration", nullable = false)
    @Builder.Default
    private Boolean askAtRegistration = false;

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
