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

    private static final java.util.regex.Pattern CODE_IN_PARENS = java.util.regex.Pattern.compile("\\(([A-Za-z]{3})\\)");
    private static final java.util.regex.Pattern CODE_AFTER_DOT = java.util.regex.Pattern.compile("·\\s*([A-Za-z]{3})\\s*$");

    /**
     * IATA code from the program's airport text, as entered at creation:
     * "Denver International (DEN)" → DEN, "Atlanta Hartsfield-Jackson · ATL" → ATL, "ORD" → ORD.
     * Null when the program has no airport or no recognisable code.
     */
    public static String airportCode(Program p) {
        String a = p == null || p.getAirport() == null ? "" : p.getAirport().trim();
        if (a.isEmpty() || a.equals("—")) return null;
        java.util.regex.Matcher m = CODE_IN_PARENS.matcher(a);
        if (m.find()) return m.group(1).toUpperCase(Locale.ROOT);
        m = CODE_AFTER_DOT.matcher(a);
        if (m.find()) return m.group(1).toUpperCase(Locale.ROOT);
        return a.matches("[A-Za-z]{3}") ? a.toUpperCase(Locale.ROOT) : null;
    }

    /** Label for the program's airport in trips ("Denver International (DEN)"), or "Airport". */
    public static String airportLabel(Program p) {
        String a = p == null || p.getAirport() == null ? "" : p.getAirport().trim();
        return a.isEmpty() || a.equals("—") ? "Airport" : a;
    }

    /**
     * Flights are tracked automatically for each program from the day before it starts until the end of
     * the day after it ends (in the program's time zone) — only while it offers airport pickups.
     * Returns {start, end}, or null when the program has no dates or no airport pickups.
     */
    public static java.time.OffsetDateTime[] trackingWindow(Program p) {
        if (p == null || Boolean.FALSE.equals(p.getAirportPickupsEnabled())) return null;
        LocalDate start = parseDate(p.getStartDate());
        if (start == null) return null;
        LocalDate end = parseDate(p.getEndDate());
        if (end == null || end.isBefore(start)) end = start;
        ZoneId zone = zone(p);
        return new java.time.OffsetDateTime[] {
            start.minusDays(1).atStartOfDay(zone).toOffsetDateTime(),
            end.plusDays(2).atStartOfDay(zone).toOffsetDateTime(),   // exclusive: end of the day after
        };
    }

    /** Whether an arrival on {@code flightDate} should be tracked now for this program. */
    public static boolean isTrackable(Program p, LocalDate flightDate, java.time.OffsetDateTime now) {
        java.time.OffsetDateTime[] w = trackingWindow(p);
        if (w == null || flightDate == null || !now.isBefore(w[1])) return false;
        LocalDate first = w[0].atZoneSameInstant(zone(p)).toLocalDate();
        LocalDate last = w[1].atZoneSameInstant(zone(p)).toLocalDate().minusDays(1);
        return !flightDate.isBefore(first) && !flightDate.isAfter(last);
    }

    /** Cutoff for the program's "leave from the venue" departure rule, or null when the rule is off. */
    public static LocalTime venueDepartureCutoff(Program p) {
        if (p == null || !Boolean.TRUE.equals(p.getVenueDepartureRule())) return null;
        LocalTime t = parseTime(p.getVenueDepartureCutoff());
        return t != null ? t : LocalTime.of(10, 30);
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
