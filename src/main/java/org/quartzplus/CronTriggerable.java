package org.quartzplus;

import java.util.TimeZone;

/**
 * Interface to specify cron trigger for job bean
 */
public interface CronTriggerable {

    /**
     * Specifies cron trigger expression.
     * <p>
     * Cron expression example: an expression to create a trigger that fires at 10:30, 11:30, 12:30, and 13:30, on every Wednesday and Friday is "0 30 10-13 ? * WED,FRI".
     * <p>
     * Cron expression example: an expression to create a trigger that simply fires every 5 minutes is "0 0/5 * * * ?".
     */
    String getCronExpression();

    /**
     * Specifies cron trigger specific time zone
     */
    TimeZone getTriggerTimeZone();
}
