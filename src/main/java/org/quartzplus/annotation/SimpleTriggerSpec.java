package org.quartzplus.annotation;

import org.quartz.SimpleTrigger;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Configuration metadata for defining a Quartz {@link org.quartz.SimpleTrigger}.
 * <p>
 * This annotation allows for the configuration of jobs that fire at a specific interval.
 * It supports both static values and dynamic resolution via Spring Expression Language (SpEL)
 * or property placeholders (e.g., {@code ${my.job.interval}}).
 *
 * @see JobSpec
 * @see CronTriggerSpec
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface SimpleTriggerSpec {

    /**
     * The number of times the trigger should repeat after its first execution.
     * <p>
     * Use {@code -1} (or {@link SimpleTrigger#REPEAT_INDEFINITELY}) to repeat
     * indefinitely. A value of {@code 0} means the job fires exactly once.
     *
     * @return the number of repeat attempts.
     */
    int repeatCount() default 0;

    /**
     * A dynamic expression or property placeholder for the repeat count.
     * <p>
     * If provided, this value is resolved at runtime and <b>overrides</b>
     * the static {@link #repeatCount()}.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.repeat.count}"}).
     */
    String repeatCountExp() default "";

    /**
     * The time interval, in milliseconds, between subsequent executions.
     * <p>
     * <b>Example:</b> {@code 60000} for one minute.
     *
     * @return the interval delay in milliseconds.
     */
    long repeatInterval() default 0;

    /**
     * A dynamic expression or property placeholder for the repeat interval.
     * <p>
     * If provided, this value is resolved at runtime and <b>overrides</b>
     * the static {@link #repeatInterval()}.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.repeat.interval}"}).
     */
    String repeatIntervalExp() default "";

}