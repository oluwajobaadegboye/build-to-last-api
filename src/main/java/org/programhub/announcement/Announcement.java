package org.programhub.announcement;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** An email from the organisers to everyone (or a filtered group) in a program; also shown on attendees' pages. */
@Entity
@Table(name = "announcements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Announcement {

    public static final String SENDING = "sending";
    public static final String SENT = "sent";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /** The filter used, as JSON (see {@link AnnouncementService.Audience}). */
    @Column(name = "audience", columnDefinition = "TEXT")
    private String audience;

    @Column(name = "audience_label", length = 500)
    private String audienceLabel;

    @Column(name = "show_on_status", nullable = false)
    @Builder.Default
    private Boolean showOnStatus = true;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "recipients", nullable = false)
    @Builder.Default
    private Integer recipients = 0;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    @Column(name = "sent_by")
    private String sentBy;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
