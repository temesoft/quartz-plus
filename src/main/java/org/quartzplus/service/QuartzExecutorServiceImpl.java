package org.quartzplus.service;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.quartz.Calendar;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.calendar.WeeklyCalendar;
import org.quartz.spi.JobFactory;
import org.quartzplus.CronTriggerable;
import org.quartzplus.Job;
import org.quartzplus.OnErrorRepeatable;
import org.quartzplus.SimpleTriggerable;
import org.quartzplus.TimeConstrainable;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.calendar.NoRestrictionsCalendar;
import org.quartzplus.configuration.Constants;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.JobRegistrationResult;
import org.quartzplus.exception.JobServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Core implementation of {@link QuartzExecutorService} for managing, scheduling, and logging Quartz jobs
 * within a Spring Boot environment.
 * <p>
 * This service acts as a high-level wrapper around the Quartz {@link Scheduler}, providing automated
 * job registration based on {@link JobSpec} annotations, dynamic expression evaluation for trigger
 * configurations, and comprehensive execution auditing via {@link JobExecutionLogService}.
 * </p>
 *
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 *   <li><b>Lifecycle Management:</b> Implements {@link Closeable} to ensure the scheduler is
 *       gracefully shut down when the application context is closed.</li>
 *   <li><b>Job Discovery:</b> Automatically registers jobs defined in {@code JobsCollection} beans
 *       during initialization.</li>
 *   <li><b>Dynamic Configuration:</b> Uses {@link ExpressionProcessor} to resolve cron expressions,
 *       intervals, and time zones at runtime.</li>
 *   <li><b>Auditing:</b> Attaches a global listener to track every job execution, capturing
 *       success/failure states and full stack traces.</li>
 * </ul>
 *
 * @see QuartzExecutorService
 * @see JobSpec
 * @see Closeable
 */
@Service
public class QuartzExecutorServiceImpl implements QuartzExecutorService, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzExecutorServiceImpl.class);
    private static final String JOB_SPEC_MEMBERS_REGEX = "[^a-zA-Z0-9_\\-]";

    private final ObjectMapper objectMapper;
    private final JobExecutionLogService jobExecutionLogService;
    private final AutowireCapableBeanFactory beanFactory;
    private final Environment environment;
    private final ExpressionProcessor expressionProcessor;
    private final Scheduler scheduler;
    private final ApplicationContext applicationContext;

    /**
     * Constructs a new {@code QuartzExecutorServiceImpl} with the necessary dependencies for
     * managing, scheduling, and logging Quartz jobs.
     * <p>
     * This constructor initializes the service by capturing the Spring {@link ApplicationContext}
     * and extracting specific components like the {@link AutowireCapableBeanFactory} and
     * {@link Environment} to support dynamic job instantiation and property resolution.
     * </p>
     *
     * @param jobExecutionLogService the service used to persist job execution results and history.
     * @param applicationContext     the Spring context used for bean discovery and environment access.
     * @param objectMapper           the {@link ObjectMapper} for serializing job data maps to JSON.
     * @param expressionProcessor    the processor used to evaluate dynamic expressions in job specifications.
     * @param scheduler              the Quartz {@link Scheduler} instance to be managed by this service.
     */
    public QuartzExecutorServiceImpl(final JobExecutionLogService jobExecutionLogService,
                                     final ApplicationContext applicationContext,
                                     final ObjectMapper objectMapper,
                                     final ExpressionProcessor expressionProcessor,
                                     final Scheduler scheduler) {
        this.jobExecutionLogService = jobExecutionLogService;
        this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        this.objectMapper = objectMapper;
        this.expressionProcessor = expressionProcessor;
        this.environment = applicationContext.getEnvironment();
        this.scheduler = scheduler;
        this.applicationContext = applicationContext;
    }

    /**
     * Closes the Quartz scheduler and releases any associated resources.
     * <p>
     * This method implementation of {@link AutoCloseable#close()} ensures an orderly
     * shutdown of the scheduler if it has been started. It prevents any new jobs
     * from being triggered and waits for currently executing jobs to complete
     * (depending on the underlying scheduler configuration).
     * </p>
     */
    @Override
    public void close() {
        try {
            if (scheduler != null && scheduler.isStarted()) {
                LOGGER.info("Shutting down quartz scheduler");
                scheduler.shutdown();
            }
        } catch (SchedulerException e) {
            LOGGER.error("Unable to shutdown quartz scheduler", e);
        }
    }

    /**
     * Initializes the Quartz scheduler by registering calendars, listeners, and job classes
     * discovered within the Spring application context.
     * <br/>
     * This initialization process performs the following steps:
     * <ol>
     *   <li>Registers specified {@link Calendar} instances into the scheduler.</li>
     *   <li>Retrieves all {@link JobsCollection} beans to build a master list of job classes.</li>
     *   <li>Attaches a global {@link JobListener} to handle execution logging (vetoes and completions).</li>
     *   <li>Configures the scheduler's {@link JobFactory}.</li>
     *   <li>Iterates through all discovered job classes, validates the presence of the {@link JobSpec}
     *       annotation, and registers them with the scheduler.</li>
     * </ol>
     *
     * @param scheduler  the Quartz {@link Scheduler} to initialize.
     * @param jobFactory the {@link JobFactory} used to instantiate job classes.
     * @param calendars  a list of fully qualified class names for {@link Calendar} implementations.
     * @throws JobServiceException       if a job class is missing the required {@link JobSpec} annotation.
     * @throws SchedulerException        if there is a failure in the Quartz scheduler operations.
     * @throws InstantiationException    if a job or calendar class cannot be instantiated.
     * @throws IllegalAccessException    if the class or its constructor is not accessible.
     * @throws InvocationTargetException if a constructor throws an exception during initialization.
     * @throws NoSuchMethodException     if a required default constructor is missing.
     */
    @Override
    public void initJobs(final Scheduler scheduler,
                         final JobFactory jobFactory,
                         final List<String> calendars
    ) throws SchedulerException, JobServiceException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        configureCalendars(scheduler, calendars);
        final var instanceId = scheduler.getSchedulerInstanceId();
        Map<String, JobsCollection> jobsCollections;
        try {
            jobsCollections = applicationContext.getBeansOfType(JobsCollection.class);
        } catch (final NoSuchBeanDefinitionException e) {
            LOGGER.info("JobsCollection bean is not defined, using empty job list instead");
            jobsCollections = Map.of();
        }

        final JobListener jobListener = new JobListener() {
            @Override
            public String getName() {
                return "QuartzExecutorService.JobListener";
            }

            @Override
            public void jobToBeExecuted(final JobExecutionContext jobExecutionContext) {
            }

            @Override
            public void jobExecutionVetoed(final JobExecutionContext jobExecutionContext) {
                insertJobExecutionLog(jobExecutionContext, new JobServiceException("Job was vetoed"), instanceId);
            }

            @Override
            public void jobWasExecuted(final JobExecutionContext jobExecutionContext, final JobExecutionException e) {
                insertJobExecutionLog(jobExecutionContext, e, instanceId);
            }
        };
        scheduler.getListenerManager().addJobListener(jobListener);
        scheduler.setJobFactory(jobFactory);

        final var jobsClasses = new ArrayList<Class<? extends Job>>();
        jobsCollections.values()
                .forEach(jobsCollection -> jobsClasses.addAll(jobsCollection.getJobClassList()));
        LOGGER.info("There are {} job classes available in JobsCollection job list", jobsClasses.size());
        for (final var jobClass : jobsClasses) {
            if (!jobClass.isAnnotationPresent(JobSpec.class)) {
                throw new JobServiceException("Job class " + jobClass.getName() + " should be an annotated with @" + JobSpec.class.getName());
            }
            registerJobClass(scheduler, jobClass, jobClass.getAnnotation(JobSpec.class), null, null);
        }
    }

    /**
     * Convenience method to register a job class with its default specifications.
     * <p>
     * This is an overloaded version of {@link #registerJobClass(Scheduler, Class, JobSpec, Map, Map)}
     * that performs registration without any additional job data or trigger parameter overrides.
     * </p>
     *
     * @param scheduler the Quartz {@link Scheduler} instance.
     * @param jobClass  the class implementing the Quartz {@link Job} interface.
     * @param jobSpec   the {@link JobSpec} metadata providing the job's configuration.
     * @return a {@link JobRegistrationResult} detailing the outcome of the registration.
     * @throws JobServiceException       if business validation fails.
     * @throws SchedulerException        if a scheduler-level error occurs.
     * @throws IllegalAccessException    if the job class constructor is inaccessible.
     * @throws InstantiationException    if the job class cannot be instantiated.
     * @throws InvocationTargetException if the constructor throws an exception.
     * @throws NoSuchMethodException     if a default constructor is not found.
     */
    @Override
    public JobRegistrationResult registerJobClass(final Scheduler scheduler,
                                                  final Class<? extends Job> jobClass,
                                                  final JobSpec jobSpec) throws JobServiceException, SchedulerException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException {
        return registerJobClass(scheduler, jobClass, jobSpec, null, null);
    }

    /**
     * Dynamically registers a job class with the Quartz scheduler based on the provided specifications
     * and optional parameter overrides.
     * <br/>
     * This method handles the end-to-end registration process, including:
     * <ul>
     *   <li>Instantiating the job class to check for triggerable interfaces or custom configurations.</li>
     *   <li>Evaluating {@link JobSpec} annotations for default job and trigger metadata.</li>
     *   <li>Applying runtime overrides for both job-level data and trigger-specific parameters.</li>
     *   <li>Creating or updating {@link JobDetail} and {@link Trigger} instances within the {@link Scheduler}.</li>
     * </ul>
     *
     * @param scheduler                 the Quartz {@link Scheduler} instance where the job will be registered.
     * @param jobClass                  the class implementing the Quartz {@link Job} interface to be registered.
     * @param jobSpec                   the {@link JobSpec} metadata providing the blueprint for the job's execution.
     * @param jobDataMap                a map of data to be persisted with the {@link JobDetail}.
     * @param triggerParameterOverrides a map of values used to override default trigger configurations at runtime.
     * @return a {@link JobRegistrationResult} indicating the status and details of the registration.
     * @throws JobServiceException       if a business-level validation or processing error occurs.
     * @throws SchedulerException        if there is a failure communicating with the Quartz Scheduler.
     * @throws IllegalAccessException    if the job class or its constructor is not accessible.
     * @throws InstantiationException    if the job class cannot be instantiated.
     * @throws NoSuchMethodException     if a required constructor is missing for the job class.
     * @throws InvocationTargetException if the job class constructor throws an exception.
     */
    @Override
    public JobRegistrationResult registerJobClass(final Scheduler scheduler,
                                                  final Class<? extends Job> jobClass,
                                                  final JobSpec jobSpec,
                                                  final Map<String, Object> jobDataMap,
                                                  final Map<String, Object> triggerParameterOverrides
    ) throws JobServiceException, SchedulerException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        if (!Job.class.isAssignableFrom(jobClass)) {
            throw new JobServiceException("Job class " + jobClass.getName() + " should extend class " + Job.class.getName());
        }
        if (jobSpec == null) {
            final var message = "Job class annotation @" + JobSpec.class.getSimpleName() + " not available: " + jobClass.getName();
            LOGGER.info(message);
            return new JobRegistrationResult(false, false, true, message, null, null);
        }

        final boolean enabled;
        final var enabledProperty = Optional.ofNullable(environment.getProperty(jobClass.getName() + ".enabled"));
        if (enabledProperty.isPresent()) {
            enabled = Boolean.parseBoolean(enabledProperty.get());
        } else {
            if (isNotBlank(jobSpec.triggerState().enabledExp())) {
                enabled = expressionProcessor.processExpression(jobSpec.triggerState().enabledExp(), Boolean.class);
            } else {
                enabled = jobSpec.triggerState().enabled() == TriggerState.State.ENABLED;
            }
        }
        if (!enabled) {
            final var message = "Job is disabled, skipping registration of: " + jobClass.getName();
            LOGGER.info(message);
            return new JobRegistrationResult(false, false, true, message, null, null);
        }

        final var jobInstance = jobClass.getDeclaredConstructor().newInstance();
        // in case the job bean is using spring SpEL, @Value or any other annotated parameters / methods
        // we need to auto-wire the job bean instance
        beanFactory.autowireBean(jobInstance);
        var trigger = scheduler.getTrigger(new TriggerKey(jobSpec.triggerName(), jobSpec.groupName()));
        // at this point if the trigger exists check its details, if they are different from annotation re-init trigger
        if (trigger != null) {
            if (checkIfResetTriggerNeeded(trigger, jobInstance, jobClass, jobSpec)) {
                LOGGER.info("Trigger details changed, resetting trigger {}", trigger.getKey().getName());
                scheduler.unscheduleJob(trigger.getKey());
                trigger = null;
            }
        }

        if (trigger == null) {
            final var p = Pattern.compile(JOB_SPEC_MEMBERS_REGEX);
            if (p.matcher(jobSpec.jobName()).find()) {
                throw new JobServiceException("JobName of job class " + jobClass.getName() + " contains illegal characters. Regex pattern is: " + JOB_SPEC_MEMBERS_REGEX);
            }
            if (p.matcher(jobSpec.groupName()).find()) {
                throw new JobServiceException("GroupName of job class " + jobClass.getName() + " contains illegal characters. Regex pattern is: " + JOB_SPEC_MEMBERS_REGEX);
            }
            if (p.matcher(jobSpec.triggerName()).find()) {
                throw new JobServiceException("TriggerName job class " + jobClass.getName() + " contains illegal characters. Regex pattern is: " + JOB_SPEC_MEMBERS_REGEX);
            }
            LOGGER.info("Scheduling job [{}] from {}", jobSpec.jobName(), jobClass.getName());
            final var job = JobBuilder.newJob(jobClass)
                    .withIdentity(jobSpec.jobName(), jobSpec.groupName())
                    .withDescription(jobSpec.jobDescription())
                    .storeDurably(false)
                    .build();
            final var description = new StringBuilder();
            final var descriptionOptions = Lists.<String>newArrayList();
            if (CronTriggerable.class.isAssignableFrom(jobClass) && SimpleTriggerable.class.isAssignableFrom(jobClass)) {
                throw new JobServiceException("Job class " + jobClass.getName()
                        + " should not implement both " + CronTriggerable.class.getName()
                        + " and " + SimpleTriggerable.class.getName());
            }

            final TriggerBuilder<?> triggerBuilder;
            if (CronTriggerable.class.isAssignableFrom(jobClass)) {
                final var cronTriggerable = (CronTriggerable) jobInstance;
                triggerBuilder = TriggerBuilder.newTrigger()
                        .withSchedule(CronScheduleBuilder
                                .cronSchedule(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_CRON_EXP, cronTriggerable.getCronExpression()))
                                .withMisfireHandlingInstructionDoNothing()
                                // only cron can have TimeZone
                                .inTimeZone(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_TIME_ZONE, cronTriggerable.getTriggerTimeZone())))
                        .withIdentity(jobSpec.triggerName(), jobSpec.groupName());
                description.append("Cron trigger for ").append(jobSpec.jobName());
            } else if (SimpleTriggerable.class.isAssignableFrom(jobClass)) {
                final var simpleTriggerable = (SimpleTriggerable) jobInstance;
                triggerBuilder = TriggerBuilder.newTrigger()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withRepeatCount(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_REPEAT_COUNT, simpleTriggerable.getRepeatCount()))
                                .withMisfireHandlingInstructionNowWithExistingCount()
                                .withIntervalInMilliseconds(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_REPEAT_INTERVAL, simpleTriggerable.getRepeatInterval())))
                        .withIdentity(jobSpec.triggerName(), jobSpec.groupName());
                description.append("Simple trigger for ").append(jobSpec.jobName());
            } else if (isNotBlank(jobSpec.trigger().cronTrigger().cronExpression()) || isNotBlank(jobSpec.trigger().cronTrigger().cronExpressionExp())) {
                final String cronExpression;
                if (isNotBlank(jobSpec.trigger().cronTrigger().cronExpressionExp())) {
                    cronExpression = expressionProcessor.processExpression(jobSpec.trigger().cronTrigger().cronExpressionExp(), String.class);
                } else {
                    cronExpression = jobSpec.trigger().cronTrigger().cronExpression();
                }
                final TimeZone timeZone;
                if (isNotBlank(jobSpec.trigger().cronTrigger().timeZoneExp())) {
                    timeZone = SimpleTimeZone.getTimeZone(expressionProcessor.processExpression(jobSpec.trigger().cronTrigger().timeZoneExp(), String.class));
                } else if (isNotBlank(jobSpec.trigger().cronTrigger().timeZone())) {
                    timeZone = SimpleTimeZone.getTimeZone(jobSpec.trigger().cronTrigger().timeZone());
                } else {
                    timeZone = SimpleTimeZone.getDefault();
                }
                triggerBuilder = TriggerBuilder.newTrigger()
                        .withSchedule(CronScheduleBuilder
                                .cronSchedule(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_CRON_EXP, cronExpression))
                                .withMisfireHandlingInstructionDoNothing()
                                // only cron can have TimeZone
                                .inTimeZone(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_TIME_ZONE, timeZone)))
                        .withIdentity(jobSpec.triggerName(), jobSpec.groupName());
                description.append("Cron trigger for ").append(jobSpec.jobName());
            } else if (jobSpec.trigger().simpleTrigger().repeatInterval() != 0 || isNotBlank(jobSpec.trigger().simpleTrigger().repeatIntervalExp())) {
                final long repeatInterval;
                if (isNotBlank(jobSpec.trigger().simpleTrigger().repeatIntervalExp())) {
                    repeatInterval = expressionProcessor.processExpression(jobSpec.trigger().simpleTrigger().repeatIntervalExp(), Long.class);
                } else {
                    repeatInterval = jobSpec.trigger().simpleTrigger().repeatInterval();
                }
                final int repeatCount;
                if (isNotBlank(jobSpec.trigger().simpleTrigger().repeatCountExp())) {
                    repeatCount = expressionProcessor.processExpression(jobSpec.trigger().simpleTrigger().repeatCountExp(), Integer.class);
                } else if (jobSpec.trigger().simpleTrigger().repeatCount() != 0) {
                    repeatCount = jobSpec.trigger().simpleTrigger().repeatCount();
                } else {
                    repeatCount = 0;
                }
                triggerBuilder = TriggerBuilder.newTrigger()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withRepeatCount(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_REPEAT_COUNT, repeatCount))
                                .withMisfireHandlingInstructionNowWithExistingCount()
                                .withIntervalInMilliseconds(checkForOverride(triggerParameterOverrides, Constants.PARAMETER_NAME_REPEAT_INTERVAL, repeatInterval)))
                        .withIdentity(jobSpec.triggerName(), jobSpec.groupName());
                description.append("Simple trigger for ").append(jobSpec.jobName());
            } else {
                throw new JobServiceException("Job class " + jobClass.getName()
                        + " should implement one of triggerable interfaces: "
                        + CronTriggerable.class.getName() + " or " + SimpleTriggerable.class.getName());
            }
            var startTime = Instant.now();
            if (TimeConstrainable.class.isAssignableFrom(jobClass)) {
                final TimeConstrainable timeConstrainable = (TimeConstrainable) jobInstance;
                descriptionOptions.add("TimeConstrainable");
                if (timeConstrainable.getStartTime() != null) {
                    startTime = timeConstrainable.getStartTime();
                    triggerBuilder.startAt(Date.from(timeConstrainable.getStartTime()));
                }
                if (timeConstrainable.getEndTime() != null) {
                    triggerBuilder.endAt(Date.from(timeConstrainable.getEndTime()));
                }
            }
            triggerBuilder.modifiedByCalendar(jobSpec.calendarClass());

            if (OnErrorRepeatable.class.isAssignableFrom(jobClass) || jobSpec.onError().onErrorRepeatCount() > 0) {
                descriptionOptions.add("OnErrorRepeatable");
            }

            if (!descriptionOptions.isEmpty()) {
                description.append(" ").append(descriptionOptions);
            }
            triggerBuilder.withDescription(description.toString());

            // Currently, there is no way to add a new job / trigger in paused state.
            // Here, we make sure that trigger will not execute between calls scheduler.scheduleJob(...) and scheduler.pauseTrigger(...)
            // by using trigger start time + 10 seconds
            final var now = Instant.now();
            final boolean startPaused;
            final var startPausedProperty = Optional.ofNullable(environment.getProperty(jobClass.getName() + ".startPaused", Boolean.class));
            if (startPausedProperty.isPresent()) {
                startPaused = startPausedProperty.get();
            } else if (isNotBlank(jobSpec.triggerState().startTypeExp())) {
                startPaused = expressionProcessor.processExpression(jobSpec.triggerState().startTypeExp(), String.class)
                        .equalsIgnoreCase(TriggerState.StartType.PAUSED.name());
            } else {
                startPaused = jobSpec.triggerState().startType() == TriggerState.StartType.PAUSED;
            }
            if (startPaused && (now.isAfter(startTime) || now.equals(startTime))) {
                triggerBuilder.startAt(Date.from(now.plus(1, ChronoUnit.SECONDS)));
            }
            trigger = triggerBuilder.build();
            // add data map if available
            if (jobDataMap != null && !jobDataMap.isEmpty()) {
                trigger.getJobDataMap().putAll(jobDataMap);
            }
            scheduler.scheduleJob(job, trigger);
            if (startPaused) {
                scheduler.pauseTrigger(trigger.getKey());
            }
            final var state = scheduler.getTriggerState(trigger.getKey());
            final var message = String.format("Scheduled job [%s] (current state: %s)", jobSpec.jobName(), state.name());
            LOGGER.info(message);
            return new JobRegistrationResult(true, false, false, message, trigger, job);
        } else {
            final var state = scheduler.getTriggerState(trigger.getKey());
            final var message = String.format("Job [%s] is already scheduled (current state: %s)", jobSpec.jobName(), state.name());
            LOGGER.info(message);
            return new JobRegistrationResult(false, true, false, message, trigger,
                    scheduler.getJobDetail(new JobKey(jobSpec.jobName(), jobSpec.groupName())));
        }
    }

    /**
     * Determines if a Quartz {@link Trigger} needs to be reset (re-scheduled) by comparing its current
     * configuration against the desired state defined in a {@link JobSpec} or a triggerable job instance.
     * <p>
     * This method evaluates differences in:
     * <ul>
     *   <li><b>Simple Triggers:</b> Repeat count, repeat interval, and associated calendar.</li>
     *   <li><b>Cron Triggers:</b> Cron expression, time zone, and associated calendar.</li>
     * </ul>
     * The desired state is derived either from the job instance itself (if it implements
     * {@code SimpleTriggerable} or {@code CronTriggerable}) or from the {@code JobSpec}
     * annotation, which may include dynamic expressions processed via an {@code expressionProcessor}.
     * </p>
     *
     * @param trigger     the existing {@link Trigger} currently registered in the scheduler.
     * @param jobInstance the actual instance of the job (checked for triggerable interfaces).
     * @param jobClass    the class of the job being evaluated.
     * @param jobSpec     the {@link JobSpec} metadata containing the target configuration.
     * @return {@code true} if the current trigger settings differ from the target configuration;
     * {@code false} otherwise.
     */
    private boolean checkIfResetTriggerNeeded(final Trigger trigger, final Object jobInstance, final Class<?> jobClass, final JobSpec jobSpec) {
        boolean resetTrigger = false;
        if (trigger instanceof SimpleTrigger && SimpleTriggerable.class.isAssignableFrom(jobClass)) {
            final var simpleTriggerable = (SimpleTriggerable) jobInstance;
            if (isNotEqual(((SimpleTrigger) trigger).getRepeatCount(), simpleTriggerable.getRepeatCount())
                    || isNotEqual(trigger.getCalendarName(), jobSpec.calendarClass())
                    || isNotEqual(((SimpleTrigger) trigger).getRepeatInterval(), simpleTriggerable.getRepeatInterval())) {
                resetTrigger = true;
            }
        } else if (trigger instanceof CronTrigger && CronTriggerable.class.isAssignableFrom(jobClass)) {
            final var cronTriggerable = (CronTriggerable) jobInstance;
            if (isNotEqual(((CronTrigger) trigger).getCronExpression(), cronTriggerable.getCronExpression())
                    || isNotEqual(trigger.getCalendarName(), jobSpec.calendarClass())
                    || isNotEqual(((CronTrigger) trigger).getTimeZone().getID(), cronTriggerable.getTriggerTimeZone().getID())) {
                resetTrigger = true;
            }
        } else if (trigger instanceof SimpleTrigger) {
            final long repeatInterval;
            if (isNotBlank(jobSpec.trigger().simpleTrigger().repeatIntervalExp())) {
                repeatInterval = expressionProcessor.processExpression(jobSpec.trigger().simpleTrigger().repeatIntervalExp(), Long.class);
            } else {
                repeatInterval = jobSpec.trigger().simpleTrigger().repeatInterval();
            }

            final int repeatCount;
            if (isNotBlank(jobSpec.trigger().simpleTrigger().repeatCountExp())) {
                repeatCount = expressionProcessor.processExpression(jobSpec.trigger().simpleTrigger().repeatCountExp(), Integer.class);
            } else {
                repeatCount = jobSpec.trigger().simpleTrigger().repeatCount();
            }

            if (isNotEqual(((SimpleTrigger) trigger).getRepeatCount(), repeatCount)
                    || isNotEqual(trigger.getCalendarName(), jobSpec.calendarClass())
                    || isNotEqual(((SimpleTrigger) trigger).getRepeatInterval(), repeatInterval)) {
                resetTrigger = true;
            }
        } else if (trigger instanceof CronTrigger) {
            final String cronExpression;
            if (isNotBlank(jobSpec.trigger().cronTrigger().cronExpressionExp())) {
                cronExpression = expressionProcessor.processExpression(jobSpec.trigger().cronTrigger().cronExpressionExp(), String.class);
            } else {
                cronExpression = jobSpec.trigger().cronTrigger().cronExpression();
            }
            final TimeZone timeZone;
            if (isNotBlank(jobSpec.trigger().cronTrigger().timeZoneExp())) {
                timeZone = SimpleTimeZone.getTimeZone(expressionProcessor.processExpression(jobSpec.trigger().cronTrigger().timeZoneExp(), String.class));
            } else if (isNotBlank(jobSpec.trigger().cronTrigger().timeZone())) {
                timeZone = SimpleTimeZone.getTimeZone(jobSpec.trigger().cronTrigger().timeZone());
            } else {
                timeZone = SimpleTimeZone.getDefault();
            }
            if (isNotEqual(((CronTrigger) trigger).getCronExpression(), cronExpression)
                    || isNotEqual(trigger.getCalendarName(), jobSpec.calendarClass())
                    || isNotEqual(((CronTrigger) trigger).getTimeZone().getID(), timeZone.getID())) {
                resetTrigger = true;
            }
        }
        return resetTrigger;
    }

    /**
     * Checks for a parameter override in the provided map and returns the overridden value if present.
     * <p>
     * This method searches the {@code parameterOverrides} map for a specific {@code parameterName}.
     * If a match is found, it returns the mapped value; otherwise, it returns the {@code currentValue}.
     * This is commonly used to dynamically replace default job parameters with execution-specific values.
     * </p>
     *
     * @param <T>                the expected type of the parameter.
     * @param parameterOverrides a map containing potential parameter overrides. Can be {@code null} or empty.
     * @param parameterName      the name of the parameter to check for an override.
     * @param currentValue       the default or current value to return if no override is found.
     * @return the overridden value from the map if it exists; otherwise, {@code currentValue}.
     * @throws ClassCastException if the value in the map cannot be cast to type {@code T}.
     */
    @SuppressWarnings("unchecked")
    private <T> T checkForOverride(final Map<String, Object> parameterOverrides,
                                   final String parameterName,
                                   final T currentValue) {
        if (parameterOverrides != null && !parameterOverrides.isEmpty() && parameterOverrides.containsKey(parameterName)) {
            return (T) parameterOverrides.get(parameterName);
        }
        return currentValue;
    }

    private boolean isNotEqual(final Object expected, final Object actual) {
        return !expected.equals(actual);
    }

    /**
     * Records the execution details of a Quartz job into the persistent execution log.
     * <p>
     * This method captures the final state of a job run, including:
     * <ul>
     *   <li>Trigger and Job metadata (group, name, priority).</li>
     *   <li>Success or failure status based on the presence of an {@link Exception}.</li>
     *   <li>The serialized {@code JobDataMap} as JSON.</li>
     *   <li>The combined execution trace, consisting of custom job output and stack traces.</li>
     * </ul>
     * The execution trace is passed through {@link #normalizeTrace(StringBuilder)} to ensure
     * compatibility by removing non-BMP characters before insertion.
     * </p>
     *
     * @param jobExecutionContext the context containing runtime information about the job.
     * @param e                   the {@link Exception} thrown during execution, or {@code null} if successful.
     * @param executionInstanceId a unique identifier for this specific execution instance.
     */
    private void insertJobExecutionLog(final JobExecutionContext jobExecutionContext,
                                       final Exception e,
                                       final String executionInstanceId) {
        final var trigger = jobExecutionContext.getTrigger();
        final var jobDetail = jobExecutionContext.getJobDetail();

        String errorMessage = "Ok";
        if (e != null) {
            errorMessage = e.getMessage();
            if (e.getCause() != null) {
                errorMessage = errorMessage + ": " + e.getCause().getMessage();
            }
        }

        final var outputStream = new ByteArrayOutputStream();
        final var writer = new PrintWriter(outputStream, true, UTF_8);
        final var jobDataMap = jobExecutionContext.getMergedJobDataMap();
        try {
            objectMapper.writeValue(writer, jobDataMap);
        } catch (final Exception ioException) {
            LOGGER.error("Unable to convert job data map to json", ioException);
        }

        final var trace = new StringBuilder();
        // if there was output created add it to the trace
        if (jobExecutionContext.getJobDetail().getJobDataMap().get(Constants.JOB_DATA_MAP_OUTPUT_PARAM) != null) {
            trace.append((String) jobExecutionContext.getJobDetail().getJobDataMap().get(Constants.JOB_DATA_MAP_OUTPUT_PARAM)).append("\n");
        }
        // if there was an exception also add it to the trace
        if (e != null) {
            trace.append(ExceptionUtils.getStackTrace(e)).append("\n");
        }
        final var log = new JobExecutionLog(
                null,
                trigger.getKey().getGroup(),
                trigger.getKey().getName(),
                jobDetail.getKey().getName(),
                trigger.getPriority(),
                (e == null),
                errorMessage,
                executionInstanceId,
                jobExecutionContext.getJobInstance().getClass().getName(),
                jobExecutionContext.getJobRunTime(),
                normalizeTrace(trace),
                outputStream.toString(UTF_8),
                Instant.now());

        try {
            jobExecutionLogService.insertJobExecutionLog(log);
        } catch (final Exception insertException) {
            LOGGER.error("Unable to insert job execution log: {}", insertException.getMessage());
        }
    }

    /**
     * Normalizes a stack trace or string by removing all Unicode surrogate characters.
     * <p>
     * This method iterates through the provided {@link StringBuilder} and filters out
     * high and low surrogate pairs (typically used in UTF-16 encoding for characters
     * outside the Basic Multilingual Plane).
     * </p>
     *
     * @param stringBuilder the {@link StringBuilder} containing the raw trace to normalize.
     * @return a {@link String} containing only the non-surrogate characters from the input.
     */
    private String normalizeTrace(final StringBuilder stringBuilder) {
        final var sb = new StringBuilder();
        for (var i = 0; i < stringBuilder.length(); i++) {
            final var ch = stringBuilder.charAt(i);
            if (!Character.isHighSurrogate(ch) && !Character.isLowSurrogate(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Configures and registers a list of Quartz {@link Calendar} instances into the provided scheduler.
     * <p>
     * This method initializes a set of default calendars ({@link WeeklyCalendar} and
     * {@link NoRestrictionsCalendar}) and appends any additional calendar class names
     * provided in the {@code calendars} list. Each class is instantiated via reflection
     * using its default constructor.
     * </p>
     *
     * @param scheduler the Quartz {@link Scheduler} where the calendars will be registered.
     * @param calendars a {@link List} of fully qualified class names of additional
     *                  {@link Calendar} implementations to register. Can be null or empty.
     * @throws RuntimeException if a critical reflection error occurs, though specific
     *                          instantiation errors are caught and logged as warnings.
     */
    private void configureCalendars(final Scheduler scheduler,
                                    final List<String> calendars) {
        final var stopwatch = StopWatch.createStarted();
        final var calendarClasses = Lists.<String>newArrayList();
        calendarClasses.add(WeeklyCalendar.class.getName());
        calendarClasses.add(NoRestrictionsCalendar.class.getName());
        if (isNotEmpty(calendars)) {
            calendarClasses.addAll(calendars);
        }
        for (final var calendarClass : calendarClasses) {
            try {
                final var calendar = Class.forName(calendarClass)
                        .asSubclass(Calendar.class)
                        .getDeclaredConstructor()
                        .newInstance();
                LOGGER.info("Adding calendar class to scheduler: {}", calendarClass);
                scheduler.addCalendar(calendarClass, calendar, true, true);
            } catch (final Exception e) {
                LOGGER.warn("Unable to add calendar class. {}", e.toString());
            }
        }
        LOGGER.debug("Finished registering available calendars in {}", stopwatch.getDuration());
    }
}
