package org.quartzplus.calendar;

import org.quartz.impl.calendar.BaseCalendar;
import org.quartzplus.annotation.JobSpec;

/**
 * A Quartz {@link org.quartz.Calendar} implementation that provides no exclusions.
 * <p>
 * This calendar acts as a "pass-through" filter, meaning it does not block any
 * dates or times from the trigger's firing schedule. It is typically used as
 * a default placeholder when no specific holiday or exclusion logic is required.
 * </p>
 *
 * @see BaseCalendar
 * @see JobSpec#calendarClass()
 */
public class NoRestrictionsCalendar extends BaseCalendar {

    /**
     * Constructs a new {@code NoRestrictionsCalendar} with no base calendar
     * and no restricted time segments.
     */
    public NoRestrictionsCalendar() {
        super();
    }
}