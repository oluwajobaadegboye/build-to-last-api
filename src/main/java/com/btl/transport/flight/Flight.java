package com.btl.transport.flight;

import com.btl.transport.common.enums.Direction;
import com.btl.transport.common.enums.FlightStatusType;
import com.btl.transport.common.enums.Leg4PickupFrom;
import com.btl.transport.participant.Participant;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "flights")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "flight_id")
    private String flightId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Column(name = "airline")
    private String airline;

    @Column(name = "flight_number")
    private String flightNumber;

    @Column(name = "direction", columnDefinition = "direction")
    private Direction direction;

    @Column(name = "submitted_datetime")
    private OffsetDateTime submittedDatetime;

    @Column(name = "live_eta")
    private OffsetDateTime liveEta;

    @Column(name = "flight_status", columnDefinition = "flight_status_type")
    private FlightStatusType flightStatus;

    @Column(name = "polling_active")
    private Boolean pollingActive;

    @Column(name = "leg4_pickup_from", columnDefinition = "leg4_pickup_from_type")
    private Leg4PickupFrom leg4PickupFrom;

    @Column(name = "delay_mins")
    private Integer delayMins;

    @Column(name = "last_polled_at")
    private OffsetDateTime lastPolledAt;

    @Column(name = "airport_code")
    private String airportCode;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
