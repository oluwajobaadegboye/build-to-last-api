package com.btl.transport.program;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Parsing helpers for program fields stored as display text.
 * Dates are saved either as ISO ("2027-03-01") or as "Mar 1, 2027"; shuttle times as "7:30 AM".
 */
public final class ProgramDates {

    private ProgramDates() {}

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
    );

    private static final DateTimeFormatter TIME_12H = DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    /** Parsed date, or null when blank/unrecognised. */
    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s, f); } catch (DateTimeParseException ignored) { /* try next */ }
        }
        return null;
    }

    /** "7:30 AM" / "19:30" → 07:30 / 19:30; null when unrecognised. */
    public static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toUpperCase(Locale.US);
        try { return LocalTime.parse(s, TIME_12H); } catch (DateTimeParseException ignored) { /* try 24h */ }
        try { return LocalTime.parse(s); } catch (DateTimeParseException ignored) { return null; }
    }

    /** The program's zone, falling back to America/New_York when missing or invalid. */
    public static ZoneId zone(Program p) {
        try {
            return p.getTimezone() != null && !p.getTimezone().isBlank()
                ? ZoneId.of(p.getTimezone()) : ZoneId.of("America/New_York");
        } catch (Exception e) {
            return ZoneId.of("America/New_York");
        }
    }
}
