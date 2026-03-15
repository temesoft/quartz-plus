package org.quartzplus.configuration;

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartzplus.Application;
import org.quartzplus.Job;
import org.quartzplus.SimpleTriggerable;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.service.InMemoryJobExecutionLogServiceImpl;
import org.quartzplus.service.JobExecutionLogService;
import org.quartzplus.service.JobsCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.apache.commons.lang3.RandomStringUtils.secure;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class,
        properties = "quartz-plus.job-execution-log.type=InMemory"
)
@AutoConfigureDataJpa
@TestConfiguration
@Import(InMemoryJobExecutionLogServiceImplTest.TestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InMemoryJobExecutionLogServiceImplTest {

    private static final String GROUP_NAME = "TestGroup";
    private static final String TEST_JOB_SIMPLE_NAME = "TestJobSimple";
    private static final String TEST_JOB_SIMPLE_TRIGGER = "TestJobSimpleTrigger";

    @Autowired
    private ConfigurableApplicationContext appContext;

    @Test
    void testStoreFunctionality() throws InterruptedException, SchedulerException {
        assertThat(appContext.getBeansOfType(Scheduler.class)).isNotEmpty();
        final var scheduler = appContext.getBean(Scheduler.class);
        assertThat(scheduler.isStarted()).isTrue();
        assertThat(scheduler.isShutdown()).isFalse();

        final var groupNames = scheduler.getJobGroupNames();
        assertThat(groupNames).isNotEmpty();
        assertThat(groupNames).contains(GROUP_NAME);

        final var trigger = scheduler.getTrigger(TriggerKey.triggerKey(TEST_JOB_SIMPLE_TRIGGER, GROUP_NAME));
        assertThat(trigger).isNotNull();

        TestJob.countDownLatch = new CountDownLatch(2);
        assertThat(TestJob.countDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
        final var jobExecutionLogService = appContext.getBean(JobExecutionLogService.class);
        assertThat(jobExecutionLogService).isInstanceOf(InMemoryJobExecutionLogServiceImpl.class);

        Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(5));

        final var jobExecutionLogs = jobExecutionLogService
                .getJobExecutionLogList(100,
                        0,
                        GROUP_NAME,
                        null,
                        TEST_JOB_SIMPLE_TRIGGER,
                        null);
        assertThat(jobExecutionLogs).isNotEmpty();
        jobExecutionLogs.forEach(log -> {
            assertThat(log.getSuccess()).isFalse();
            assertThat(log.getErrorMessage())
                    .contains("Error executing job")
                    .contains("aborting execution");
        });
        assertThat(jobExecutionLogService.clearJobExecutionLog(Instant.now()))
                .isGreaterThanOrEqualTo((int) jobExecutionLogs.getTotalElements());
    }

    @Test
    void testMultiThread() {
        final var jobExecutionLogService = appContext.getBean(JobExecutionLogService.class);
        final var numberOfThreads = 1_000;
        final var listOfStartedThreads = IntStream.range(0, numberOfThreads)
                .parallel()
                .mapToObj(id -> new Worker(jobExecutionLogService))
                .toList().stream().map((Function<Worker, Thread>) worker -> {
                    worker.start();
                    return worker;
                }).toList();

        IntStream.range(0, numberOfThreads).parallel().forEach(value -> {
            try {
                listOfStartedThreads.get(value).join();
            } catch (InterruptedException e) {
                fail("Error while waiting for thread to finish: " + e.getMessage());
            }
        });

        final var updatedJobExecutionLogs = jobExecutionLogService
                .getJobExecutionLogList(10, 0, null, null, null, null);
        assertThat(updatedJobExecutionLogs.getTotalElements()).isGreaterThanOrEqualTo(numberOfThreads);
    }

    private static class Worker extends Thread {

        private final JobExecutionLogService jobExecutionLogService;

        public Worker(final JobExecutionLogService jobExecutionLogService) {
            this.jobExecutionLogService = jobExecutionLogService;
        }

        @Override
        public void run() {
            jobExecutionLogService.insertJobExecutionLog(
                    new JobExecutionLog(
                            secure().nextAlphanumeric(20),
                            secure().nextAlphanumeric(20),
                            secure().nextAlphanumeric(20),
                            "{}",
                            3,
                            true,
                            "errorMessage",
                            "executionInstanceId",
                            secure().nextAlphanumeric(20),
                            123456L,
                            secure().nextAlphanumeric(20),
                            secure().nextAlphanumeric(20),
                            Instant.now())
            );
        }
    }

    @AfterEach
    void clearEntries() {
        final var jobExecutionLogService = appContext.getBean(JobExecutionLogService.class);
        jobExecutionLogService.clearJobExecutionLog(Instant.now());
    }

    @JobSpec(jobName = TEST_JOB_SIMPLE_NAME, groupName = GROUP_NAME, triggerName = TEST_JOB_SIMPLE_TRIGGER)
    public static class TestJob extends Job implements SimpleTriggerable {

        private static final AtomicLong COUNTER = new AtomicLong(0);

        private static CountDownLatch countDownLatch = new CountDownLatch(1);

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
            TestJob.countDownLatch.countDown();
            throw new RuntimeException("Test exception message #" + COUNTER.incrementAndGet());
        }

        @Override
        public int getRepeatCount() {
            return -1;
        }

        @Override
        public long getRepeatInterval() {
            return 100;
        }
    }

    @Configuration
    @Import(value = {
            QuartzPlusAutoConfiguration.class
    })
    static class TestConfig {

        @Bean
        JobsCollection jobsCollection() {
            return () -> List.of(TestJob.class);
        }
    }
}
