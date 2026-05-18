package com.btl.transport.unit;

import com.btl.transport.notification.AirportConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DelayClassificationTest {

    private AirportConfig config() {
        return AirportConfig.builder()
            .minorDelayThresholdHrs(BigDecimal.valueOf(2.0))
            .majorDelayThresholdHrs(BigDecimal.valueOf(4.0))
            .build();
    }

    private String classify(int delayMins, AirportConfig config) {
        double delayHrs = delayMins / 60.0;
        if (delayHrs < config.getMinorDelayThresholdHrs().doubleValue()) return "MINOR";
        if (delayHrs < config.getMajorDelayThresholdHrs().doubleValue()) return "MAJOR";
        return "CRITICAL";
    }

    @Test
    void no_delay_is_minor() {
        assertThat(classify(0, config())).isEqualTo("MINOR");
    }

    @Test
    void delay_under_2hrs_is_minor() {
        assertThat(classify(90, config())).isEqualTo("MINOR");
    }

    @Test
    void delay_exactly_2hrs_is_major() {
        assertThat(classify(120, config())).isEqualTo("MAJOR");
    }

    @Test
    void delay_3hrs_is_major() {
        assertThat(classify(180, config())).isEqualTo("MAJOR");
    }

    @Test
    void delay_4hrs_or_more_is_critical() {
        assertThat(classify(240, config())).isEqualTo("CRITICAL");
        assertThat(classify(300, config())).isEqualTo("CRITICAL");
    }
}
