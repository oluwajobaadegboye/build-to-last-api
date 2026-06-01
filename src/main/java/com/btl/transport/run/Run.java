package com.btl.transport.run;

import com.btl.transport.common.PostgresEnumType;
import com.btl.transport.common.enums.*;
import com.btl.transport.driver.Driver;
import com.btl.transport.vehicle.Vehicle;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "run_id")
    private String runId;

    @Type(PostgresEnumType.RunTypePgType.class)
    @Column(name = "run_type", columnDefinition = "run_type")
    private RunType runType;

    @Type(PostgresEnumType.DirectionPgType.class)
    @Column(name = "direction", columnDefinition = "direction")
    private Direction direction;

    @Type(PostgresEnumType.ConferenceDayPgType.class)
    @Column(name = "conference_day", columnDefinition = "conference_day")
    private ConferenceDay conferenceDay;

    @Column(name = "conference_date")
    private LocalDate conferenceDate;

    // "HH:MM" text
    @Column(name = "depart_time")
    private String departTime;

    @Column(name = "seats_total")
    private Integer seatsTotal;

    @Column(name = "seats_filled")
    private Integer seatsFilled;

    @Type(PostgresEnumType.RunStatusPgType.class)
    @Column(name = "status", columnDefinition = "run_status_enum")
    private RunStatusEnum status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(name = "pickup_location")
    private String pickupLocation;

    @Column(name = "dropoff_location")
    private String dropoffLocation;

    @Column(name = "manifest_sent")
    private Boolean manifestSent;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "whatsapp_group_link")
    private String whatsappGroupLink;

    public LocalTime getDepartTimeAsLocalTime() {
        return departTime != null ? LocalTime.parse(departTime) : null;
    }

    public int getSeatsRemaining() {
        int total = seatsTotal != null ? seatsTotal : 0;
        int filled = seatsFilled != null ? seatsFilled : 0;
        return Math.max(0, total - filled);
    }
}
