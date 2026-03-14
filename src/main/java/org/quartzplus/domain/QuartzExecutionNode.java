package org.quartzplus.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a single execution node within a clustered Quartz environment.
 * <p>
 * This class tracks the identity and heartbeat status of a scheduler instance. It is used
 * to determine if a specific node in the cluster is currently active based on its last
 * check-in time and the expected check-in interval.
 *
 * @see java.io.Serializable
 */
public class QuartzExecutionNode implements Serializable {

    private final String schedulerName;
    private final String instanceName;
    private final Instant lastCheckInTime;
    private final Long checkInInterval;

    /**
     * Constructs a new representation of a Quartz execution node.
     *
     * @param schedulerName   the logical name of the scheduler.
     * @param instanceName    the unique identifier for this specific node instance.
     * @param lastCheckInTime the timestamp of the most recent heartbeat recorded by this node.
     * @param checkInInterval the maximum time (in milliseconds) allowed between heartbeats
     *                        before the node is considered offline.
     */
    public QuartzExecutionNode(final String schedulerName,
                               final String instanceName,
                               final Instant lastCheckInTime,
                               final Long checkInInterval) {
        this.schedulerName = schedulerName;
        this.instanceName = instanceName;
        this.lastCheckInTime = lastCheckInTime;
        this.checkInInterval = checkInInterval;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("schedulerName", schedulerName)
                .append("instanceName", instanceName)
                .append("lastCheckInTime", lastCheckInTime)
                .append("checkInInterval", checkInInterval)
                .toString();
    }

    public String getSchedulerName() {
        return schedulerName;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public Instant getLastCheckInTime() {
        return lastCheckInTime;
    }

    public Long getCheckInInterval() {
        return checkInInterval;
    }

    /**
     * Determines if the node is currently "Ready" (active).
     * <p>
     * A node is considered ready if the duration since its {@link #lastCheckInTime}
     * does not exceed the {@link #checkInInterval}.
     *
     * @return {@code true} if the node has checked in within the allowed interval;
     * {@code false} otherwise.
     */
    public boolean isReady() {
        return Instant.now().toEpochMilli() - lastCheckInTime.toEpochMilli() <= checkInInterval;
    }
}
