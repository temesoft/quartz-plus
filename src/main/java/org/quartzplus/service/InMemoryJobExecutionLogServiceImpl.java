package org.quartzplus.service;

import jakarta.annotation.Nullable;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.collections4.queue.SynchronizedQueue;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.JobExecutionStats;
import org.quartzplus.domain.QuartzExecutionNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * In-memory implementation of the {@link JobExecutionLogService} that uses a circular buffer to store logs.
 * <p>This implementation is ideal for development environments or lightweight applications where persistent
 * history is not required. It utilizes a thread-safe {@link CircularFifoQueue} to automatically
 * evict the oldest log entries once the maximum size is reached.</p>
 * <p>Note: Clustering support ({@link #getQuartzExecutionNodeList()}) and database table prefixes
 * are not supported by this volatile implementation.</p>
 *
 * @see JobExecutionLogService
 * @see CircularFifoQueue
 * @see SynchronizedQueue
 */
public class InMemoryJobExecutionLogServiceImpl implements JobExecutionLogService {

    private final Queue<JobExecutionLog> EXECUTION_LOGS;

    /**
     * Constructs a new service instance with a fixed maximum buffer size.
     *
     * @param maxSize the maximum number of {@link JobExecutionLog} entries to retain.
     */
    public InMemoryJobExecutionLogServiceImpl(final int maxSize) {
        EXECUTION_LOGS = SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxSize));
    }

    /**
     * Filters and retrieves a paged list of logs from memory, newest first.
     * <p>
     * Filtering is performed via stream predicates. If a filter parameter is blank or null,
     * it is ignored during the search.
     *
     * @param pageSize      the number of logs per page.
     * @param currentPage   the current page index (zero-based).
     * @param groupName     (Optional) filter by job group.
     * @param jobName       (Optional) filter by job name.
     * @param triggerName   (Optional) filter by trigger name.
     * @param instanceClass (Optional) filter by the job class name.
     * @return a {@link Page} containing the filtered logs.
     */
    @Override
    public Page<JobExecutionLog> getJobExecutionLogList(final Integer pageSize,
                                                        final Integer currentPage,
                                                        @Nullable final String groupName,
                                                        @Nullable final String jobName,
                                                        @Nullable final String triggerName,
                                                        @Nullable final String instanceClass) {
        return getJobExecutionLogList(pageSize, currentPage, groupName, jobName, triggerName, instanceClass, null, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<JobExecutionLog> getJobExecutionLogList(final Integer pageSize,
                                                        final Integer currentPage,
                                                        @Nullable final String groupName,
                                                        @Nullable final String jobName,
                                                        @Nullable final String triggerName,
                                                        @Nullable final String instanceClass,
                                                        @Nullable final Boolean success,
                                                        @Nullable final Instant from,
                                                        @Nullable final Instant to) {
        final var list = snapshot().stream()
                .filter(log -> !isNotBlank(groupName) || log.getGroupName().equals(groupName))
                .filter(log -> !isNotBlank(jobName) || log.getJobName().equals(jobName))
                .filter(log -> !isNotBlank(triggerName) || log.getTriggerName().equals(triggerName))
                .filter(log -> !isNotBlank(instanceClass) || log.getInstanceClass().equals(instanceClass))
                .filter(log -> success == null || success.equals(log.getSuccess()))
                .filter(log -> from == null || !log.getCreateTime().isBefore(from))
                .filter(log -> to == null || log.getCreateTime().isBefore(to))
                .sorted(Comparator.comparing(JobExecutionLog::getCreateTime).reversed())
                .toList();
        final var first = Math.min((long) currentPage * pageSize, list.size());
        final var last = Math.min(first + pageSize, list.size());
        return new PageImpl<>(list.subList((int) first, (int) last), PageRequest.of(currentPage, pageSize), list.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<JobExecutionStats> getJobExecutionStats(final Instant from,
                                                        final Instant to,
                                                        final Duration bucketSize,
                                                        @Nullable final String groupName,
                                                        @Nullable final String jobName) {
        final var accumulator = new ExecutionStatsAccumulator(from, to, bucketSize);
        snapshot().stream()
                .filter(log -> !isNotBlank(groupName) || log.getGroupName().equals(groupName))
                .filter(log -> !isNotBlank(jobName) || log.getJobName().equals(jobName))
                .forEach(log -> accumulator.addRun(log.getCreateTime(), Boolean.TRUE.equals(log.getSuccess()), log.getDuration()));
        return accumulator.toStats();
    }

    // a copy taken under the queue's lock: iterating the synchronized queue itself is not thread safe
    private List<JobExecutionLog> snapshot() {
        synchronized (EXECUTION_LOGS) {
            return List.copyOf(EXECUTION_LOGS);
        }
    }

    /**
     * Searches the in-memory queue for a log entry with the specified unique ID.
     *
     * @param jobExecutionLogId the UUID of the log entry.
     * @return an {@link Optional} containing the log if found.
     */
    @Override
    public Optional<JobExecutionLog> getJobExecutionLog(final String jobExecutionLogId) {
        return EXECUTION_LOGS.stream().filter(log -> log.getId().equals(jobExecutionLogId)).findFirst();
    }

    /**
     * Adds a new log entry to the buffer.
     * If the buffer is full, the oldest entry is automatically evicted.
     *
     * @param jobExecutionLog the log entry to store.
     */
    @Override
    public void insertJobExecutionLog(final JobExecutionLog jobExecutionLog) {
        EXECUTION_LOGS.add(jobExecutionLog);
    }

    /**
     * Removes log entries from the buffer that were created before the specified timestamp.
     *
     * @param since the cutoff timestamp for removal.
     * @return the total number of entries removed from memory.
     */
    @Override
    public int clearJobExecutionLog(final Instant since) {
        final var itemsToRemove = EXECUTION_LOGS.stream()
                .filter(jobExecutionLog -> jobExecutionLog.getCreateTime().isBefore(since))
                .toList();
        if (itemsToRemove.isEmpty()) {
            return 0;
        }
        EXECUTION_LOGS.removeAll(itemsToRemove);
        return itemsToRemove.size();
    }

    /**
     * This implementation does not track cluster nodes.
     *
     * @return an empty list.
     */
    @Override
    public List<QuartzExecutionNode> getQuartzExecutionNodeList() {
        return List.of();
    }

    /**
     * This implementation does not use database tables.
     *
     * @return an empty string.
     */
    @Override
    public String getTablePrefix() {
        return "";
    }
}
