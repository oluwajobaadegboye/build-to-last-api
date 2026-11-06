package com.btl.transport.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Membership of an admin user in a program (one user can belong to many programs). */
@Entity
@Table(name = "program_admins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "admin_user_id", nullable = false)
    private Integer adminUserId;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @Column(name = "role", nullable = false)
    @Builder.Default
    private String role = "FULL";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
