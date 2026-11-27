package org.quartzplus.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStatsAccumulatorTest {

    private static final Instant FROM = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void testBucketsCoverTheRangeWithAShorterLastBucket() {
        final var accumulator = new ExecutionStatsAccumulator(FROM, FROM.plusSeconds(250), Duration.ofSeconds(100));

        final var stats = accumulator.toStats();

        assertThat(stats).hasSize(3);
        assertThat(stats.get(0).getStart()).isEqualTo(FROM);
        assertThat(stats.get(1).getStart()).isEqualTo(FROM.plusSeconds(100));
        assertThat(stats.get(2).getStart()).isEqualTo(FROM.plusSeconds(200));
        assertThat(stats.get(2).getEnd()).isEqualTo(FROM.plusSeconds(250));
    }

    @Test
    void testRunsAreAddedToTheirBucketAndRunsOutsideTheRangeIgnored() {
        final var accumulator = new ExecutionStatsAccumulator(FROM, FROM.plusSeconds(200), Duration.ofSeconds(100));
        accumulator.addRun(FROM, true, 10);
        accumulator.addRun(FROM.plusSeconds(99), false, 30);
        accumulator.addRun(FROM.plusSeconds(100), true, 5);
        accumulator.addRun(FROM.minusMillis(1), true, 1000);
        accumulator.addRun(FROM.plusSeconds(200), true, 1000);

        final var stats = accumulator.toStats();

        assertThat(stats.get(0).getSuccessCount()).isEqualTo(1);
        assertThat(stats.get(0).getFailureCount()).isEqualTo(1);
        assertThat(stats.get(0).getTotalDuration()).isEqualTo(40);
        assertThat(stats.get(0).getMaxDuration()).isEqualTo(30);
        assertThat(stats.get(0).getAverageDuration()).isEqualTo(20);
        assertThat(stats.get(1).getSuccessCount()).isEqualTo(1);
        assertThat(stats.get(1).getMaxDuration()).isEqualTo(5);
    }

    @Test
    void testEmptyBucketHasZeroAverage() {
        final var stats = new ExecutionStatsAccumulator(FROM, FROM.plusSeconds(60), Duration.ofSeconds(60)).toStats();

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getAverageDuration()).isZero();
    }

    @Test
    void testRangeMustBePositiveAndBucketsLongerThanZero() {
        assertThatThrownBy(() -> new ExecutionStatsAccumulator(FROM, FROM, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExecutionStatsAccumulator(FROM, FROM.plusSeconds(1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
