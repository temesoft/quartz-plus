package org.quartzplus.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Configuration metadata for defining a Quartz {@link org.quartz.CronTrigger}.
 * <p>
 * This annotation allows for both static and dynamic configuration of cron-based schedules.
 * Fields ending in {@code Exp} support Spring Expression Language (SpEL) or property
 * placeholders (e.g., {@code ${my.job.cron}}), allowing the schedule to be resolved
 * at runtime from the application environment.
 *
 * @see JobSpec
 * @see SimpleTriggerSpec
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface CronTriggerSpec {

    /**
     * The static cron expression defining the trigger schedule.
     * <p>
     * <b>Examples:</b>
     * <ul>
     *   <li>{@code "0 30 10-13 ? * WED,FRI"} - Fires at 10:30, 11:30, 12:30, and 13:30 every Wed and Fri.</li>
     *   <li>{@code "0 0/5 * * * ?"} - Fires every 5 minutes.</li>
     * </ul>
     *
     * @return the cron expression string.
     */
    String cronExpression() default "";

    /**
     * A dynamic expression or property placeholder for the cron schedule.
     * <p>
     * If provided, this value is processed via an {@code ExpressionProcessor} and
     * <b>overrides</b> the static {@link #cronExpression()}.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.schedule.cron}"}).
     */
    String cronExpressionExp() default "";

    /**
     * The ID of the {@link java.util.TimeZone} in which the cron expression should be evaluated.
     * <p>
     * If left empty, the system default time zone is used.
     *
     * @return the time zone ID (e.g., "UTC", "America/New_York").
     */
    String timeZone() default "";

    /**
     * A dynamic expression or property placeholder for the trigger time zone.
     * <p>
     * If provided, this value is processed at runtime and <b>overrides</b>
     * the static {@link #timeZone()}.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.schedule.timezone}"}).
     */
    String timeZoneExp() default "";

}
