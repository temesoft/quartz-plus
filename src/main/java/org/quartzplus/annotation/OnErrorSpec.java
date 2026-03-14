package org.quartzplus.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Configuration metadata for defining error handling and retry strategies for a Quartz job.
 * <p>
 * This annotation specifies how the {@code QuartzExecutorService} should react when a job's
 * {@code executeJob(...)} method throws an exception. It supports static values as well as
 * dynamic Spring Expression Language (SpEL) or property placeholders for flexible
 * runtime configuration.
 *
 * @see JobSpec
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface OnErrorSpec {

    /**
     * The number of times to re-attempt the job execution if an exception occurs.
     * <p>
     * <b>Default:</b> {@code 0} (no retries).
     *
     * @return the number of retry attempts.
     */
    int onErrorRepeatCount() default 0;

    /**
     * A dynamic expression or property placeholder for the retry count.
     * <p>
     * If provided, this value is resolved at runtime and <b>overrides</b>
     * {@link #onErrorRepeatCount()}.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.retry.count}"}).
     */
    String onErrorRepeatCountExp() default "";

    /**
     * The waiting period, in milliseconds, between subsequent retry attempts.
     * <p>
     * <b>Default:</b> {@code 0} (immediate retry).
     *
     * @return the delay in milliseconds.
     */
    int onErrorRepeatDelay() default 0;

    /**
     * A dynamic expression or property placeholder for the retry delay.
     * <p>
     * If provided, this value is resolved at runtime and <b>overrides</b>
     * {@link #onErrorRepeatDelay()}.
     *
     * @return a SpEL expression or property key (e.g., {@code "${job.retry.delay}"}).
     */
    String onErrorRepeatDelayExp() default "";

}
