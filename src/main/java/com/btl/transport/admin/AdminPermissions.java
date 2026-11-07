package com.btl.transport.admin;

import java.util.*;
import java.util.regex.Pattern;

/**
 * What a program admin may do. Stored on their program membership ({@code program_admins.role}) as a
 * comma-separated set, e.g. "REGISTRATIONS,PAYMENTS". "FULL" means everything. Existing values
 * ("FULL", "ACCOMMODATION") remain valid.
 *
 * <p>Every admin API request is checked against {@link #isAllowed} by JwtAuthFilter, so the
 * permissions are enforced on the server, not just hidden in the UI.
 */
public final class AdminPermissions {

    private AdminPermissions() {}

    public static final String FULL = "FULL";
    /** See all participants, their details and payment status, and export them (read-only). */
    public static final String REGISTRATIONS = "REGISTRATIONS";
    /** See everyone's payment status and record payments / mark paid. */
    public static final String PAYMENTS = "PAYMENTS";
    /** Manage rooms, allocation and accommodation contacts. */
    public static final String ACCOMMODATION = "ACCOMMODATION";

    public static final List<String> ALL = List.of(FULL, REGISTRATIONS, PAYMENTS, ACCOMMODATION);

    /** "REGISTRATIONS, payments" → {REGISTRATIONS, PAYMENTS}; unknown values dropped; FULL absorbs the rest. */
    public static Set<String> parse(String stored) {
        Set<String> out = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) return out;
        for (String p : stored.split(",")) {
            String v = p.trim().toUpperCase(Locale.ROOT);
            if (v.equals("PAID")) v = REGISTRATIONS;  // former "Paid participants" permission, merged into Registrations
            if (ALL.contains(v)) out.add(v);
        }
        if (out.contains(FULL)) return new LinkedHashSet<>(List.of(FULL));
        return out;
    }

    /** Normalised storage form, in a stable order; null/empty when nothing valid was given. */
    public static String format(Collection<String> perms) {
        if (perms == null) return null;
        Set<String> parsed = parse(String.join(",", perms));
        if (parsed.isEmpty()) return null;
        List<String> ordered = new ArrayList<>();
        for (String p : ALL) if (parsed.contains(p)) ordered.add(p);
        return String.join(",", ordered);
    }

    /** Human label, e.g. "Full Access" or "Registrations, Payments". */
    public static String label(Set<String> perms) {
        if (perms.isEmpty() || perms.contains(FULL)) return "Full Access";
        List<String> names = new ArrayList<>();
        for (String p : ALL) {
            if (!perms.contains(p)) continue;
            names.add(switch (p) {
                case REGISTRATIONS -> "Registrations";
                case PAYMENTS -> "Payments";
                case ACCOMMODATION -> "Accommodation";
                default -> p;
            });
        }
        return String.join(", ", names);
    }

    // ── Which permission each admin endpoint needs (FULL may call everything) ──────────────

    private record Rule(String method, Pattern path, Set<String> anyOf) {}

    private static final String P = "^/api/v1/admin";
    private static final Set<String> VIEW_PARTICIPANTS = Set.of(REGISTRATIONS, PAYMENTS);

    private static final List<Rule> RULES = List.of(
        // Participants: viewers can list, open and export (read-only).
        new Rule("GET", Pattern.compile(P + "/participants$"), VIEW_PARTICIPANTS),
        new Rule("GET", Pattern.compile(P + "/participants/export\\.csv$"), VIEW_PARTICIPANTS),
        new Rule("GET", Pattern.compile(P + "/participants?/[^/]+$"), VIEW_PARTICIPANTS),
        new Rule("GET", Pattern.compile(P + "/participants/[^/]+/payments$"), VIEW_PARTICIPANTS),
        new Rule("GET", Pattern.compile(P + "/registration-fields$"), VIEW_PARTICIPANTS),
        // Payments
        new Rule("POST", Pattern.compile(P + "/participants/[^/]+/payments$"), Set.of(PAYMENTS)),
        new Rule("DELETE", Pattern.compile(P + "/participants/[^/]+/payments/[^/]+$"), Set.of(PAYMENTS)),
        new Rule("PATCH", Pattern.compile(P + "/participants/[^/]+/payment-status$"), Set.of(PAYMENTS)),
        // Accommodation
        new Rule("*", Pattern.compile(P + "/room-assignments(/.*)?$"), Set.of(ACCOMMODATION)),
        new Rule("*", Pattern.compile(P + "/accommodation-contacts(/.*)?$"), Set.of(ACCOMMODATION)),
        new Rule("PATCH", Pattern.compile(P + "/programs/[^/]+/(room-allocation-settings|roommate-visible)$"), Set.of(ACCOMMODATION)),
        // The Accommodation page saves the hotel list through the program update; the controller
        // limits non-full admins to the hotels field.
        new Rule("PATCH", Pattern.compile(P + "/programs/[^/]+$"), Set.of(ACCOMMODATION)),
        new Rule("GET", Pattern.compile(P + "/hotels$"), Set.of(ACCOMMODATION))
    );

    /** Whether an admin holding {@code perms} may make this request. */
    public static boolean isAllowed(Set<String> perms, String method, String path) {
        if (perms.contains(FULL)) return true;
        for (Rule r : RULES) {
            if ((r.method().equals("*") || r.method().equalsIgnoreCase(method)) && r.path().matcher(path).matches()) {
                for (String p : r.anyOf()) if (perms.contains(p)) return true;
                return false;
            }
        }
        return false;  // everything else (settings, trips, drivers, users, …) is full-admin only
    }
}
