package com.btl.transport.unit;

import com.btl.transport.common.Leg4CalculatorService;
import com.btl.transport.common.enums.Leg4PickupFrom;
import com.btl.transport.hotel.Hotel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class Leg4CalculatorServiceTest {

    private Leg4CalculatorService service;
    private final LocalTime defaultCutoff = LocalTime.of(10, 30);

    @BeforeEach
    void setup() {
        service = new Leg4CalculatorService();
    }

    @Test
    void departure_before_cutoff_picks_up_from_lodging() {
        assertThat(service.calculate(LocalTime.of(9, 0), null, defaultCutoff))
            .isEqualTo(Leg4PickupFrom.HOTEL);
    }

    @Test
    void departure_after_cutoff_picks_up_from_venue() {
        assertThat(service.calculate(LocalTime.of(14, 0), null, defaultCutoff))
            .isEqualTo(Leg4PickupFrom.CHURCH);
    }

    @Test
    void departure_at_exact_cutoff_picks_up_from_venue() {
        assertThat(service.calculate(LocalTime.of(10, 30), null, defaultCutoff))
            .isEqualTo(Leg4PickupFrom.CHURCH);
    }

    @Test
    void hotel_custom_cutoff_overrides_default() {
        Hotel hotel = Hotel.builder().leg4CutoffTime("12:00").build();
        // Departure at 11:00 is before the 12:00 hotel cutoff → leaves from the hotel
        assertThat(service.calculate(LocalTime.of(11, 0), hotel, defaultCutoff))
            .isEqualTo(Leg4PickupFrom.HOTEL);
    }

    @Test
    void hotel_custom_cutoff_before_departure_gives_venue() {
        Hotel hotel = Hotel.builder().leg4CutoffTime("08:00").build();
        // Departure at 10:00 is after the 08:00 hotel cutoff → picked up at the venue
        assertThat(service.calculate(LocalTime.of(10, 0), hotel, defaultCutoff))
            .isEqualTo(Leg4PickupFrom.CHURCH);
    }

    @Test
    void null_hotel_falls_back_to_default_cutoff() {
        assertThat(service.calculate(LocalTime.of(9, 0), null, defaultCutoff))
            .isEqualTo(Leg4PickupFrom.HOTEL);
    }
}
