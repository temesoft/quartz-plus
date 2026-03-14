package org.quartzplus.configuration;

import org.quartz.Scheduler;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * A Spring Boot {@link HealthIndicator} that monitors the status of the Quartz Scheduler.
 * <p>
 * This component provides real-time health data to the Spring Boot Actuator health endpoint,
 * checking if the scheduler is started, in standby, or shut down. It also exposes
 * operational metadata such as the Quartz version and clustering status.
 *
 * @see HealthIndicator
 * @see org.quartz.Scheduler
 */
@Component
public class QuartzPlusSchedulerHealthIndicator implements HealthIndicator {

    private final Scheduler quartzScheduler;

    /**
     * Constructs the health indicator with the provided Quartz {@link Scheduler}.
     *
     * @param quartzScheduler the scheduler instance to monitor.
     */
    public QuartzPlusSchedulerHealthIndicator(final Scheduler quartzScheduler) {
        this.quartzScheduler = quartzScheduler;
    }

    /**
     * Evaluates the health of the Quartz Scheduler.
     * <p>
     * The status is reported as:
     * <ul>
     *     <li><b>UP</b>: If the scheduler is initialized and started. Includes metadata
     *     details like version and clustering.</li>
     *     <li><b>DOWN</b>: If the scheduler is null, not started, or if an exception
     *     occurs during the check.</li>
     * </ul>
     *
     * @return a {@link Health} object containing status and detail information.
     */
    @Override
    public Health health() {
        final var builder = new Health.Builder();
        try {
            if (quartzScheduler != null) {
                if (quartzScheduler.isStarted()) {
                    return builder.up()
                            .withDetail("version", quartzScheduler.getMetaData().getVersion())
                            .withDetail("clustered", quartzScheduler.getMetaData().isJobStoreClustered())
                            .withDetail("shutdown", quartzScheduler.isShutdown())
                            .withDetail("standby", quartzScheduler.isInStandbyMode())
                            .build();
                } else {
                    return builder.down()
                            .withDetail("reason", "Quartz scheduler is not started")
                            .build();
                }
            }
            return builder.down().withDetail("reason", "Quartz scheduler is null").build();
        } catch (Exception ex) {
            // Spring Boot 4 prefers reporting the exception in the health detail
            return builder.down(ex).build();
        }
    }
}
