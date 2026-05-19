package com.btl.transport.participant;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FlightPairsValidator implements ConstraintValidator<ValidFlightPairs, RegisterRequest> {

    @Override
    public boolean isValid(RegisterRequest req, ConstraintValidatorContext ctx) {
        if (req == null) return true;

        boolean valid = true;

        boolean hasArrivalAirline = notBlank(req.arrivalAirline());
        boolean hasArrivalNumber  = notBlank(req.arrivalFlightNumber());
        if (hasArrivalAirline != hasArrivalNumber) {
            addViolation(ctx, "Arrival airline and flight number must both be provided",
                hasArrivalAirline ? "arrival_flight_number" : "arrival_airline");
            valid = false;
        }

        boolean hasDepartAirline = notBlank(req.departureAirline());
        boolean hasDepartNumber  = notBlank(req.departureFlightNumber());
        if (hasDepartAirline != hasDepartNumber) {
            addViolation(ctx, "Departure airline and flight number must both be provided",
                hasDepartAirline ? "departure_flight_number" : "departure_airline");
            valid = false;
        }

        return valid;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private void addViolation(ConstraintValidatorContext ctx, String message, String field) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message)
           .addPropertyNode(field)
           .addConstraintViolation();
    }
}
