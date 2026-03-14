package org.quartzplus.service;

import jakarta.annotation.Nullable;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.collections4.queue.SynchronizedQueue;
import org.apache.commons.lang3.stream.Streams;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.QuartzExecutionNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
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
     * Filters and retrieves a paged list of logs from memory.
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
        final var list = Streams.of(EXECUTION_LOGS).filter(jobExecutionLog -> {
            if (isNotBlank(groupName)) {
                if (!jobExecutionLog.getGroupName().equals(groupName)) {
                    return false;
                }
            }
            if (isNotBlank(jobName)) {
                if (!jobExecutionLog.getJobName().equals(jobName)) {
                    return false;
                }
            }
            if (isNotBlank(triggerName)) {
                if (!jobExecutionLog.getTriggerName().equals(triggerName)) {
                    return false;
                }
            }
            if (isNotBlank(instanceClass)) {
                if (!jobExecutionLog.getInstanceClass().equals(instanceClass)) {
                    return false;
                }
            }
            return true;
        }).toList();
        return new PageImpl<>(list, PageRequest.of(currentPage, pageSize), list.size());
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
