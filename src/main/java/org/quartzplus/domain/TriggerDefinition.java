package org.quartzplus.domain;

import org.quartz.Trigger;

import java.io.Serializable;

/**
 * A data transfer object (DTO) that encapsulates a Quartz {@link Trigger} along with its current lifecycle state.
 * <p>
 * This class provides a unified view of a trigger's definition and its runtime status (e.g., NORMAL, PAUSED, BLOCKED),
 * which is useful for monitoring and administrative interfaces.
 *
 * @see org.quartz.Trigger
 * @see org.quartz.Trigger.TriggerState
 * @see java.io.Serializable
 */
public class TriggerDefinition implements Serializable {

    private final Trigger trigger;
    private final Trigger.TriggerState state;

    /**
     * Constructs a new {@code TriggerDefinition} with the specified trigger and state.
     *
     * @param trigger the Quartz {@link Trigger} instance containing scheduling data.
     * @param state   the current {@link Trigger.TriggerState} of the trigger in the scheduler.
     */
    public TriggerDefinition(final Trigger trigger, final Trigger.TriggerState state) {
        this.trigger = trigger;
        this.state = state;
    }

    @Override
    public String toString() {
        return "TriggerDefinition" +
                "{trigger=" + trigger +
                ", state=" + state +
                '}';
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public Trigger.TriggerState getState() {
        return state;
    }
}
