package com.btl.transport.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "admin_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "role", nullable = false)
    @Builder.Default
    private String role = "FULL";
}
