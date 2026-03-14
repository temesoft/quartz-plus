package org.quartzplus.domain;


import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a historical record of a single Quartz job execution.
 * <p>
 * This immutable data object captures comprehensive details about a job run,
 * including identifying information, execution status, performance metrics,
 * and diagnostic data (such as stack traces and JSON context) in the event of failure.
 */
public class JobExecutionLog {

    private final String id;
    private final String groupName;
    private final String triggerName;
    private final String jobName;
    private final Integer priority;
    private final Boolean success;
    private final String errorMessage;
    private final String stackTrace;
    private final String jsonData;
    private final String fireInstanceId;
    private final String instanceClass;
    private final Long duration;
    private final Instant createTime;

    /**
     * Constructs a detailed log entry for a job execution.
     *
     * @param id             unique identifier for the log; if null, a random UUID is generated.
     * @param groupName      the group name of the job.
     * @param triggerName    the name of the trigger that fired the job.
     * @param jobName        the name of the job being executed.
     * @param priority       the priority level assigned to the execution.
     * @param success        {@code true} if the job completed without unhandled exceptions.
     * @param errorMessage   the error message if the job failed.
     * @param fireInstanceId the unique ID assigned by Quartz to this specific firing instance.
     * @param instanceClass  the fully qualified name of the job implementation class.
     * @param duration       the time taken to execute the job, typically in milliseconds.
     * @param stackTrace     the full exception stack trace if the job failed.
     * @param jsonData       serialized job data or parameters used during execution.
     * @param createTime     the timestamp of the log entry; if null, the current time is used.
     */
    public JobExecutionLog(final String id,
                           final String groupName,
                           final String triggerName,
                           final String jobName,
                           final Integer priority,
                           final Boolean success,
                           final String errorMessage,
                           final String fireInstanceId,
                           final String instanceClass,
                           final Long duration,
                           final String stackTrace,
                           final String jsonData,
                           final Instant createTime) {
        this.id = (id == null ? UUID.randomUUID().toString() : id);
        this.createTime = (createTime == null ? Instant.now() : createTime);
        this.groupName = groupName;
        this.triggerName = triggerName;
        this.jobName = jobName;
        this.priority = priority;
        this.success = success;
        this.errorMessage = errorMessage;
        this.fireInstanceId = fireInstanceId;
        this.instanceClass = instanceClass;
        this.duration = duration;
        this.stackTrace = stackTrace;
        this.jsonData = jsonData;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("groupName", groupName)
                .append("triggerName", triggerName)
                .append("jobName", jobName)
                .append("priority", priority)
                .append("success", success)
                .append("errorMessage", errorMessage)
                .append("stackTrace", stackTrace)
                .append("jsonData", jsonData)
                .append("fireInstanceId", fireInstanceId)
                .append("instanceClass", instanceClass)
                .append("duration", duration)
                .append("createTime", createTime)
                .toString();
    }

    public String getId() {
        return id;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public String getJobName() {
        return jobName;
    }

    public Integer getPriority() {
        return priority;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public String getJsonData() {
        return jsonData;
    }

    public String getFireInstanceId() {
        return fireInstanceId;
    }

    public String getInstanceClass() {
        return instanceClass;
    }

    public Long getDuration() {
        return duration;
    }

    public Instant getCreateTime() {
        return createTime;
    }
}
