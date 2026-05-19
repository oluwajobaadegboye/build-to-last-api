package com.btl.transport.vehicle;

import com.btl.transport.common.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "type", columnDefinition = "vehicle_type")
    private VehicleType type;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
