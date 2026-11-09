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

    @Column(name = "email")
    private String email;

    /** False only for self sign-ups that haven't clicked their verification link yet. */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = true;

    @Column(name = "verification_token")
    private String verificationToken;

    @Column(name = "verification_sent_at")
    private OffsetDateTime verificationSentAt;
}
