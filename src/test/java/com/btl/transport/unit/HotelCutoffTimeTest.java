package com.btl.transport.unit;

import com.btl.transport.hotel.Hotel;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class HotelCutoffTimeTest {

    @Test
    void parses_hhmm_format() {
        Hotel h = Hotel.builder().leg4CutoffTime("10:08").build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isEqualTo(LocalTime.of(10, 8));
    }

    @Test
    void parses_hhmm_am_format() {
        Hotel h = Hotel.builder().leg4CutoffTime("10:08 AM").build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isEqualTo(LocalTime.of(10, 8));
    }

    @Test
    void parses_hhmm_pm_format() {
        Hotel h = Hotel.builder().leg4CutoffTime("01:30 PM").build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isEqualTo(LocalTime.of(13, 30));
    }

    @Test
    void returns_null_for_null_value() {
        Hotel h = Hotel.builder().build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isNull();
    }

    @Test
    void hampton_inn_cutoff() {
        Hotel h = Hotel.builder().leg4CutoffTime("10:08").build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isEqualTo(LocalTime.of(10, 8));
    }

    @Test
    void holiday_inn_cutoff() {
        Hotel h = Hotel.builder().leg4CutoffTime("10:02").build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isEqualTo(LocalTime.of(10, 2));
    }

    @Test
    void la_quinta_cutoff() {
        Hotel h = Hotel.builder().leg4CutoffTime("10:12").build();
        assertThat(h.getLeg4CutoffTimeAsLocalTime()).isEqualTo(LocalTime.of(10, 12));
    }
}
