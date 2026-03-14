package org.quartzplus.configuration;

import com.google.common.util.concurrent.Uninterruptibles;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Calendar;
import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.calendar.WeeklyCalendar;
import org.quartzplus.Job;
import org.quartzplus.JobMetricTypeEnum;
import org.quartzplus.SimpleTriggerable;
import org.quartzplus.TimeConstrainable;
import org.quartzplus.annotation.CronTriggerSpec;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.OnErrorSpec;
import org.quartzplus.annotation.SimpleTriggerSpec;
import org.quartzplus.annotation.TriggerSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.exception.JobServiceException;
import org.quartzplus.service.DataSourceJobExecutionLogServiceImpl;
import org.quartzplus.service.JobExecutionLogService;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.service.QuartzExecutorService;
import org.quartzplus.test.H2Configuration;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.util.Assert.isTrue;
import static org.springframework.util.Assert.notNull;

public class QuartzPlusAutoConfigurationTest {

    private static final String GROUP_NAME = "TestGroup";
    private static final String TEST_WEEKDAYS_JOB_TRIGGER = "QuartzClusteredConfigurationTest_TestWeekdayJobTrigger";
    private static final String TEST_WEEKDAYS_JOB_NAME = "QuartzClusteredConfigurationTest_TestWeekdayJob";
    private static final String TEST_JOB_SIMPLE_TRIGGER = "QuartzClusteredConfigurationTest_TestJobSimpleTrigger";
    private static final String TEST_JOB_SIMPLE_NAME = "QuartzClusteredConfigurationTest_TestJobSimple";
    private static final String TEST_JOB_PAUSED_TRIGGER = "QuartzClusteredConfigurationTest_TestJobPausedTrigger";
    private static final String TEST_JOB_PAUSED_NAME = "QuartzClusteredConfigurationTest_TestJobPaused";
    private static final String TEST_JOB_CRON_TRIGGER = "QuartzClusteredConfigurationTest_TestJobCronTrigger";
    private static final String TEST_JOB_CRON_NAME = "QuartzClusteredConfigurationTest_TestJobCron";
    private static final String FAILING_JOB_ON_ERROR_REPEAT_TRIGGER = "QuartzClusteredConfigurationTest_FailingTestJobTriggerOnErrorRepeat";
    private static final String FAILING_JOB_ON_ERROR_REPEAT_NAME = "QuartzClusteredConfigurationTest_FailingTestJobOnErrorRepeat";
    private static final String FAILING_JOB_TRIGGER = "QuartzClusteredConfigurationTest_FailingTestJobTrigger";
    private static final String FAILING_JOB_NAME = "QuartzClusteredConfigurationTest_FailingTestJob";
    private static final String TIME_ZONE = "America/Los_Angeles";
    private static final String CRON_EXPRESSION_ORIGINAL = "0/1 * * * * ?";
    private static final String CRON_EXPRESSION_RESET = "0/2 * * * * ?";

    private static Scheduler scheduler;
    private static QuartzExecutorService quartzExecutorService;
    private static MeterRegistry meterRegistry;
    private static ConfigurableApplicationContext appContext;
    private static SpringApplication app;

    @Test
    public void testMetrics() {
        Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(5));
        assertThat(meterRegistry).isNotNull();
        final var failureMeter = meterRegistry.get(JobMetricTypeEnum.FAILURE.getMetricName(GROUP_NAME, FAILING_JOB_NAME));
        assertThat(failureMeter).isNotNull();
        await().atMost(Duration.ofSeconds(5)).until(() -> failureMeter.counter().count() > 0);
        final var successMeter = meterRegistry.get(JobMetricTypeEnum.SUCCESS.getMetricName(GROUP_NAME, TEST_JOB_CRON_NAME));
        assertThat(successMeter).isNotNull();
        await().atMost(Duration.ofSeconds(5)).until(() -> successMeter.counter().count() > 0);
        final var durationTimer = meterRegistry.get(JobMetricTypeEnum.DURATION.getMetricName(GROUP_NAME, FAILING_JOB_NAME));
        assertThat(durationTimer).isNotNull();
        await().atMost(Duration.ofSeconds(5)).until(() -> durationTimer.timer().count() > 0);
    }

    @Test
    public void testQuartScheduler() throws SchedulerException {
        notNull(scheduler, "Quartz scheduler should not be null");
        isTrue(scheduler.isStarted(), "Quartz scheduler should be started by now");
        isTrue(!scheduler.isInStandbyMode(), "Quartz scheduler should not be in a standby mode");
    }

    @Test
    public void testTriggerTimeZone() throws SchedulerException {
        final var trigger = scheduler.getTrigger(new TriggerKey(TEST_WEEKDAYS_JOB_TRIGGER, GROUP_NAME));
        notNull(trigger, "Trigger for test weekdays job is should not be null");
        isTrue(trigger instanceof CronTrigger, "Trigger returned should be cron trigger");
        final var tz = ((CronTrigger) trigger).getTimeZone();
        notNull(tz, "Job trigger should have time zone selected");
        isTrue(tz.getID().equals(TIME_ZONE), "Time zone should be [" + TIME_ZONE + "] and it is [" + tz.getDisplayName() + "]");
    }

    @Test
    public void testCalendarSelection() throws SchedulerException {
        final var trigger = scheduler.getTrigger(new TriggerKey(TEST_WEEKDAYS_JOB_TRIGGER, GROUP_NAME));
        notNull(trigger, "Trigger for test weekdays job is should not be null");
        isTrue(WeeklyCalendar.class.getName().equals(trigger.getCalendarName()), "The job should have NoWeekendsCalendar selected as trigger calendar");
    }

    @Test
    public void testStartedPausedTrigger() throws SchedulerException {
        final var triggerKey = new TriggerKey(TEST_JOB_PAUSED_TRIGGER, GROUP_NAME);
        Trigger.TriggerState state = scheduler.getTriggerState(triggerKey);
        isTrue(state == Trigger.TriggerState.PAUSED, "The trigger should have started in paused state");
        scheduler.resumeTrigger(triggerKey);
        state = scheduler.getTriggerState(triggerKey);
        isTrue(state == Trigger.TriggerState.NORMAL, "The trigger should change its state to normal");
    }

    @Test
    public void testCalendarWeekdaysOnly() throws SchedulerException {
        final var calName = WeeklyCalendar.class.getName();
        final Calendar calendar = scheduler.getCalendar(calName);
        notNull(calendar, "Calendar " + calName + " should not be null");
        final var dtFormatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 11)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 30)
                .toFormatter()
                .withZone(ZoneId.systemDefault());
        final var farFutureMon = dtFormatter.parse("2500-01-25", Instant::from);
        final var farFutureTue = dtFormatter.parse("2500-01-26", Instant::from);
        final var farFutureWed = dtFormatter.parse("2500-01-27", Instant::from);
        final var farFutureThu = dtFormatter.parse("2500-01-28", Instant::from);
        final var farFutureFri = dtFormatter.parse("2500-01-29", Instant::from);
        final var farFutureSat = dtFormatter.parse("2500-01-30", Instant::from);
        final var farFutureSun = dtFormatter.parse("2500-01-31", Instant::from);
        isTrue(calendar.isTimeIncluded(farFutureMon.toEpochMilli()), "Future date " + farFutureMon + " is a 'Monday' and should included");
        isTrue(calendar.isTimeIncluded(farFutureTue.toEpochMilli()), "Future date " + farFutureTue + " is a 'Tuesday' and should included");
        isTrue(calendar.isTimeIncluded(farFutureWed.toEpochMilli()), "Future date " + farFutureWed + " is a 'Wednesday' and should included");
        isTrue(calendar.isTimeIncluded(farFutureThu.toEpochMilli()), "Future date " + farFutureThu + " is a 'Thursday' and should included");
        isTrue(calendar.isTimeIncluded(farFutureFri.toEpochMilli()), "Future date " + farFutureFri + " is a 'Friday' and should included");
        isTrue(!calendar.isTimeIncluded(farFutureSat.toEpochMilli()), "Future date " + farFutureSat + " is a 'Saturday' and should excluded");
        isTrue(!calendar.isTimeIncluded(farFutureSun.toEpochMilli()), "Future date " + farFutureSun + " is a 'Sunday' and should excluded");
    }

    @Test
    public void testJobExecutions() {
        notNull(quartzExecutorService, "QuartzExecutorService bean should not be null");
        isTrue(TestJob.successCountDownLatch.getCount() == 0, "Quartz scheduler should have already executed the TestJob");
    }

    /**
     * This test case tries to re-register job classes (cron and simple trigger) during runtime after the scheduler has been initialized with {@link JobsCollection}
     */
    @Test
    public void testReRegisterJobs() throws JobServiceException, SchedulerException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        quartzExecutorService.registerJobClass(scheduler, TestJob.class, TestJob.class.getAnnotation(JobSpec.class));
        quartzExecutorService.registerJobClass(scheduler, TestJobCron.class, TestJobCron.class.getAnnotation(JobSpec.class));
        final var trigger = (SimpleTrigger) scheduler.getTrigger(new TriggerKey(TEST_JOB_SIMPLE_TRIGGER, GROUP_NAME));
        isTrue(trigger.getRepeatInterval() == 1, "The trigger should not have been reset; repeat interval should be = 1");
        final var triggerCron = (CronTrigger) scheduler.getTrigger(new TriggerKey(TEST_JOB_CRON_TRIGGER, GROUP_NAME));
        isTrue(triggerCron.getCronExpression().equals(CRON_EXPRESSION_ORIGINAL), "The trigger should have not been reset; CronExpression should be = " + CRON_EXPRESSION_ORIGINAL);
    }

    /**
     * This test case tries to re-register / reset changed job classes (cron and simple trigger) during runtime after the scheduler has been initialized with {@link JobsCollection}
     */
    @Test
    public void testReRegisterJobsSimpleReset() throws JobServiceException, SchedulerException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        var trigger = (SimpleTrigger) scheduler.getTrigger(new TriggerKey(TEST_JOB_SIMPLE_TRIGGER, GROUP_NAME));
        assertThat(trigger.getRepeatInterval()).isEqualTo(1);
        quartzExecutorService.registerJobClass(scheduler, TestJobSimpleTriggerReset.class, TestJobSimpleTriggerReset.class.getAnnotation(JobSpec.class));
        trigger = (SimpleTrigger) scheduler.getTrigger(new TriggerKey(TEST_JOB_SIMPLE_TRIGGER, GROUP_NAME));
        assertThat(trigger.getRepeatInterval()).isEqualTo(2);
    }

    /**
     * This test case tries to re-register / reset changed job classes (cron and simple trigger) during runtime after the scheduler has been initialized with {@link JobsCollection}
     */
    @Test
    public void testReRegisterJobsCronReset() throws JobServiceException, SchedulerException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        var triggerCron = (CronTrigger) scheduler.getTrigger(new TriggerKey(TEST_JOB_CRON_TRIGGER, GROUP_NAME));
        isTrue(triggerCron.getCronExpression().equals(CRON_EXPRESSION_ORIGINAL), "The trigger should have not yet been reset; CronExpression should be = " + CRON_EXPRESSION_ORIGINAL);
        quartzExecutorService.registerJobClass(scheduler, TestJobCronTriggerReset.class, TestJobCronTriggerReset.class.getAnnotation(JobSpec.class));
        triggerCron = (CronTrigger) scheduler.getTrigger(new TriggerKey(TEST_JOB_CRON_TRIGGER, GROUP_NAME));
        isTrue(triggerCron.getCronExpression().equals(CRON_EXPRESSION_RESET), "The trigger should have been reset; CronExpression should be = " + CRON_EXPRESSION_RESET);
    }

    @Test
    public void testJobFailureOnErrorRepeat() {
        assertThat(FailingTestJobWithOnErrorRepeat.failureOnErrorRepeatRepeatCountDownLatch.getCount()).isEqualTo(0);
        assertThat(FailingTestJobWithOnErrorRepeat.failureOnErrorRepeatCountDownLatch.getCount()).isEqualTo(0);
    }

    @Test
    public void testJobFailure() {
        assertThat(FailingTestJob.failureCountDownLatch.getCount()).isEqualTo(0);
    }

    @BeforeAll
    public static void setUpOnce() throws Exception {
        app = new SpringApplication(TestConfig.class);
        final var props = new Properties();
        props.putAll(H2Configuration.createQuartzConfigProperties());
        props.setProperty("testing.on.error.repeat.count", "2");
        props.setProperty("testing.on.error.repeat.delay", "1");
        props.setProperty("testing.cron.trigger", "0/30 * * * * ?");
        app.setDefaultProperties(props);
        app.setBannerMode(Banner.Mode.OFF);
        appContext = app.run();
        scheduler = appContext.getBean(Scheduler.class);
        quartzExecutorService = appContext.getBean(QuartzExecutorService.class);
        meterRegistry = appContext.getBean(MeterRegistry.class);

        assertThat(appContext.getBeansOfType(JobExecutionLogService.class)).isNotEmpty().hasSize(1);
        assertThat(appContext.getBean(JobExecutionLogService.class)).isInstanceOf(DataSourceJobExecutionLogServiceImpl.class);

        // let make sure the jobs finish, and trigger what they need to, before proceeding to tests
        TestJob.successCountDownLatch.await(2, TimeUnit.SECONDS);
        FailingTestJobWithOnErrorRepeat.failureOnErrorRepeatCountDownLatch.await(2, TimeUnit.SECONDS);
        FailingTestJobWithOnErrorRepeat.failureOnErrorRepeatRepeatCountDownLatch.await(2, TimeUnit.SECONDS);
        FailingTestJob.failureCountDownLatch.await(2, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void shutdown() {
        if (appContext != null) {
            appContext.close();
        }
    }

    @JobSpec(
            jobName = TEST_JOB_SIMPLE_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_SIMPLE_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = SimpleTriggerable.REPEAT_INDEFINITELY,
                            repeatInterval = 1
                    )
            )
    )
    public static class TestJob extends Job {

        private static final CountDownLatch successCountDownLatch = new CountDownLatch(1);

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }

        @Override
        public void onSuccess(final JobExecutionContext jobExecutionContext) {
            TestJob.successCountDownLatch.countDown();
        }
    }

    @JobSpec(
            jobName = TEST_JOB_PAUSED_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_PAUSED_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED, startType = TriggerState.StartType.PAUSED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = SimpleTriggerable.REPEAT_INDEFINITELY,
                            repeatInterval = 1
                    )
            )
    )
    public static class TestJobPaused extends Job implements TimeConstrainable {

        @Override
        public Instant getStartTime() {
            return Instant.now().minus(1, ChronoUnit.SECONDS);
        }

        @Override
        public Instant getEndTime() {
            return null;
        }

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }
    }

    /**
     * This job class is similar to {@link TestJob} except repeatInterval is changed, to initiate trigger reset
     */
    @JobSpec(
            jobName = TEST_JOB_SIMPLE_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_SIMPLE_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = SimpleTriggerable.REPEAT_INDEFINITELY,
                            repeatInterval = 2
                    )
            )
    )
    public static class TestJobSimpleTriggerReset extends Job {

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }

        @Override
        public void onSuccess(final JobExecutionContext jobExecutionContext) {
        }
    }

    /**
     * This job class is similar to {@link TestJob} except repeatInterval is changed, to initiate trigger reset
     */
    @JobSpec(
            jobName = TEST_JOB_CRON_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_CRON_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    cronTrigger = @CronTriggerSpec(
                            cronExpression = CRON_EXPRESSION_ORIGINAL,
                            timeZone = "UTC"
                    )
            )
    )
    public static class TestJobCron extends Job {

        private static final CountDownLatch successCountDownLatch = new CountDownLatch(1);

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }

        @Override
        public void onSuccess(final JobExecutionContext jobExecutionContext) {
            successCountDownLatch.countDown();
        }
    }

    /**
     * This job class is similar to {@link TestJobCron} except repeatInterval is changed, to initiate trigger reset
     */
    @JobSpec(
            jobName = TEST_JOB_CRON_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_CRON_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    cronTrigger = @CronTriggerSpec(
                            cronExpression = CRON_EXPRESSION_RESET,
                            timeZone = "UTC"
                    )
            )
    )
    public static class TestJobCronTriggerReset extends Job {

        private static final CountDownLatch successCountDownLatch = new CountDownLatch(1);

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }

        @Override
        public void onSuccess(final JobExecutionContext jobExecutionContext) {
            successCountDownLatch.countDown();
        }
    }

    @JobSpec(
            jobName = FAILING_JOB_ON_ERROR_REPEAT_NAME,
            groupName = GROUP_NAME,
            triggerName = FAILING_JOB_ON_ERROR_REPEAT_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = 0,
                            repeatInterval = 1
                    )
            ),
            onError = @OnErrorSpec(
                    onErrorRepeatCountExp = "${testing.on.error.repeat.count}",
                    onErrorRepeatDelayExp = "${testing.on.error.repeat.delay}"
            )
    )
    public static class FailingTestJobWithOnErrorRepeat extends Job {

        private static final CountDownLatch failureOnErrorRepeatCountDownLatch = new CountDownLatch(1);
        private static final CountDownLatch failureOnErrorRepeatRepeatCountDownLatch = new CountDownLatch(2);

        public static final String EXCEPTION_MESSAGE = "This is an expected exception";

        @Override
        public void onSuccess(final JobExecutionContext jobExecutionContext) {
            super.onSuccess(jobExecutionContext);
        }

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
            failureOnErrorRepeatRepeatCountDownLatch.countDown();
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(500));
            throw new RuntimeException(EXCEPTION_MESSAGE);
        }

        @Override
        public void onFailure(final JobExecutionContext jobExecutionContext, Throwable e) {
            notNull(e, "Exception should not be null on failure");
            isTrue(e.getCause().getMessage().equals(EXCEPTION_MESSAGE), "We should have received correct error message");
            failureOnErrorRepeatCountDownLatch.countDown();
        }
    }

    @JobSpec(
            jobName = FAILING_JOB_NAME,
            groupName = GROUP_NAME,
            triggerName = FAILING_JOB_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = -1,
                            repeatInterval = 1
                    )
            )
    )
    public static class FailingTestJob extends Job {

        private static final CountDownLatch failureCountDownLatch = new CountDownLatch(1);

        public static final String EXCEPTION_MESSAGE = "This is an expected exception";

        @Value("${testing.on.error.repeat.count}")
        Integer onErrorRepeatCount = null;

        @Value("${testing.on.error.repeat.delay}")
        Integer onErrorRepeatDelay = null;

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
            failureCountDownLatch.countDown();
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(1));
            throw new RuntimeException(EXCEPTION_MESSAGE);
        }

        @Override
        public void onFailure(final JobExecutionContext jobExecutionContext, Throwable e) {
            notNull(e, "Exception should not be null on failure");
            isTrue(e.getCause().getMessage().equals(EXCEPTION_MESSAGE), "We should have received correct error message");
            failureCountDownLatch.countDown();
        }
    }

    @JobSpec(
            jobName = TEST_WEEKDAYS_JOB_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_WEEKDAYS_JOB_TRIGGER,
            calendarClass = "org.quartz.impl.calendar.WeeklyCalendar",
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    cronTrigger = @CronTriggerSpec(
                            cronExpressionExp = "${testing.cron.trigger}",
                            timeZone = "America/Los_Angeles"
                    )
            )
    )
    public static class WeekdayTestJob extends Job implements TimeConstrainable {

        @Override
        public Instant getStartTime() {
            return Instant.now();
        }

        @Override
        public Instant getEndTime() {
            return Instant.now().plus(10, ChronoUnit.DAYS);
        }

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }
    }

    @Configuration
    @Import({
            TestCoreConfigImport.class,
            QuartzAutoConfiguration.class,
            QuartzPlusAutoConfiguration.class,
            QuartzPlusFlywayConfiguration.class
    })
    static class TestConfig {

        @Bean
        JobsCollection jobsCollection() {
            return () -> List.of(
                    TestJob.class,
                    TestJobPaused.class,
                    TestJobCron.class,
                    FailingTestJobWithOnErrorRepeat.class,
                    FailingTestJob.class,
                    WeekdayTestJob.class
            );
        }
    }
}