package org.quartzplus.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.Instant;

/**
 * Aggregated job executions of one time bucket: how many runs finished in {@code [start, end)}, how many of them
 * succeeded and failed, and how long they took.
 *
 * @since 1.0.5
 */
public class JobExecutionStats {

    private final Instant start;
    private final Instant end;
    private final long successCount;
    private final long failureCount;
    private final long totalDuration;
    private final long maxDuration;

    /**
     * Constructs the statistics of one time bucket.
     *
     * @param start         the start of the bucket, inclusive.
     * @param end           the end of the bucket, exclusive.
     * @param successCount  the number of runs that succeeded.
     * @param failureCount  the number of runs that failed.
     * @param totalDuration the sum of the durations of all runs, in milliseconds.
     * @param maxDuration   the longest duration of a run, in milliseconds; {@code 0} without runs.
     */
    public JobExecutionStats(final Instant start,
                             final Instant end,
                             final long successCount,
                             final long failureCount,
                             final long totalDuration,
                             final long maxDuration) {
        this.start = start;
        this.end = end;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.totalDuration = totalDuration;
        this.maxDuration = maxDuration;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public long getFailureCount() {
        return failureCount;
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    public long getMaxDuration() {
        return maxDuration;
    }

    /**
     * Returns the mean duration of the bucket's runs.
     *
     * @return the mean duration in milliseconds, or {@code 0} without runs.
     */
    public long getAverageDuration() {
        final var count = successCount + failureCount;
        return count == 0 ? 0 : totalDuration / count;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("start", start)
                .append("end", end)
                .append("successCount", successCount)
                .append("failureCount", failureCount)
                .append("totalDuration", totalDuration)
                .append("maxDuration", maxDuration)
                .toString();
    }
}
