package org.quartzplus;

/**
 * Interface to specify repeatable settings for erroneous job execution.
 * Can be used with simple or cron triggers
 */
public interface OnErrorRepeatable {

    /**
     * Specifies repeat count of job bean executeJob(...) method in case of exception inside executeJob(...)
     */
    int getOnErrorRepeatCount();

    /**
     * Specifies delay in milliseconds between erroneous executions of job bean executeJob(...)
     */
    int getOnErrorRepeatDelay();
}
