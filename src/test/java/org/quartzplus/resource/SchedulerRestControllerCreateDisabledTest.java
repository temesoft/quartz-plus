package org.quartzplus.resource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartzplus.configuration.QuartzPlusAutoConfiguration;
import org.quartzplus.configuration.QuartzPlusFlywayConfiguration;
import org.quartzplus.internal.ExecutionLogCleanupJob;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.test.EmbeddedPortRetriever;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class SchedulerRestControllerCreateDisabledTest {

    private static final String SCHEDULER_URI = "/test-scheduler-api"; // set in test application.properties
    private static final RestTemplate restTemplate = new RestTemplate();

    static {
        restTemplate.setErrorHandler(response -> !response.getStatusCode().is2xxSuccessful());
    }

    private static ConfigurableApplicationContext appContext;
    private static int port = -1;

    @Test
    void testCreateJobIsAnswered404WhenDisabled() throws SchedulerException {
        final var response = restTemplate.exchange("http://localhost:" + port + SCHEDULER_URI + "/create" +
                        "?instanceClass={instanceClass}&groupName={groupName}&triggerName={triggerName}&jobName={jobName}",
                HttpMethod.PUT, null, String.class, Map.of(
                        "instanceClass", ExecutionLogCleanupJob.class.getName(),
                        "groupName", "CreateDisabledGroup",
                        "triggerName", "CreateDisabledTrigger",
                        "jobName", "CreateDisabledJob"
                ));
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(appContext.getBean(Scheduler.class).getJobDetail(new JobKey("CreateDisabledJob", "CreateDisabledGroup"))).isNull();
    }

    @BeforeAll
    static void setUpOnce() throws Exception {
        final var app = new SpringApplication(
                TestCoreConfigImport.class,
                QuartzPlusAutoConfiguration.class,
                QuartzAutoConfiguration.class,
                QuartzPlusFlywayConfiguration.class,
                TestConfig.class
        );
        final var properties = new Properties();
        properties.setProperty("quartz-plus.api.create-enabled", "false");
        app.setDefaultProperties(properties);
        final var embeddedPortRetriever = new EmbeddedPortRetriever(app);
        app.setBannerMode(Banner.Mode.OFF);
        appContext = app.run();
        port = embeddedPortRetriever.getRetrievedPort();
    }

    @AfterAll
    static void tearDownOnce() {
        if (appContext != null) {
            appContext.close();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        JobsCollection jobsCollection() {
            return () -> List.of(ExecutionLogCleanupJob.class);
        }
    }
}
