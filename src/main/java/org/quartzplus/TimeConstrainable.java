package org.quartzplus;

import java.time.Instant;

/**
 * Interface to specify trigger time constraints (start / end).
 * Can be used with both simple or cron triggers
 */
public interface TimeConstrainable {

    /**
     * Specifies trigger start date object. Null value omits start time constrain.
     */
    Instant getStartTime();

    /**
     * Specifies trigger end date object. Null value omits end time constrain.
     */
    Instant getEndTime();
}
