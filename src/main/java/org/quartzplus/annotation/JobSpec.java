package org.quartzplus.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines the complete configuration blueprint for a Quartz Job and its associated Trigger.
 * <p>
 * This annotation is the primary mechanism for declarative job registration within the
 * {@code QuartzExecutorService}. It encapsulates identity (names and groups), scheduling
 * strategy (via {@link TriggerSpec}), and lifecycle behavior (via {@link TriggerState}).
 *
 * <p><b>Usage Requirements:</b></p>
 * <ul>
 *   <li>The annotated class <strong>must</strong> implement the Quartz {@link org.quartzplus.Job} interface.</li>
 *   <li>The class must be accessible to the {@code QuartzExecutorService} through a {@code JobsCollection}.</li>
 * </ul>
 *
 * @see TriggerSpec
 * @see TriggerState
 * @see OnErrorSpec
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface JobSpec {

    /**
     * Unique identifier for the JobDetail.
     *
     * @return the name of the job.
     */
    String jobName();

    /**
     * The logical grouping for both the Job and its Trigger.
     * Defaults to "MainGroup".
     *
     * @return the group name.
     */
    String groupName() default "MainGroup";

    /**
     * Unique identifier for the Trigger associated with this job.
     *
     * @return the name of the trigger.
     */
    String triggerName();

    /**
     * A human-readable description of what the job does, stored within the JobDetail.
     *
     * @return the job description.
     */
    String jobDescription() default "";

    /**
     * Defines the initial state of the trigger, such as whether it is enabled or
     * starts in a paused state.
     *
     * @return the trigger state configuration.
     */
    TriggerState triggerState() default @TriggerState;

    /**
     * Defines the scheduling strategy (Cron or Simple) for this job.
     *
     * @return the trigger specification.
     */
    TriggerSpec trigger() default @TriggerSpec;

    /**
     * Defines the behavior and logging strategy if the job execution fails.
     *
     * @return the error handling specification.
     */
    OnErrorSpec onError() default @OnErrorSpec;

    /**
     * The fully qualified class name of the Quartz {@link org.quartz.Calendar} to associate
     * with the trigger. This allows for excluding specific time periods (e.g., holidays).
     * <p>
     * Defaults to {@link org.quartzplus.calendar.NoRestrictionsCalendar}.
     *
     * @return the calendar class name.
     */
    String calendarClass() default "org.quartzplus.calendar.NoRestrictionsCalendar";

}