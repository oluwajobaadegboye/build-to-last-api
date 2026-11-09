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
        // Early flights leave from the attendee's lodging before sessions begin; flights at or after the
        // cutoff let them attend the morning session and be picked up at the venue (CHURCH = venue).
        return departureTime.isBefore(cutoff) ? Leg4PickupFrom.HOTEL : Leg4PickupFrom.CHURCH;
    }
}
