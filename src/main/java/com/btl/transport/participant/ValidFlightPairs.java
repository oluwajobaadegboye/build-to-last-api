package com.btl.transport.participant;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = FlightPairsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFlightPairs {
    String message() default "Flight airline and number must both be provided together";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
