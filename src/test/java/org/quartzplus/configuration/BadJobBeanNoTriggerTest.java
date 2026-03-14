package org.quartzplus.configuration;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartzplus.CronTriggerable;
import org.quartzplus.Job;
import org.quartzplus.SimpleTriggerable;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.TriggerSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.exception.JobServiceException;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.test.H2Configuration;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BadJobBeanNoTriggerTest {

    private static final String GROUP_NAME = "TestGroup";
    private static final String TEST_JOB_TRIGGER = "BadJobBeanNoTriggerTest_TestJobTrigger";
    private static final String TEST_JOB_NAME = "BadJobBeanNoTriggerTest_TestJob";

    private SpringApplication app;

    @Bean
    public JobsCollection jobsCollection() {
        return () -> List.of(TestJob.class);
    }

    /**
     * This test should trow a RuntimeException because job bean is not implementing
     * {@link org.quartzplus.SimpleTriggerable} or {@link org.quartzplus.CronTriggerable}
     */
    @Test
    public void testBadJobBeanNoTrigger() {
        assertThatThrownBy(() -> {
            setup();
            app.run();
        })
                .hasRootCauseInstanceOf(JobServiceException.class)
                .hasRootCauseMessage("Job class %s$TestJob should implement one of triggerable interfaces: %s or %s",
                        BadJobBeanNoTriggerTest.class.getName(),
                        CronTriggerable.class.getName(),
                        SimpleTriggerable.class.getName());
    }

    public void setup() {
        app = new SpringApplication(
                TestCoreConfigImport.class,
                QuartzPlusAutoConfiguration.class,
                QuartzAutoConfiguration.class,
                BadJobBeanNoTriggerTest.class
        );
        app.setDefaultProperties(H2Configuration.createQuartzConfigProperties());
        app.setBannerMode(Banner.Mode.OFF);
    }

    /**
     * Following job bean is missing trigger spec definition
     */
    @JobSpec(
            jobName = TEST_JOB_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec
    )
    public static class TestJob extends Job {
        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }
    }
}
