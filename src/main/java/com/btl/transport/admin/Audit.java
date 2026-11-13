package com.btl.transport.admin;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Lets a controller describe its change more precisely for the activity log, e.g. "Checked in"
 * instead of "Changed check-in for". Only sets request attributes: it can never fail the request.
 */
public final class Audit {
    static final String ACTION  = "btl.audit.action";
    static final String DETAIL  = "btl.audit.detail";
    static final String SUBJECT = "btl.audit.subject";

    private Audit() {}

    /** Replaces the logged action. The subject's name is shown after it, so phrase it to lead in: "Handed out Cap to". */
    public static void action(String action) { set(ACTION, action); }
    /** A short extra detail shown with the entry, e.g. "Paid" or "email, phone". */
    public static void detail(String detail) { set(DETAIL, detail); }
    /** Name of what was changed when it isn't in the URL, e.g. the label of a newly added question. */
    public static void subject(String name) { set(SUBJECT, name); }

    private static void set(String key, String value) {
        RequestAttributes r = RequestContextHolder.getRequestAttributes();
        if (r != null && value != null && !value.isBlank()) r.setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
    }
}
