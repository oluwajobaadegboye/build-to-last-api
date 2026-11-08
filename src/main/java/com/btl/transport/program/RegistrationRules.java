package com.btl.transport.program;

import java.time.*;

/** Whether a program is taking registrations: paused by an admin, past its closing time, or full. */
public final class RegistrationRules {

    private RegistrationRules() {}

    public static final String OPEN = "open", PAUSED = "paused", CLOSED = "closed", FULL = "full";

    /** Closing instant from the program's local "YYYY-MM-DDTHH:mm" and timezone; null when not set/invalid. */
    public static OffsetDateTime closesAt(Program p) {
        String v = p.getRegistrationClosesAt();
        if (v == null || v.isBlank()) return null;
        try {
            ZoneId zone = ZoneId.of(p.getTimezone() != null ? p.getTimezone() : "America/New_York");
            return LocalDateTime.parse(v.trim()).atZone(zone).toOffsetDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    public static String state(Program p, long registered, OffsetDateTime now) {
        if (p == null) return OPEN;
        if (Boolean.FALSE.equals(p.getRegistrationOpen())) return PAUSED;
        OffsetDateTime close = closesAt(p);
        if (close != null && !now.isBefore(close)) return CLOSED;
        if (p.getMaxRegistrations() != null && p.getMaxRegistrations() > 0 && registered >= p.getMaxRegistrations()) return FULL;
        return OPEN;
    }

    public static String message(String state) {
        return switch (state) {
            case PAUSED -> "Registration for this program is currently paused.";
            case CLOSED -> "Registration for this program has closed.";
            case FULL -> "This program is full — registration has closed.";
            default -> "";
        };
    }
}
