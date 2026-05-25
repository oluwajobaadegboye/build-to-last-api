package com.btl.transport.driver;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "drivers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "email")
    private String email;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "available_dates", columnDefinition = "date[]")
    private String[] availableDates;

    @Column(name = "active_from")
    private LocalDate activeFrom;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
