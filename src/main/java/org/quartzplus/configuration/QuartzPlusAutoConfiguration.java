package org.quartzplus.configuration;

import jakarta.annotation.PreDestroy;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartzplus.service.QuartzExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Autoconfiguration for QuartzPlus, facilitating the setup and lifecycle management of the Quartz Scheduler.
 * <p>
 * This configuration ensures that Quartz starts after primary data sources, JPA, and Flyway migrations
 * are completed. it listens for the {@link ContextRefreshedEvent} to initialize jobs and start the scheduler
 * once the application context is fully ready.
 *
 * @see QuartzPlusProperties
 * @see QuartzPlusCommonConfiguration
 */
@AutoConfiguration(
        after = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@Import({QuartzPlusCommonConfiguration.class})
@EnableConfigurationProperties(QuartzPlusProperties.class)
public class QuartzPlusAutoConfiguration implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzPlusAutoConfiguration.class);

    @Autowired
    private QuartzProperties quartzProperties;
    @Autowired
    private QuartzPlusProperties quartzPlusProperties;
    @Autowired
    private Scheduler scheduler;

    private QuartzExecutorService quartzExecutorService = null;
    private JobFactory jobFactory = null;

    /**
     * Shuts down the {@link Scheduler} before the bean is destroyed.
     * Respects the {@code spring.quartz.wait-for-jobs-to-complete-on-shutdown} property setting.
     *
     * @throws SchedulerException if there is an error during scheduler shutdown.
     */
    @PreDestroy
    public void destroy() throws SchedulerException {
        scheduler.shutdown(quartzProperties.isWaitForJobsToCompleteOnShutdown());
    }

    /**
     * Handles the application startup logic. Starts the Quartz scheduler if it hasn't
     * been started yet and initializes jobs using the {@link QuartzExecutorService}.
     *
     * @param contextRefreshedEvent the event triggered when the ApplicationContext is refreshed.
     * @throws RuntimeException if the scheduler fails to start or jobs fail to initialize.
     */
    @Override
    public void onApplicationEvent(final ContextRefreshedEvent contextRefreshedEvent) {
        try {
            if (!scheduler.isStarted()) {
                LOGGER.info("Starting up quartz scheduler");
                scheduler.start();
            }
            quartzExecutorService.initJobs(scheduler, jobFactory, quartzPlusProperties.getCalendars());
        } catch (final Exception e) {
            throw new RuntimeException("Unable to start quartz scheduler", e);
        }
    }

    /**
     * Injects the service responsible for job initialization.
     *
     * @param quartzExecutorService the executor service to use.
     */
    @Autowired
    public void setQuartzExecutorService(final QuartzExecutorService quartzExecutorService) {
        this.quartzExecutorService = quartzExecutorService;
    }

    /**
     * Injects the factory used to create Quartz job instances.
     *
     * @param jobFactory the job factory to use.
     */
    @Autowired
    public void setJobFactory(final JobFactory jobFactory) {
        this.jobFactory = jobFactory;
    }
}
