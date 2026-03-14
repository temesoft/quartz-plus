package org.quartzplus.service;

import org.quartz.Calendar;
import org.quartz.JobDetail;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.spi.JobFactory;
import org.quartzplus.Job;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.domain.JobRegistrationResult;
import org.quartzplus.exception.JobServiceException;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * Interface for managing, scheduling, and logging Quartz jobs within a Spring Boot environment.
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
public interface QuartzExecutorService {

    /**
     * Initializes the Quartz scheduler by registering calendars, listeners, and job classes
     * discovered within the Spring application context.
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
    void initJobs(final Scheduler scheduler,
                  final JobFactory jobFactory,
                  final List<String> calendars
    ) throws SchedulerException, ClassNotFoundException, ParseException, JobServiceException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException;

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
    JobRegistrationResult registerJobClass(final Scheduler scheduler,
                                           final Class<? extends Job> jobClass,
                                           final JobSpec jobSpec
    ) throws JobServiceException, SchedulerException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException;

    /**
     * Dynamically registers a job class with the Quartz scheduler based on the provided specifications
     * and optional parameter overrides.
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
    JobRegistrationResult registerJobClass(
            final Scheduler scheduler,
            final Class<? extends Job> jobClass,
            final JobSpec jobSpec,
            final Map<String, Object> jobDataMap,
            final Map<String, Object> triggerParameterOverrides
    ) throws JobServiceException, SchedulerException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException;
}