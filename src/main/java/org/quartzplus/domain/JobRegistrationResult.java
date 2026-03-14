package org.quartzplus.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;

/**
 * Represents the outcome of an attempt to register a new job and its associated trigger within the Quartz scheduler.
 * <p>
 * This immutable data object provides state information regarding whether the job was newly created,
 * if it already existed in the scheduler, or if it was intentionally skipped (disabled). It also
 * contains the resulting {@link JobDetail} and {@link Trigger} definitions.
 */
public class JobRegistrationResult {

    private final boolean registered;
    private final boolean alreadyExist;
    private final boolean disabled;
    private final String message;
    private final Trigger trigger;
    private final JobDetail jobDetail;

    /**
     * Constructs a new registration result with full status details.
     *
     * @param registered   {@code true} if the job and trigger were successfully added to the scheduler.
     * @param alreadyExist {@code true} if a job with the same identity was already present in the scheduler.
     * @param disabled     {@code true} if the registration was bypassed due to configuration or status flags.
     * @param message      A descriptive message or reason regarding the registration outcome.
     * @param trigger      The {@link Trigger} associated with this registration attempt.
     * @param jobDetail    The {@link JobDetail} associated with this registration attempt.
     */
    public JobRegistrationResult(final boolean registered, final boolean alreadyExist, final boolean disabled, final String message, final Trigger trigger, final JobDetail jobDetail) {
        this.registered = registered;
        this.alreadyExist = alreadyExist;
        this.disabled = disabled;
        this.message = message;
        this.trigger = trigger;
        this.jobDetail = jobDetail;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("registered", registered)
                .append("alreadyExist", alreadyExist)
                .append("disabled", disabled)
                .append("message", message)
                .append("trigger", trigger)
                .append("jobDetail", jobDetail)
                .toString();
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isAlreadyExist() {
        return alreadyExist;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public String getMessage() {
        return message;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public JobDetail getJobDetail() {
        return jobDetail;
    }
}