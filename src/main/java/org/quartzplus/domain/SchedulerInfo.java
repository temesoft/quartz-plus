package org.quartzplus.domain;

import org.quartz.SchedulerMetaData;
import org.quartzplus.Job;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * A data transfer object (DTO) providing a comprehensive snapshot of the QuartzPlus scheduler status.
 * <p>
 * This class aggregates standard Quartz {@link SchedulerMetaData} with QuartzPlus-specific
 * configuration details, such as registered job classes, logging service implementations,
 * and system URIs. It is typically used as the response body for management endpoints.
 *
 * @see org.quartz.SchedulerMetaData
 * @see java.io.Serializable
 */
public class SchedulerInfo implements Serializable {

    private final SchedulerMetaData schedulerMetaData;
    private final List<Class<? extends Job>> jobsCollection;
    private final Instant serverTimestamp;
    private final String tablePrefix;
    private final String jobExecutionLogServiceClass;
    private final String quartzPlusVersion;
    private final String webUri;
    private final String apiUri;

    /**
     * Constructs a new {@code SchedulerInfo} with the specified system metadata.
     *
     * @param schedulerMetaData           The core metadata from the underlying Quartz scheduler.
     * @param jobsCollection              A list of {@link Job} classes currently registered or available.
     * @param serverTimestamp             The current time on the server at the moment of data generation.
     * @param tablePrefix                 The prefix used for database tables (e.g., "QRTZ_").
     * @param jobExecutionLogServiceClass The fully qualified name of the active logging service implementation.
     * @param quartzPlusVersion           The version string of the QuartzPlus library.
     * @param webUri                      The configured base URI for the Web Administration portal.
     * @param apiUri                      The configured base URI for the REST API.
     */
    public SchedulerInfo(final SchedulerMetaData schedulerMetaData,
                         final List<Class<? extends Job>> jobsCollection,
                         final Instant serverTimestamp,
                         final String tablePrefix,
                         final String jobExecutionLogServiceClass,
                         final String quartzPlusVersion,
                         final String webUri,
                         final String apiUri) {
        this.schedulerMetaData = schedulerMetaData;
        this.jobsCollection = jobsCollection;
        this.serverTimestamp = serverTimestamp;
        this.tablePrefix = tablePrefix;
        this.jobExecutionLogServiceClass = jobExecutionLogServiceClass;
        this.quartzPlusVersion = quartzPlusVersion;
        this.webUri = webUri;
        this.apiUri = apiUri;
    }

    @Override
    public String toString() {
        return "SchedulerInfo" +
                "{schedulerMetaData=" + schedulerMetaData +
                ", jobsCollection=" + jobsCollection +
                ", tablePrefix=" + tablePrefix +
                ", serverTimestamp=" + serverTimestamp +
                ", jobExecutionLogServiceClass=" + jobExecutionLogServiceClass +
                ", quartzPlusVersion=" + quartzPlusVersion +
                ", webUri=" + webUri +
                ", apiUri=" + apiUri +
                '}';
    }

    public SchedulerMetaData getSchedulerMetaData() {
        return schedulerMetaData;
    }

    public List<Class<? extends Job>> getJobsCollection() {
        return jobsCollection;
    }

    public Instant getServerTimestamp() {
        return serverTimestamp;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public String getJobExecutionLogServiceClass() {
        return jobExecutionLogServiceClass;
    }

    public String getQuartzPlusVersion() {
        return quartzPlusVersion;
    }

    public String getWebUri() {
        return webUri;
    }

    public String getApiUri() {
        return apiUri;
    }
}
