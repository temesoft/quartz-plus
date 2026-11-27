package org.quartzplus.service;

import org.quartzplus.domain.JobExecutionStats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits {@code [from, to)} into buckets of equal length (the last one may be shorter) and adds job runs up per
 * bucket, for the built-in {@link JobExecutionLogService} implementations.
 *
 * @since 1.0.5
 */
final class ExecutionStatsAccumulator {

    private final Instant from;
    private final Instant to;
    private final long bucketMillis;
    private final long[] successCounts;
    private final long[] failureCounts;
    private final long[] totalDurations;
    private final long[] maxDurations;

    ExecutionStatsAccumulator(final Instant from, final Instant to, final Duration bucketSize) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (bucketSize.toMillis() <= 0) {
            throw new IllegalArgumentException("bucketSize must be positive");
        }
        this.from = from;
        this.to = to;
        this.bucketMillis = bucketSize.toMillis();
        final var range = to.toEpochMilli() - from.toEpochMilli();
        final var count = (int) ((range + bucketMillis - 1) / bucketMillis);
        this.successCounts = new long[count];
        this.failureCounts = new long[count];
        this.totalDurations = new long[count];
        this.maxDurations = new long[count];
    }

    int bucketCount() {
        return successCounts.length;
    }

    Instant bucketStart(final int bucket) {
        return from.plusMillis(bucket * bucketMillis);
    }

    Instant bucketEnd(final int bucket) {
        final var end = bucketStart(bucket + 1);
        return end.isAfter(to) ? to : end;
    }

    /**
     * Adds one run; a run that finished outside {@code [from, to)} is ignored.
     */
    void addRun(final Instant finished, final boolean success, final long duration) {
        if (finished.isBefore(from) || !finished.isBefore(to)) {
            return;
        }
        final var bucket = (int) ((finished.toEpochMilli() - from.toEpochMilli()) / bucketMillis);
        addBucket(bucket, success ? 1 : 0, success ? 0 : 1, duration, duration);
    }

    /**
     * Adds runs already aggregated for one bucket.
     */
    void addBucket(final int bucket, final long successes, final long failures, final long totalDuration, final long maxDuration) {
        successCounts[bucket] += successes;
        failureCounts[bucket] += failures;
        totalDurations[bucket] += totalDuration;
        maxDurations[bucket] = Math.max(maxDurations[bucket], maxDuration);
    }

    List<JobExecutionStats> toStats() {
        final var result = new ArrayList<JobExecutionStats>(bucketCount());
        for (var bucket = 0; bucket < bucketCount(); bucket++) {
            result.add(new JobExecutionStats(
                    bucketStart(bucket),
                    bucketEnd(bucket),
                    successCounts[bucket],
                    failureCounts[bucket],
                    totalDurations[bucket],
                    maxDurations[bucket]
            ));
        }
        return result;
    }
}
