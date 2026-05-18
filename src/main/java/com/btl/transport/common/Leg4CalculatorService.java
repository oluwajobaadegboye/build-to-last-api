package com.btl.transport.common;

import com.btl.transport.common.enums.Leg4PickupFrom;
import com.btl.transport.hotel.Hotel;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

@Service
public class Leg4CalculatorService {

    public Leg4PickupFrom calculate(LocalTime departureTime, Hotel hotel, LocalTime defaultCutoff) {
        LocalTime cutoff = (hotel != null && hotel.getLeg4CutoffTime() != null)
            ? hotel.getLeg4CutoffTimeAsLocalTime()
            : defaultCutoff;
        if (cutoff == null) cutoff = LocalTime.of(10, 30);
        return departureTime.isBefore(cutoff) ? Leg4PickupFrom.CHURCH : Leg4PickupFrom.HOTEL;
    }
}
