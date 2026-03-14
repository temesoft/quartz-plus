package org.quartzplus.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A polymorphic trigger specification that defines how a Quartz job should be scheduled.
 * <p>
 * This annotation acts as a container for either a {@link SimpleTriggerSpec} or a
 * {@link CronTriggerSpec}. The {@code QuartzExecutorService} determines which trigger
 * type to initialize based on the presence of non-default values within these sub-annotations.
 *
 * <p><b>Usage Note:</b> Only one trigger type should typically be configured per job.
 * If both are provided, the implementation logic in the executor service determines
 * the precedence (usually prioritizing Cron over Simple).</p>
 *
 * @see JobSpec
 * @see SimpleTriggerSpec
 * @see CronTriggerSpec
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface TriggerSpec {

    /**
     * Configuration for an interval-based trigger.
     *
     * @return the simple trigger metadata.
     */
    SimpleTriggerSpec simpleTrigger() default @SimpleTriggerSpec;

    /**
     * Configuration for a calendar-based (cron) trigger.
     *
     * @return the cron trigger metadata.
     */
    CronTriggerSpec cronTrigger() default @CronTriggerSpec;

}
