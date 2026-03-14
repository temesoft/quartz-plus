package org.quartzplus;

import java.util.Locale;

/**
 * <p>
 * JobMetricTypeEnum is used to simplify and centralize the metrics naming for job execution events: success counter, failure counter, and duration timer.
 * </p>
 * Example of retrieving job metric names:
 * <pre>
 * String metricNameSuccess = JobMetricTypeEnum.SUCCESS.getMetricName(groupName, jobName);
 * </pre>
 */
public enum JobMetricTypeEnum {

    SUCCESS,
    FAILURE,
    DURATION;

    private static final String DOT = ".";

    /**
     * Returns metric name for provided group name and job name in the following pattern: jobs.GroupName.JobName.{JobMetricType}
     */
    public String getMetricName(final String groupName, final String jobName) {
        return Job.METRIC_PREFIX +
                DOT + groupName +
                DOT + jobName +
                DOT + name().toLowerCase(Locale.getDefault());
    }
}
