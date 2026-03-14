package org.quartzplus.configuration;

import com.google.common.collect.Maps;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import org.quartzplus.resource.SchedulerRestController;
import org.quartzplus.service.DataSourceJobExecutionLogServiceImpl;
import org.quartzplus.service.ExpressionProcessor;
import org.quartzplus.service.ExpressionProcessorImpl;
import org.quartzplus.service.InMemoryJobExecutionLogServiceImpl;
import org.quartzplus.service.JobExecutionLogService;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.service.QuartzExecutorService;
import org.quartzplus.service.QuartzExecutorServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Core configuration class for QuartzPlus, defining common beans, infrastructure, and integration points.
 * <p>
 * This class handles the setup of critical components including:
 * <ul>
 *     <li>Custom {@link JobFactory} for Spring bean autowiring within Quartz jobs.</li>
 *     <li>Log storage strategies (In-memory vs. JDBC) via {@link JobExecutionLogService}.</li>
 *     <li>JSON serialization support for Spring 7 / Jackson 3.</li>
 *     <li>Management endpoints and expression processing for dynamic job scheduling.</li>
 * </ul>
 * It also imports web administration and REST controller configurations and loads
 * versioning metadata from {@code quartz-plus-git.properties}.
 */
@Configuration
@Import({
        QuartzPlusWebAdminConfiguration.class,
        QuartzPlusFlywayConfiguration.class,
        SchedulerRestController.class
})
@PropertySource("quartz-plus-git.properties")
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@EnableConfigurationProperties(QuartzPlusProperties.class)
public class QuartzPlusCommonConfiguration {

    private final ApplicationContext applicationContext;
    private final Scheduler scheduler;
    private final QuartzProperties quartzProperties;
    private final QuartzPlusProperties quartzPlusProperties;

    /**
     * Constructs the configuration with required Spring-managed dependencies.
     *
     * @param applicationContext   the current Spring application context.
     * @param scheduler            the Quartz scheduler instance.
     * @param quartzProperties     standard Spring Boot Quartz properties.
     * @param quartzPlusProperties Quartz-Plus specific properties.
     */
    public QuartzPlusCommonConfiguration(final ApplicationContext applicationContext,
                                         final Scheduler scheduler,
                                         final QuartzProperties quartzProperties,
                                         final QuartzPlusProperties quartzPlusProperties) {
        this.applicationContext = applicationContext;
        this.scheduler = scheduler;
        this.quartzProperties = quartzProperties;
        this.quartzPlusProperties = quartzPlusProperties;
    }

    /**
     * Creates a {@link JobFactory} that supports dependency injection into Quartz Job instances.
     *
     * @return a {@link AutowiringSpringBeanJobFactory} instance.
     */
    @Bean
    @ConditionalOnMissingBean
    JobFactory jobFactory() {
        final var factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(applicationContext);
        final var context = new SchedulerContext();
        factory.setSchedulerContext(context);
        return factory;
    }

    /**
     * Configures a Jackson 3 based message converter for handling JSON web requests.
     * Disables failure on empty beans to ensure stability during serialization.
     *
     * @return a configured {@link JacksonJsonHttpMessageConverter}.
     */
    @Bean
    public JacksonJsonHttpMessageConverter mappingJacksonHttpMessageConverter() {
        // Use Jackson 3's native immutable builder instead of FactoryBeans
        var jsonMapper = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .build();

        // The new Jackson 3 converter from Spring Framework 7
        var converter = new JacksonJsonHttpMessageConverter(jsonMapper);
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));

        return converter;
    }

    /**
     * Ensures the scheduler is shut down gracefully when the application context is destroyed.
     *
     * @throws SchedulerException if shutdown fails.
     */
    @PreDestroy
    public void destroy() throws SchedulerException {
        scheduler.shutdown(quartzProperties.isWaitForJobsToCompleteOnShutdown());
    }

    /**
     * An extension of {@link SpringBeanJobFactory} that performs autowiring on
     * created job instances using the Spring {@link AutowireCapableBeanFactory}.
     */
    static final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

        private transient AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(final ApplicationContext context) {
            beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        public Job newJob(final TriggerFiredBundle triggerFiredBundle, final Scheduler scheduler) throws SchedulerException {
            Object job = null;
            try {
                job = super.createJobInstance(triggerFiredBundle);
                beanFactory.autowireBean(job);
            } catch (final Exception e) {
                throw new SchedulerException(String.format("Unable to auto-wire new job bean: %s", job), e);
            }
            return (Job) job;
        }
    }

    /**
     * Provides a default {@link MeterRegistry} if none exists, enabling basic metrics collection.
     */
    @Bean
    @ConditionalOnMissingBean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Determines and configures the appropriate {@link JobExecutionLogService}.
     * <p>
     * Switches to an in-memory implementation if specifically enabled via properties
     * or if no {@link DataSource} is available. Otherwise, uses a JDBC-based implementation.
     *
     * @param dataSource  the database connection, if available.
     * @param tablePrefix the database table prefix for logging.
     * @return a concrete {@link JobExecutionLogService}.
     */
    @Bean
    @ConditionalOnMissingBean
    JobExecutionLogService jdbcJobService(@Nullable final DataSource dataSource,
                                          @Value("${spring.flyway.placeholders.executionLogTablePrefix:QRTZ_}") final String tablePrefix) {
        if (dataSource == null
                || quartzPlusProperties.getJobExecutionLog().getType() == QuartzPlusProperties.JobExecutionLogType.InMemory) {
            return new InMemoryJobExecutionLogServiceImpl(quartzPlusProperties.getJobExecutionLog().getInMemoryMaxSize());
        } else {
            return new DataSourceJobExecutionLogServiceImpl(new JdbcTemplate(dataSource), tablePrefix);
        }
    }

    /**
     * Creates an {@link ExpressionProcessor} for evaluating dynamic job parameters.
     */
    @Bean
    @ConditionalOnMissingBean
    ExpressionProcessor expressionProcessor(final ConfigurableApplicationContext ctx,
                                            final ConversionService conversionService) {
        return new ExpressionProcessorImpl(ctx, conversionService);
    }

    /**
     * Creates the primary {@link QuartzExecutorService} for job orchestration.
     */
    @Bean
    @ConditionalOnMissingBean
    QuartzExecutorService quartzExecutorService(final JobExecutionLogService jobExecutionLogService,
                                                final ObjectMapper objectMapper,
                                                final ExpressionProcessor expressionProcessor,
                                                final Scheduler quartzScheduler) {
        return new QuartzExecutorServiceImpl(
                jobExecutionLogService,
                applicationContext,
                objectMapper,
                expressionProcessor,
                quartzScheduler
        );
    }

    /**
     * Configures the management endpoint for monitoring and administrative actions.
     */
    @Bean
    @ConditionalOnMissingBean
    QuartzPlusManagementEndpoint quartzManagementEndpoint(
            final Scheduler quartzScheduler,
            final List<JobsCollection> jobsCollections,
            final JobExecutionLogService jobExecutionLogService,
            @Value("${quartz-plus-git.build.version:N/A}") final String quartzPlusVersion) {
        return new QuartzPlusManagementEndpoint(
                quartzScheduler,
                jobsCollections,
                jobExecutionLogService,
                quartzPlusVersion,
                quartzPlusProperties.getWebAdmin().isEnabled()
                        ? quartzPlusProperties.getWebAdmin().getUri() : null,
                quartzPlusProperties.getApiUri()
        );
    }

    /**
     * Scans the environment for configuration properties related to Quartz and QuartzPlus.
     * Filters for keys starting with {@code org.quartz.}, {@code quartzplus.}, etc.
     *
     * @param environment the Spring environment to inspect.
     * @return a filtered {@link Map} of configuration keys and values.
     */
    public static Map<String, Object> getConfigProperties(final Environment environment) {
        final var map = Maps.<String, Object>newHashMap();
        for (final var propertySource : ((AbstractEnvironment) environment).getPropertySources()) {
            if (propertySource instanceof MapPropertySource) {
                map.putAll(((MapPropertySource) propertySource).getSource());
            }
        }
        final var result = Maps.<String, Object>newTreeMap();
        for (final var key : map.keySet()) {
            if (key != null) {
                if (key.startsWith("org.quartz.")
                        || key.startsWith("quartz-plus.")
                        || key.startsWith("org.quartzplus.")
                        || key.startsWith("spring.quartz.")
                        || key.startsWith("spring.flyway.placeholders.quartzTablePrefix")
                        || key.startsWith("spring.flyway.placeholders.executionLogTablePrefix")) {
                    result.put(key, environment.getProperty(key));
                }
            }
        }
        return result;
    }
}
