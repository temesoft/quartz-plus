package org.quartzplus.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Configuration metadata for defining the activation state and initial lifecycle of a Quartz trigger.
 * <p>
 * This annotation controls whether a trigger is created at all ({@link State}) and whether
 * it begins firing immediately or starts in a suspended state ({@link StartType}).
 * Both properties support static values and dynamic resolution via Spring Expression Language (SpEL)
 * or property placeholders.
 *
 * @see JobSpec
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface TriggerState {

    /**
     * Determines if the trigger should be registered with the scheduler.
     * <p>
     * If set to {@link State#DISABLED}, the scheduler will not create or fire this trigger.
     *
     * @return the enabled/disabled state of the trigger.
     */
    State enabled() default State.ENABLED;

    /**
     * A dynamic expression or property placeholder for the enabled state.
     * <p>
     * If provided, this value is resolved at runtime and <b>overrides</b> {@link #enabled()}.
     * Expects a string matching a {@link State} enum constant.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.feature.enabled}"}).
     */
    String enabledExp() default "";

    /**
     * Defines the initial execution state for newly registered triggers.
     * <p>
     * Use {@link StartType#PAUSED} to register a trigger without it firing immediately,
     * allowing for manual activation later via the scheduler.
     *
     * @return the initial start type.
     */
    StartType startType() default StartType.UNPAUSED;

    /**
     * A dynamic expression or property placeholder for the start type.
     * <p>
     * If provided, this value is resolved at runtime and <b>overrides</b> {@link #startType()}.
     * Expects a string matching a {@link StartType} enum constant.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.startup.paused}"}).
     */
    String startTypeExp() default "";

    /**
     * Represents the availability of a trigger within the scheduler.
     */
    enum State {
        /**
         * Trigger is created and eligible to fire.
         */
        ENABLED,
        /**
         * Trigger is not created in the scheduler.
         */
        DISABLED
    }

    /**
     * Represents the initial firing status of a trigger upon registration.
     */
    enum StartType {
        /**
         * Trigger is registered but suspended from firing.
         */
        PAUSED,
        /**
         * Trigger begins its firing schedule immediately upon registration.
         */
        UNPAUSED
    }
}
