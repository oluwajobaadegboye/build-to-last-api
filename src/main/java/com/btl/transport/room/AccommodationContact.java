package com.btl.transport.room;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "accommodation_contacts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccommodationContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "program_id", nullable = false)
    private String programId;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String whatsapp;

    @Column(name = "sort_order")
    private Short sortOrder;
}
