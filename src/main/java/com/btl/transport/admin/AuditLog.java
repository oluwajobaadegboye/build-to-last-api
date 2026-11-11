package com.btl.transport.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** One admin change: who did what, in which program, when. */
@Entity
@Table(name = "admin_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "program_id") private String programId;
    @Column(name = "username") private String username;
    @Column(name = "action", nullable = false) private String action;
    @Column(name = "target") private String target;
    @Column(name = "method") private String method;
    @Column(name = "path") private String path;
    /** checkin, participants, payments, souvenirs, accommodation, transport, form, settings, team, other. Null on rows before V57. */
    @Column(name = "category") private String category;
    /** Name of the person or thing changed, captured before the change (so it survives deletes). */
    @Column(name = "target_name") private String targetName;
    /** Short extra detail, e.g. "Paid" or "email, phone". */
    @Column(name = "detail") private String detail;
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
