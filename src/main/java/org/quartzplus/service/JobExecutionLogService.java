package org.quartzplus.service;

import jakarta.annotation.Nullable;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.QuartzExecutionNode;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing the persistence and retrieval of Quartz job execution logs.
 * <p>
 * This service provides the infrastructure to track job history, including success/failure states,
 * execution durations, and error details. It also provides insights into the status of
 * active scheduler nodes within a clustered environment.</p>
 *
 * @see JobExecutionLog
 * @see QuartzExecutionNode
 */
public interface JobExecutionLogService {

    /**
     * Retrieves a paged collection of job execution logs based on the provided filters.
     *
     * @param pageSize      the maximum number of records to return in a single page.
     * @param currentPage   the zero-based index of the page to retrieve.
     * @param groupName     (Optional) filter logs by the Quartz job group.
     * @param jobName       (Optional) filter logs by the Quartz job name.
     * @param triggerName   (Optional) filter logs by the name of the trigger that fired the job.
     * @param instanceClass (Optional) filter logs by the fully qualified class name of the job implementation.
     * @return a {@link Page} of {@link JobExecutionLog} matching the criteria.
     */
    Page<JobExecutionLog> getJobExecutionLogList(Integer pageSize,
                                                 Integer currentPage,
                                                 @Nullable String groupName,
                                                 @Nullable String jobName,
                                                 @Nullable String triggerName,
                                                 @Nullable String instanceClass);

    /**
     * Retrieves a specific job execution log entry by its unique identifier.
     *
     * @param jobExecutionLogId the unique ID (typically a UUID) of the log entry.
     * @return an {@link Optional} containing the log if found, otherwise empty.
     */
    Optional<JobExecutionLog> getJobExecutionLog(String jobExecutionLogId);

    /**
     * Records a new job execution event in the log storage.
     *
     * @param jobExecutionLog the execution details to persist.
     */
    void insertJobExecutionLog(JobExecutionLog jobExecutionLog);

    /**
     * Deletes historical log entries created before the specified timestamp.
     *
     * @param until the cutoff {@link Instant}; records older than this will be removed.
     * @return the total number of records successfully deleted.
     */
    int clearJobExecutionLog(Instant until);

    /**
     * Retrieves a list of all known scheduler nodes in the cluster and their last check-in status.
     *
     * @return a {@link List} of {@link QuartzExecutionNode} objects representing the cluster state.
     */
    List<QuartzExecutionNode> getQuartzExecutionNodeList();

    /**
     * Returns the configured prefix used for the underlying storage tables or identifiers.
     *
     * @return the table prefix string (e.g., "QRTZ_").
     */
    String getTablePrefix();

}
