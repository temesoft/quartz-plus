package org.quartzplus;

import ch.qos.logback.classic.Level;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.time.StopWatch;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.configuration.LogCaptureServiceImpl;
import org.quartzplus.service.ExpressionProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.quartzplus.JobMetricTypeEnum.DURATION;
import static org.quartzplus.JobMetricTypeEnum.FAILURE;
import static org.quartzplus.JobMetricTypeEnum.SUCCESS;
import static org.quartzplus.configuration.Constants.JOB_DATA_MAP_OUTPUT_PARAM;

/**
 * Base abstract class for all QuartzPlus jobs, extending {@link QuartzJobBean} to provide
 * enhanced lifecycle management, metrics collection, and automatic error handling.
 * <p>
 * This class orchestrates the execution of business logic defined in {@link #executeJob(JobExecutionContext)}
 * by adding the following features:
 * <ul>
 *     <li><b>Metrics:</b> Automatic recording of job duration, success counts, and failure counts via Micrometer.</li>
 *     <li><b>Log Capture:</b> Intercepts SLF4J logs generated during the job's execution to be stored in the execution history.</li>
 *     <li><b>Retry Logic:</b> Supports automatic retries with back-off, configured either via the {@link JobSpec} annotation
 *     or the {@link OnErrorRepeatable} interface.</li>
 *     <li><b>Lifecycle Hooks:</b> Provides {@link #onSuccess(JobExecutionContext)} and {@link #onFailure(JobExecutionContext, Throwable)}
 *     methods for custom post-processing.</li>
 * </ul>
 *
 * @see JobSpec
 * @see LogCaptureServiceImpl
 * @see ExpressionProcessor
 */
public abstract class Job extends QuartzJobBean {

    public static final String METRIC_PREFIX = "jobs";

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired
    private ExpressionProcessor expressionProcessor;

    /**
     * Defines the minimum logging level to capture for this job's execution history.
     * Override this to change the granularity of captured logs.
     *
     * @return the {@link Level} to capture; defaults to {@code INFO}.
     */
    public Level getLoggerLevel() {
        return Level.INFO;
    }

    /**
     * The primary entry point for job business logic.
     * Implementations should place their task logic here.
     *
     * @param jobExecutionContext the Quartz execution context.
     * @throws Exception if an error occurs that should trigger the retry policy or failure logic.
     */
    public abstract void executeJob(JobExecutionContext jobExecutionContext) throws Exception;

    /**
     * Callback method executed after the job and all retry attempts have failed.
     *
     * @param jobExecutionContext the Quartz execution context.
     * @param e                   the final exception that caused the job to fail.
     */
    public void onFailure(final JobExecutionContext jobExecutionContext, final Throwable e) {
    }

    /**
     * Callback method executed after the job completes successfully.
     * Note: This is called within the retry template upon the first successful execution.
     *
     * @param jobExecutionContext the Quartz execution context.
     */
    public void onSuccess(final JobExecutionContext jobExecutionContext) {
    }

    /**
     * Final implementation of the Quartz {@code executeInternal} method.
     * This method wraps {@link #executeJob(JobExecutionContext)} with logging capture,
     * retry templates, metrics increments, and stopwatch timing.
     *
     * @param jobExecutionContext the context for the current execution.
     * @throws JobExecutionException if the job fails after exhausting all retry attempts.
     */
    @Override
    protected final void executeInternal(final JobExecutionContext jobExecutionContext) throws JobExecutionException {
        final var groupName = jobExecutionContext.getJobDetail().getKey().getGroup();
        final var jobName = jobExecutionContext.getJobDetail().getKey().getName();
        final var metricsNameSuccess = SUCCESS.getMetricName(groupName, jobName);
        Optional<Timer> timeContext = Optional.empty();
        if (meterRegistry != null) {
            final String metricsNameDuration = DURATION.getMetricName(groupName, jobName);
            timeContext = Optional.of(meterRegistry.timer(metricsNameDuration));
        }
        final var stopwatch = StopWatch.createStarted();
        var maxAttempts = 1; // only one pass by default
        var backOffPeriod = 0L; // back off immediately by default

        if (this instanceof final OnErrorRepeatable onErrorRepeatable) {
            if (onErrorRepeatable.getOnErrorRepeatCount() > 1) {
                maxAttempts = onErrorRepeatable.getOnErrorRepeatCount();
            }
            if (onErrorRepeatable.getOnErrorRepeatDelay() > 0) {
                backOffPeriod = onErrorRepeatable.getOnErrorRepeatDelay();
            }
        }

        final var jobSpec = this.getClass().getAnnotation(JobSpec.class);
        if (isNotBlank(jobSpec.onError().onErrorRepeatCountExp())) {
            maxAttempts = expressionProcessor.processExpression(jobSpec.onError().onErrorRepeatCountExp(), Integer.class);
        } else if (jobSpec.onError().onErrorRepeatCount() > 1) {
            maxAttempts = jobSpec.onError().onErrorRepeatCount();
        }
        if (isNotBlank(jobSpec.onError().onErrorRepeatDelayExp())) {
            backOffPeriod = expressionProcessor.processExpression(jobSpec.onError().onErrorRepeatDelayExp(), Long.class);
        } else if (jobSpec.onError().onErrorRepeatDelay() > 0) {
            backOffPeriod = jobSpec.onError().onErrorRepeatDelay();
        }

        var retryPolicy = RetryPolicy.builder()
                .backOff(new FixedBackOff(backOffPeriod, maxAttempts))
                .build();
        var retryTemplate = new RetryTemplate(retryPolicy);
        final var logCaptureService = new LogCaptureServiceImpl(getLoggerLevel(), jobExecutionContext.getJobDetail().getJobClass());
        final var state = new RetryState();
        try {
            retryTemplate.execute(() -> {
                if (state.getAttemptCount() > 0) {
                    jobExecutionContext.getMergedJobDataMap().put("RetryCount", state.getAttemptCount());
                }
                if (state.getLastException() != null) {
                    jobExecutionContext.getMergedJobDataMap().put("LastThrowableClass", state.getLastException().getClass());
                    jobExecutionContext.getMergedJobDataMap().put("LastThrowableMessage", state.getLastException().getMessage());
                }
                try {
                    executeJob(jobExecutionContext);
                } catch (Throwable ex) {
                    state.recordAttempt(ex);
                    throw ex;
                }
                onSuccess(jobExecutionContext);
                if (meterRegistry != null) {
                    meterRegistry.counter(metricsNameSuccess).increment();
                }
                return null;
            });
        } catch (final Throwable throwable) {
            onFailure(jobExecutionContext, throwable);
            if (meterRegistry != null) {
                final var metricsNameFailure = FAILURE.getMetricName(groupName, jobName);
                meterRegistry.counter(metricsNameFailure).increment();
            }
            throw new JobExecutionException("Error executing job \"" +
                    jobExecutionContext.getJobDetail().getKey().toString() + "\"", throwable);
        } finally {
            stopwatch.stop();
            timeContext.ifPresent(timer -> {
                timer.record(stopwatch.getDuration());
                timer.close();
            });
            final var list = logCaptureService.getList();
            if (!list.isEmpty()) {
                final var output = list.stream()
                        .filter(Objects::nonNull)
                        .map(e -> e.getInstant() + "\t" + e.getLevel() + ": " + e.getFormattedMessage())
                        .collect(Collectors.joining("\n"));
                logCaptureService.close();

                // we use JobDetail.JobDataMap to transfer the captured
                // log output to QuartzExecutorService.insertJobExecutionLog(...) method
                jobExecutionContext.getJobDetail().getJobDataMap().put(JOB_DATA_MAP_OUTPUT_PARAM, output);
            }
        }
    }

    /**
     * Internal state holder to track retry attempts and the last exception encountered
     * during a multi-attempt execution.
     */
    static class RetryState {
        private int attemptCount = 0;
        private Throwable lastException = null;

        /**
         * Increments the attempt counter and stores the provided exception.
         *
         * @param ex the exception encountered during the current attempt.
         */
        void recordAttempt(final Throwable ex) {
            this.attemptCount++;
            this.lastException = ex;
        }

        /**
         * Returns the number of failed attempts recorded so far.
         */
        int getAttemptCount() {
            return attemptCount;
        }

        /**
         * Returns the most recent {@link Throwable} caught during execution attempts.
         */
        Throwable getLastException() {
            return lastException;
        }
    }
}
