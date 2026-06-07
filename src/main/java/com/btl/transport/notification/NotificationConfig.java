package com.btl.transport.notification;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "admin_name_1")
    private String adminName1;

    @Column(name = "admin_phone_1")
    private String adminPhone1;

    @Column(name = "admin_whatsapp_1")
    private String adminWhatsapp1;

    @Column(name = "admin_name_2")
    private String adminName2;

    @Column(name = "admin_phone_2")
    private String adminPhone2;

    @Column(name = "admin_whatsapp_2")
    private String adminWhatsapp2;

    @Column(name = "template_registration")
    private String templateRegistration;

    @Column(name = "template_confirmation")
    private String templateConfirmation;

    @Column(name = "template_pickup_reminder")
    private String templatePickupReminder;

    @Column(name = "template_driver_assigned")
    private String templateDriverAssigned;

    @Column(name = "template_delay_minor")
    private String templateDelayMinor;

    @Column(name = "template_delay_major")
    private String templateDelayMajor;

    @Column(name = "template_cancellation")
    private String templateCancellation;

    @Column(name = "template_shuttle_reminder")
    private String templateShuttleReminder;

    @Column(name = "template_help_forward")
    private String templateHelpForward;

    @Column(name = "template_reschedule")
    private String templateReschedule;

    @Column(name = "reminder_before_mins")
    private Integer reminderBeforeMins;

    @Column(name = "program_id")
    private String programId;

    @Column(name = "accommodation_name_1")
    private String accommodationName1;

    @Column(name = "accommodation_phone_1")
    private String accommodationPhone1;

    @Column(name = "accommodation_whatsapp_1")
    private String accommodationWhatsapp1;

    @Column(name = "accommodation_name_2")
    private String accommodationName2;

    @Column(name = "accommodation_phone_2")
    private String accommodationPhone2;

    @Column(name = "accommodation_whatsapp_2")
    private String accommodationWhatsapp2;
}
