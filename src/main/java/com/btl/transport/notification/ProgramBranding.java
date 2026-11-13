package com.btl.transport.notification;

import com.btl.transport.program.Program;
import com.btl.transport.program.ProgramDates;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything an outgoing message says about "the program", taken from the program's own details
 * (name, dates, venue, city, airport, services, lodging type). Nothing here is specific to one program;
 * a message with no program falls back to neutral wording.
 */
public final class ProgramBranding {

    private final String name;          // "" when unknown
    private final String dates;         // "July 5–9, 2027" or ""
    private final String venueLine;     // "Camp Hall, 1 Pine Rd, Denver, CO" or ""
    private final String place;         // "Camp Hall · Denver, CO" or ""
    private final String airport;       // "" when pickups are off or no airport set
    private final String lodging;       // "hotel", "cabin", …
    private final String covers;        // "your registration details, accommodation, airport pickups and daily shuttle schedule"

    private ProgramBranding(Program p) {
        this.name = p != null && notBlank(p.getName()) ? p.getName().trim() : "";
        this.dates = p != null ? dateRange(p.getStartDate(), p.getEndDate()) : "";
        String cityState = p == null ? "" : join(", ", p.getCity(), p.getState());
        this.venueLine = p == null ? "" : join(", ", p.getVenue(), p.getVenueAddr(), cityState);
        this.place = p == null ? "" : join(" · ", p.getVenue(), cityState);
        boolean airportOn = p == null || !Boolean.FALSE.equals(p.getAirportPickupsEnabled());
        boolean shuttleOn = p == null || !Boolean.FALSE.equals(p.getDailyShuttleEnabled());
        String a = p == null ? "" : (p.getAirport() == null ? "" : p.getAirport().trim());
        this.airport = airportOn && notBlank(a) && !a.equals("—") ? a : "";
        this.lodging = p == null ? "hotel" : Program.normalizeLodgingType(p.getLodgingType());
        List<String> parts = new ArrayList<>();
        parts.add("registration details");
        parts.add("accommodation");
        if (airportOn) parts.add("airport pickups");
        if (shuttleOn) parts.add("daily shuttle schedule");
        this.covers = "your " + (parts.size() == 1 ? parts.get(0)
            : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1));
    }

    public static ProgramBranding of(Program p) { return new ProgramBranding(p); }

    /** "Pinewood Youth Camp — Registration Confirmed", or just "Registration Confirmed" without a program. */
    public String subject(String what) { return name.isEmpty() ? what : name + " — " + what; }

    /** Email sender name: the program's name, or null to use the configured default. */
    public String senderName() { return name.isEmpty() ? null : name; }

    /** Signature line: "Pinewood Youth Camp Team". */
    public String teamName() { return name.isEmpty() ? "The Organising Team" : name + " Team"; }

    /** Name at the top of an email: the program's name, or "ProgramHub". */
    public String headerName() { return name.isEmpty() ? "ProgramHub" : name; }

    /** "Camp Hall · Denver, CO", or "". */
    public String place() { return place; }

    /** "July 5–9, 2027", or "". */
    public String dates() { return dates; }

    /** Full venue address, or "". */
    public String venueLine() { return venueLine; }

    /** Arrival airport when pickups are on, or "". */
    public String airport() { return airport; }

    /** Lodging word with a capital: "Hotel", "Cabin", … */
    public String lodgingLabel() { return Character.toUpperCase(lodging.charAt(0)) + lodging.substring(1); }

    /** Program name for running text: "Pinewood Youth Camp", or "the program". */
    public String nameOrGeneric() { return name.isEmpty() ? "the program" : name; }

    /** Placeholders for plain-text / SMS templates (values not escaped). */
    public Map<String, String> textVars() {
        Map<String, String> v = new HashMap<>();
        v.put("program_name", nameOrGeneric());
        v.put("team_name", teamName());
        v.put("program_dates", dates);
        v.put("program_place", place);
        v.put("venue_line", venueLine);
        v.put("airport", airport);
        v.put("lodging", lodging);
        v.put("lodging_label", Character.toUpperCase(lodging.charAt(0)) + lodging.substring(1));
        v.put("covers", covers);
        return v;
    }

    static String dateRange(String start, String end) {
        LocalDate s = ProgramDates.parseDate(start);
        if (s == null) return "";
        LocalDate e = ProgramDates.parseDate(end);
        DateTimeFormatter month = DateTimeFormatter.ofPattern("MMMM", Locale.US);
        if (e == null || !e.isAfter(s)) return s.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US));
        if (s.getYear() != e.getYear()) {
            DateTimeFormatter full = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
            return s.format(full) + " – " + e.format(full);
        }
        if (s.getMonth() == e.getMonth()) {
            return s.format(month) + " " + s.getDayOfMonth() + "–" + e.getDayOfMonth() + ", " + s.getYear();
        }
        return s.format(month) + " " + s.getDayOfMonth() + " – " + e.format(month) + " " + e.getDayOfMonth() + ", " + s.getYear();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String join(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) if (notBlank(p)) kept.add(p.trim());
        return String.join(sep, kept);
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
