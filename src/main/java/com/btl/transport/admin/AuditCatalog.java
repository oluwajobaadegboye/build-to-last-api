package com.btl.transport.admin;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-language names for admin changes, matched on method + path. Used when a change is logged and
 * again when older rows (logged before categories existed) are read back. Group 1 of a pattern is the target.
 */
public final class AuditCatalog {

    /** What a target id in the path refers to, so its name can be looked up. */
    public enum Kind { PARTICIPANT_CODE, PARTICIPANT_ID, QUESTION, SOUVENIR, ADMIN, DRIVER, VEHICLE, ROOM, TRIP, NONE }

    public record Match(String category, String action, Kind kind, String target) {}

    private record Rule(String method, Pattern path, String category, String action, Kind kind) {}

    private static final String P = "^/api/v1/admin";
    private static Rule r(String method, String path, String category, String action, Kind kind) {
        return new Rule(method, Pattern.compile(P + path + "$"), category, action, kind);
    }
    private static Rule r(String method, String path, String category, String action) {
        return r(method, path, category, action, Kind.NONE);
    }

    /** First match wins, so specific paths come before general ones. */
    private static final List<Rule> RULES = List.of(
        // Check-in
        r("PUT", "/participants/([^/]+)/check-in", "checkin", "Changed check-in for", Kind.PARTICIPANT_CODE),
        // Payments ("Sent payment reminders" is also the cooldown key in ProgramOpsController — keep it as is)
        r("POST", "/participants/([^/]+)/payments", "payments", "Recorded a payment for", Kind.PARTICIPANT_CODE),
        r("DELETE", "/participants/([^/]+)/payments/[^/]+", "payments", "Deleted a payment for", Kind.PARTICIPANT_CODE),
        r("PATCH", "/participants/([^/]+)/payment-status", "payments", "Changed payment status for", Kind.PARTICIPANT_CODE),
        r("POST", "/payments/reminders", "payments", "Sent payment reminders"),
        // Souvenirs
        r("PUT", "/participants/([^/]+)/souvenirs/[^/]+", "souvenirs", "Updated souvenirs for", Kind.PARTICIPANT_CODE),
        r("POST", "/souvenirs", "souvenirs", "Added souvenir item"),
        r("PUT", "/souvenirs/order", "souvenirs", "Reordered souvenir items"),
        r("POST", "/souvenirs/([^/]+)/restore", "souvenirs", "Restored souvenir item", Kind.SOUVENIR),
        r("PATCH", "/souvenirs/([^/]+)", "souvenirs", "Edited souvenir item", Kind.SOUVENIR),
        r("DELETE", "/souvenirs/([^/]+)", "souvenirs", "Removed souvenir item", Kind.SOUVENIR),
        // Registration form
        r("POST", "/registration-fields", "form", "Added question"),
        r("PUT", "/registration-fields/order", "form", "Reordered registration questions"),
        r("POST", "/registration-fields/([^/]+)/restore", "form", "Restored question", Kind.QUESTION),
        r("PATCH", "/registration-fields/([^/]+)", "form", "Edited question", Kind.QUESTION),
        r("DELETE", "/registration-fields/([^/]+)", "form", "Removed question", Kind.QUESTION),
        // Participants
        r("POST", "/participants/([^/]+)/resend-code", "participants", "Resent the registration code to", Kind.PARTICIPANT_CODE),
        r("POST", "/participants/assign-breakout-groups", "participants", "Assigned breakout groups"),
        r("PATCH", "/participants/([^/]+)/hotel", "accommodation", "Changed hotel for", Kind.PARTICIPANT_ID),
        r("PATCH", "/participants/([^/]+)/attention", "participants", "Changed the attention flag for", Kind.PARTICIPANT_ID),
        r("POST", "/alerts?/([^/]+)/resolve", "participants", "Resolved an alert for", Kind.PARTICIPANT_CODE),
        r("PATCH", "/participant/([^/]+)/flight/[^/]+", "transport", "Updated flight for", Kind.PARTICIPANT_CODE),
        r("PATCH", "/participant/([^/]+)", "participants", "Edited", Kind.PARTICIPANT_CODE),
        r("PUT", "/participants?/([^/]+)", "participants", "Edited", Kind.PARTICIPANT_CODE),
        r("DELETE", "/participants?/([^/]+)", "participants", "Deleted participant", Kind.PARTICIPANT_CODE),
        r("POST", "/upload", "participants", "Imported participants from a file"),
        // Team
        r("POST", "/users", "team", "Added admin"),
        r("PUT", "/users/([^/]+)", "team", "Changed access for", Kind.ADMIN),
        r("DELETE", "/users/([^/]+)", "team", "Removed admin", Kind.ADMIN),
        // Accommodation
        r("POST", "/room-assignments/import", "accommodation", "Imported rooms from a file"),
        r("POST", "/room-assignments/notify", "accommodation", "Emailed room details to everyone"),
        r("POST", "/room-assignments/backfill-participants", "accommodation", "Linked rooms to participants"),
        r("PUT", "/room-assignments/realloc", "accommodation", "Reallocated rooms"),
        r("POST", "/room-assignments/([^/]+)/notify", "accommodation", "Emailed room details for", Kind.ROOM),
        r("POST", "/room-assignments/([^/]+)/occupants/[^/]+/notify", "accommodation", "Emailed room details for", Kind.ROOM),
        r("PATCH", "/room-assignments/([^/]+)/occupants/[^/]+/ticket", "accommodation", "Updated a ticket in", Kind.ROOM),
        r("PUT", "/room-assignments/([^/]+)/occupants/[^/]+", "accommodation", "Assigned someone to", Kind.ROOM),
        r("DELETE", "/room-assignments/([^/]+)/occupants/[^/]+", "accommodation", "Removed someone from", Kind.ROOM),
        r("POST", "/room-assignments", "accommodation", "Added a room"),
        r("PATCH", "/room-assignments/([^/]+)", "accommodation", "Edited", Kind.ROOM),
        r("DELETE", "/room-assignments/([^/]+)", "accommodation", "Removed", Kind.ROOM),
        r("PATCH", "/programs/[^/]+/(?:roommate-visible|room-allocation-settings)", "accommodation", "Changed room settings"),
        r("POST", "/accommodation-contacts", "accommodation", "Added an accommodation contact"),
        r("PATCH", "/accommodation-contacts/[^/]+", "accommodation", "Edited an accommodation contact"),
        r("DELETE", "/accommodation-contacts/[^/]+", "accommodation", "Removed an accommodation contact"),
        // Transport
        r("PATCH", "/runs/([^/]+)/driver", "transport", "Assigned a driver to", Kind.TRIP),
        r("PATCH", "/runs/([^/]+)/vehicle", "transport", "Assigned a vehicle to", Kind.TRIP),
        r("PATCH", "/runs/([^/]+)/group-link", "transport", "Changed the group link for", Kind.TRIP),
        r("PATCH", "/run-participants/boarded", "transport", "Updated boarding"),
        r("PATCH", "/run-participants/move", "transport", "Moved people between trips"),
        r("POST", "/run", "transport", "Created a trip"),
        r("PATCH", "/run/([^/]+)", "transport", "Edited", Kind.TRIP),
        r("DELETE", "/run/([^/]+)", "transport", "Deleted", Kind.TRIP),
        r("POST", "/sync-airport-runs", "transport", "Rebuilt airport trips"),
        r("POST", "/recalculate-departure-times", "transport", "Recalculated departure times"),
        r("POST", "/send-manifest/([^/]+)", "transport", "Sent the manifest to", Kind.DRIVER),
        r("POST", "/drivers/([^/]+)/resend-link", "transport", "Resent the driver link to", Kind.DRIVER),
        r("POST", "/drivers", "transport", "Added driver"),
        r("PATCH", "/drivers/([^/]+)", "transport", "Edited driver", Kind.DRIVER),
        r("DELETE", "/drivers/([^/]+)", "transport", "Removed driver", Kind.DRIVER),
        r("POST", "/vehicles", "transport", "Added vehicle"),
        r("PATCH", "/vehicles/([^/]+)", "transport", "Edited vehicle", Kind.VEHICLE),
        r("DELETE", "/vehicles/([^/]+)", "transport", "Removed vehicle", Kind.VEHICLE),
        r("PATCH", "/config/shuttle", "transport", "Changed shuttle settings"),
        r("PATCH", "/config/airport", "transport", "Changed airport settings"),
        // Settings
        r("PATCH", "/config/notifications", "settings", "Changed notification settings"),
        r("PATCH", "/programs/[^/]+/policies", "settings", "Changed program policies"),
        r("POST", "/programs", "settings", "Created a program"),
        r("PATCH", "/programs/[^/]+", "settings", "Changed program settings"),
        r("DELETE", "/programs/[^/]+", "settings", "Deleted a program")
    );

    private AuditCatalog() {}

    /** Never null: unknown changes come back as category "other". */
    public static Match match(String method, String path) {
        if (method != null && path != null) {
            for (Rule rule : RULES) {
                if (!rule.method().equals(method)) continue;
                Matcher m = rule.path().matcher(path);
                if (m.matches()) {
                    String target = m.groupCount() >= 1 && m.group(1) != null && !m.group(1).isBlank() ? m.group(1) : null;
                    return new Match(rule.category(), rule.action(), target == null ? Kind.NONE : rule.kind(), target);
                }
            }
        }
        return new Match("other", "Made a change", Kind.NONE, null);
    }
}
