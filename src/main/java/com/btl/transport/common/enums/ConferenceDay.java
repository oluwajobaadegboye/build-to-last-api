package com.btl.transport.common.enums;

import java.time.DayOfWeek;

/**
 * Weekday a run or shuttle config belongs to. Stored as the Postgres enum conference_day.
 * MONDAY–WEDNESDAY were added later (V41) and are appended to keep existing ordinals stable.
 */
public enum ConferenceDay {
    THURSDAY, FRIDAY, SATURDAY, SUNDAY, MONDAY, TUESDAY, WEDNESDAY;

    public static ConferenceDay of(DayOfWeek dayOfWeek) {
        return dayOfWeek == null ? null : valueOf(dayOfWeek.name());
    }
}
