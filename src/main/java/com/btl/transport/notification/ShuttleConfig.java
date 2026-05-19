package com.btl.transport.notification;

import com.btl.transport.common.PostgresEnumType;
import com.btl.transport.common.enums.ConferenceDay;
import com.btl.transport.common.enums.Direction;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import lombok.*;

@Entity
@Table(name = "shuttle_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShuttleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "config_label")
    private String configLabel;

    @Type(PostgresEnumType.ConferenceDayPgType.class)
    @Column(name = "conference_day", columnDefinition = "conference_day")
    private ConferenceDay conferenceDay;

    @Type(PostgresEnumType.DirectionPgType.class)
    @Column(name = "direction", columnDefinition = "direction")
    private Direction direction;

    // "HH:MM" stored as text
    @Column(name = "window_start")
    private String windowStart;

    @Column(name = "window_end")
    private String windowEnd;

    @Column(name = "interval_mins")
    private Integer intervalMins;

    @Column(name = "max_vehicles")
    private Integer maxVehicles;

    @Column(name = "seats_per_vehicle")
    private Integer seatsPerVehicle;
}
