package com.btl.transport.participant;

import java.util.Set;

/** First / last name and gender rules shared by registration and admin edits. */
public final class PersonName {
    private PersonName() {}

    public static final int MAX_PART = 120;
    public static final Set<String> GENDERS = Set.of("male", "female");

    /** Trimmed text with inner runs of spaces collapsed; null for blank. */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /** "First Last" for full_name, which every screen, email and export reads. */
    public static String join(String first, String last) {
        String f = clean(first), l = clean(last);
        if (f == null) return l;
        if (l == null) return f;
        return f + " " + l;
    }

    /** "male" / "female" (any case) → lowercase; blank → null; anything else → IllegalArgumentException. */
    public static String gender(String raw) {
        String g = clean(raw);
        if (g == null) return null;
        g = g.toLowerCase();
        if (!GENDERS.contains(g)) throw new IllegalArgumentException("Gender must be Male or Female");
        return g;
    }
}
