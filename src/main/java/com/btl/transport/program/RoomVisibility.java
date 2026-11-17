package com.btl.transport.program;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * When attendees may see their room on their page: always (default), once checked in, from a date and
 * time, or whichever of those comes first. Admins always see rooms.
 */
public final class RoomVisibility {

    public static final String ALWAYS = "always", CHECKIN = "checkin", DATE = "date", CHECKIN_OR_DATE = "checkin_or_date";
    public static final Set<String> MODES = Set.of(ALWAYS, CHECKIN, DATE, CHECKIN_OR_DATE);

    private RoomVisibility() {}

    public static String mode(Program p) {
        String m = p == null ? null : p.getRoomVisibility();
        return m != null && MODES.contains(m) ? m : ALWAYS;
    }

    /** True once the date/time (program time) has passed; false when there's no valid date. */
    static boolean dateReached(Program p, OffsetDateTime now) {
        if (p.getRoomVisibleFrom() == null || p.getRoomVisibleFrom().isBlank()) return false;
        try {
            ZoneId zone;
            try { zone = ZoneId.of(p.getTimezone() != null ? p.getTimezone() : "America/New_York"); }
            catch (Exception e) { zone = ZoneId.of("America/New_York"); }
            return !now.isBefore(LocalDateTime.parse(p.getRoomVisibleFrom()).atZone(zone).toOffsetDateTime());
        } catch (Exception e) {
            return false;
        }
    }

    /** Null when the attendee may see their room; otherwise {reason, from} explaining when they will. */
    public static Map<String, Object> lock(Program p, OffsetDateTime checkedInAt, OffsetDateTime now) {
        String m = mode(p);
        boolean checkedIn = checkedInAt != null;
        boolean visible = switch (m) {
            case CHECKIN -> checkedIn;
            case DATE -> dateReached(p, now) || (p.getRoomVisibleFrom() == null || p.getRoomVisibleFrom().isBlank());
            case CHECKIN_OR_DATE -> checkedIn || dateReached(p, now);
            default -> true;
        };
        if (visible) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reason", m);
        out.put("from", m.equals(CHECKIN) ? null : p.getRoomVisibleFrom());
        return out;
    }
}
