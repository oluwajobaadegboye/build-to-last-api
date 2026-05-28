package com.btl.transport.program;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "programs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ini")
    private String ini;

    @Column(name = "type")
    private String type;

    @Column(name = "start_date")
    private String startDate;

    @Column(name = "end_date")
    private String endDate;

    @Column(name = "phase")
    private String phase;

    @Column(name = "venue")
    private String venue;

    @Column(name = "venue_addr")
    private String venueAddr;

    @Column(name = "airport")
    private String airport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hotels", columnDefinition = "jsonb")
    private String hotels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "morning_runs", columnDefinition = "jsonb")
    private String morningRuns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evening_runs", columnDefinition = "jsonb")
    private String eveningRuns;

    @Column(name = "rule_window")
    private String ruleWindow;

    @Column(name = "rule_cap")
    private String ruleCap;

    @Column(name = "rule_buffer")
    private String ruleBuffer;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "hotel_selection_enabled")
    private Boolean hotelSelectionEnabled;

    @Column(name = "registration_open")
    private Boolean registrationOpen;

    @Column(name = "reg_title", length = 500)
    private String regTitle;

    @Column(name = "reg_description", columnDefinition = "text")
    private String regDescription;

    @Column(name = "timezone")
    private String timezone;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
