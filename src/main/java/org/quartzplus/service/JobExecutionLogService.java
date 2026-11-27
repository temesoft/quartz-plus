package org.quartzplus.service;

import jakarta.annotation.Nullable;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.JobExecutionStats;
import org.quartzplus.domain.QuartzExecutionNode;
import org.springframework.data.domain.Page;

import java.time.Duration;
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
     * Retrieves a paged collection of job execution logs, newest first, that match every given filter; a filter
     * that is {@code null} is not applied.
     * <p>
     * The default implementation serves only requests without a result or time filter, by calling
     * {@link #getJobExecutionLogList(Integer, Integer, String, String, String, String)}; an implementation that
     * supports those filters overrides it.</p>
     *
     * @param pageSize      the maximum number of records to return in a single page.
     * @param currentPage   the zero-based index of the page to retrieve.
     * @param groupName     (Optional) filter logs by the Quartz job group.
     * @param jobName       (Optional) filter logs by the Quartz job name.
     * @param triggerName   (Optional) filter logs by the name of the trigger that fired the job.
     * @param instanceClass (Optional) filter logs by the fully qualified class name of the job implementation.
     * @param success       (Optional) {@code true} for successful runs only, {@code false} for failed runs only.
     * @param from          (Optional) only runs that finished at or after this time.
     * @param to            (Optional) only runs that finished before this time.
     * @return a {@link Page} of {@link JobExecutionLog} matching the criteria.
     * @throws UnsupportedOperationException if a result or time filter is given and the implementation cannot
     *                                       apply it.
     * @since 1.0.5
     */
    default Page<JobExecutionLog> getJobExecutionLogList(final Integer pageSize,
                                                         final Integer currentPage,
                                                         @Nullable final String groupName,
                                                         @Nullable final String jobName,
                                                         @Nullable final String triggerName,
                                                         @Nullable final String instanceClass,
                                                         @Nullable final Boolean success,
                                                         @Nullable final Instant from,
                                                         @Nullable final Instant to) {
        if (success != null || from != null || to != null) {
            throw new UnsupportedOperationException(
                    getClass().getName() + " cannot filter execution logs by result or time");
        }
        return getJobExecutionLogList(pageSize, currentPage, groupName, jobName, triggerName, instanceClass);
    }

    /**
     * Aggregates the job executions that finished in {@code [from, to)} into consecutive buckets of
     * {@code bucketSize}, the last of which ends at {@code to} and may be shorter. Every bucket is returned, also
     * those without runs, oldest first.
     * <p>
     * The default implementation does not support statistics; both built-in implementations do.</p>
     *
     * @param from       the start of the first bucket, inclusive.
     * @param to         the end of the last bucket, exclusive; after {@code from}.
     * @param bucketSize the length of each bucket; positive.
     * @param groupName  (Optional) only runs of jobs in this group.
     * @param jobName    (Optional) only runs of jobs with this name.
     * @return one {@link JobExecutionStats} per bucket.
     * @throws UnsupportedOperationException if the implementation does not support statistics.
     * @since 1.0.5
     */
    default List<JobExecutionStats> getJobExecutionStats(final Instant from,
                                                         final Instant to,
                                                         final Duration bucketSize,
                                                         @Nullable final String groupName,
                                                         @Nullable final String jobName) {
        throw new UnsupportedOperationException(getClass().getName() + " does not provide execution statistics");
    }

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
