package org.quartzplus.internal;

import ch.qos.logback.classic.Level;
import com.google.common.base.Stopwatch;
import org.quartz.JobExecutionContext;
import org.quartzplus.Job;
import org.quartzplus.annotation.CronTriggerSpec;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.TriggerSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.configuration.Constants;
import org.quartzplus.service.JobExecutionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Calendar;
import java.util.SimpleTimeZone;

/**
 * Internal system job responsible for purging old execution log entries from the storage provider.
 * <p>
 * This job prevents the execution log (whether in-memory or database-backed) from growing indefinitely.
 * By default, it runs daily at 2:00 AM UTC and removes records older than 30 days.
 * <p>
 * <b>Configuration:</b>
 * <ul>
 *     <li>Enable/Disable: {@code job-execution-log-cleanup-job.enabled} (Default: true)</li>
 *     <li>Schedule: {@code job-execution-log-cleanup-job.cron-expression} (Default: 0 0 2 * * ?)</li>
 *     <li>Retention Period: {@code org.quartzplus.internal.ExecutionLogCleanupJob.daysAgo} (Default: 30)</li>
 * </ul>
 * <p>
 * <b>Dynamic Parameters:</b>
 * This job accepts a JSON parameter {@code "daysAgo"} in the JobDataMap to override the
 * configured retention period for a specific run.
 *
 * @see JobExecutionLogService
 * @see JobSpec
 */
@JobSpec(jobName = "ExecutionLogCleanupJob",
        groupName = Constants.GROUP_NAME_INTERNAL,
        triggerName = "ExecutionLogCleanupJob-Trigger",
        jobDescription = "Job cleans old execution log entries, by default older than 30 days ago. " +
                "Optionally takes json parameter \"daysAgo\" to overwrite the original setting. Example {\"daysAgo\":7}",
        triggerState = @TriggerState(enabledExp = "${job-execution-log-cleanup-job.enabled:true}"),
        trigger = @TriggerSpec(
                cronTrigger = @CronTriggerSpec(
                        cronExpressionExp = "${job-execution-log-cleanup-job.cron-expression:0 0 2 * * ?}",
                        timeZoneExp = "${job-execution-log-cleanup-job.time-zone:UTC}"
                )
        )
)
public class ExecutionLogCleanupJob extends Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionLogCleanupJob.class);
    private static final String DAYS_AGO_KEY = "daysAgo";

    @Autowired
    private JobExecutionLogService jobExecutionLogService;

    @Value("${org.quartzplus.internal.ExecutionLogCleanupJob.daysAgo:30}")
    private int daysAgo;

    @Override
    public Level getLoggerLevel() {
        return Level.DEBUG;
    }

    /**
     * Executes the cleanup logic.
     * <p>
     * Calculates the cutoff timestamp based on the {@code daysAgo} parameter,
     * invokes the log service to delete records, and updates the execution context
     * with the count of cleared records.
     *
     * @param jobExecutionContext the context providing access to the merged JobDataMap and scheduler.
     * @throws RuntimeException if an error occurs while communicating with the log service.
     */
    @Override
    public void executeJob(final JobExecutionContext jobExecutionContext) {
        LOGGER.debug("Starting job execution cleanup job");
        final var stopwatch = Stopwatch.createStarted();
        final var cal = Calendar.getInstance(SimpleTimeZone.getDefault());
        if (jobExecutionContext.getMergedJobDataMap().containsKey(DAYS_AGO_KEY)) {
            daysAgo = jobExecutionContext.getMergedJobDataMap().getInt(DAYS_AGO_KEY);
        }
        LOGGER.debug("Will try to clear records older than {} days ago", daysAgo);
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        try {
            final int cleared = jobExecutionLogService.clearJobExecutionLog(cal.toInstant());
            jobExecutionContext.getMergedJobDataMap().put("clearedRecords", cleared);
            jobExecutionContext.getMergedJobDataMap().put("since", cal.toInstant());
            if (!jobExecutionContext.getMergedJobDataMap().containsKey(DAYS_AGO_KEY)) {
                jobExecutionContext.getMergedJobDataMap().put(DAYS_AGO_KEY, daysAgo);
            }
            LOGGER.info("Finished in {}, cleared {} records", stopwatch.stop(), cleared);
        } catch (final Exception e) {
            throw new RuntimeException("Problem cleaning...");
        }
    }
}
