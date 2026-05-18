package com.btl.transport.notification;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "airport_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "grouping_window_mins")
    private Integer groupingWindowMins;

    // Stored as text "2026-06-12T12:00:00" in DB — parsed at runtime
    @Column(name = "polling_end_datetime")
    private String pollingEndDatetime;

    @Column(name = "minor_delay_threshold_hrs")
    private BigDecimal minorDelayThresholdHrs;

    @Column(name = "major_delay_threshold_hrs")
    private BigDecimal majorDelayThresholdHrs;

    @Column(name = "airport_code")
    private String airportCode;

    @Column(name = "leg4_default_cutoff_time")
    private String leg4DefaultCutoffTime;

    @Column(name = "polling_start_date")
    private String pollingStartDate;

    @Column(name = "polling_interval_mins")
    private Integer pollingIntervalMins;

    public OffsetDateTime getPollingEndAsOffsetDateTime() {
        return pollingEndDatetime != null
            ? OffsetDateTime.parse(pollingEndDatetime + "-05:00")
            : null;
    }

    public LocalTime getLeg4DefaultCutoffAsLocalTime() {
        return leg4DefaultCutoffTime != null ? LocalTime.parse(leg4DefaultCutoffTime) : null;
    }

    public LocalDate getPollingStartAsLocalDate() {
        return pollingStartDate != null ? LocalDate.parse(pollingStartDate) : null;
    }
}
