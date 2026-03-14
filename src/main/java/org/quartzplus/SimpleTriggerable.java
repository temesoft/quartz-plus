package org.quartzplus;

import org.quartz.SimpleTrigger;

/**
 * Interface to specify simple trigger for job bean
 */
public interface SimpleTriggerable {

    int REPEAT_INDEFINITELY = SimpleTrigger.REPEAT_INDEFINITELY;

    /**
     * Specifies simple trigger repeat count. Value: -1 used to specify repeat indefinitely - {@link SimpleTriggerable#REPEAT_INDEFINITELY}
     */
    int getRepeatCount();

    /**
     * Specifies simple trigger repeat interval. Interval delay between executions in milliseconds.
     */
    long getRepeatInterval();

}
