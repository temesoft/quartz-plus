package org.quartzplus.resource;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartzplus.Job;
import org.quartzplus.SimpleTriggerable;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.SimpleTriggerSpec;
import org.quartzplus.annotation.TriggerSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.configuration.QuartzPlusAutoConfiguration;
import org.quartzplus.configuration.QuartzPlusFlywayConfiguration;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.test.EmbeddedPortRetriever;
import org.quartzplus.test.H2Configuration;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@SuppressWarnings("unchecked")
public class SchedulerRestControllerTest {

    private static final String GROUP_NAME = "TestGroup";
    private static final String TEST_JOB_TRIGGER = "SchedulerRestControllerTest_TestJobTrigger";
    private static final String TEST_JOB_NAME = "SchedulerRestControllerTest_TestJob";
    private static final String TEST_JOB_NAME_REMOVABLE = "SchedulerRestControllerTest_RemovableTestJob";
    private static final String TEST_JOB_TRIGGER_REMOVABLE = "SchedulerRestControllerTest_RemovableTestJobTrigger";
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);
    private static final String URL_PREFIX = "http://localhost:";
    private static final String SCHEDULER_URI = "/test-scheduler-api";
    private static final RestTemplate restTemplate = new RestTemplate();

    static {
        restTemplate.setErrorHandler(response -> !response.getStatusCode().is2xxSuccessful());
    }

    private static ConfigurableApplicationContext appContext;
    private static Scheduler scheduler = null;
    private static int port = -1;

    @Test
    public void testSchedulerMetadata() {
        final var resultMap = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/", Map.class);
        assertThat(resultMap).isNotNull();
        assertThat(((Map<String, Object>) resultMap.get("schedulerMetaData")).get("started")).isEqualTo(Boolean.TRUE);
        assertThat(((Map<String, Object>) resultMap.get("schedulerMetaData")).get("runningSince")).isNotNull();
        assertThat((List<Class<? extends Job>>) resultMap.get("jobsCollection")).isNotEmpty();
    }

    @Test
    public void testGroupsList() {
        assertThat((List<String>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/groups", List.class))
                .contains(GROUP_NAME);
    }

    @Test
    public void testExecutionLog() {
        var resultPageMap = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log", Map.class);
        assertThat(resultPageMap).isNotNull();
        var resultList = (List<Object>) resultPageMap.get("content");
        assertThat(resultList).isNotEmpty();
        var entryMap = (Map<String, String>) resultList.get(0);
        assertThat(entryMap).isNotEmpty().containsKey("id");
        var id = entryMap.get("id");
        entryMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log?id=" + id, Map.class);
        assertThat(entryMap).isNotEmpty().containsKeys("id", "groupName");

        var resultMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log?groupName=" + entryMap.get("groupName"), Map.class);
        assertThat(resultMap).isNotEmpty();

        resultMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log" +
                "?groupName=" + entryMap.get("groupName") +
                "&jobName=" + entryMap.get("jobName"), Map.class);
        assertThat(resultMap).isNotEmpty();

        resultMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log" +
                "?groupName=" + entryMap.get("groupName") +
                "&jobName=" + entryMap.get("jobName") +
                "&triggerName=" + entryMap.get("triggerName"), Map.class);
        assertThat(resultMap).isNotEmpty();

        resultMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log" +
                "?groupName=" + entryMap.get("groupName") +
                "&jobName=" + entryMap.get("jobName") +
                "&triggerName=" + entryMap.get("triggerName") +
                "&instanceClass=" + entryMap.get("instanceClass"), Map.class);
        assertThat(resultMap).isNotEmpty();
    }

    @Test
    public void testExecutionLogBadEntity() {
        assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log?id=BAD-ID-NOT-FOUND", String.class))
                .isNull();
    }

    @Test
    public void testQuartzNodes() throws SchedulerException {
        if (scheduler.getMetaData().isJobStoreClustered()) {
            assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/nodes", List.class)).isNotNull().isNotEmpty();
        } else {
            assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/nodes", List.class)).isNotNull().isEmpty();
        }
    }

    @Test
    public void testTriggerJob() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final var request = new HttpEntity<>("{\"date\": \"2010-01-01 01:01:01\"}", headers);
        assertThat(restTemplate.postForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger/execute?groupName=" +
                GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER + "&jobName=" + TEST_JOB_NAME, request, Map.class))
                .isNotEmpty().contains(new AbstractMap.SimpleEntry<>("name", TEST_JOB_NAME));
    }

    @Test
    public void testCreateJob() throws SchedulerException {
        final var jobName = RandomStringUtils.secure().nextAlphabetic(10);
        final var groupName = RandomStringUtils.secure().nextAlphabetic(10);
        final var triggerName = RandomStringUtils.secure().nextAlphabetic(10);
        final var someValue = RandomStringUtils.secure().nextAlphabetic(10);
        final var map = Maps.<String, String>newHashMap();
        final var jobClass = TestJob2.class.getName();
        map.put("instanceClass", jobClass);
        map.put("groupName", groupName);
        map.put("triggerName", triggerName);
        map.put("jobName", jobName);
        map.put("jobDescription", "This is a test...");
        map.put("startPaused", "false");
        map.put("repeatInterval", "5000");
        map.put("repeatCount", "5");
        map.put("calendar", "NoRestrictionsCalendar");
        final var jsonData = "{\"someKey\":\"" + someValue + "\"}";
        map.put("jsonData", jsonData);
        restTemplate.put(URL_PREFIX + port + SCHEDULER_URI + "/create" +
                "?instanceClass={instanceClass}" +
                "&groupName={groupName}" +
                "&triggerName={triggerName}" +
                "&jobName={jobName}" +
                "&jobDescription={jobDescription}" +
                "&startPaused={startPaused}" +
                "&calendar={calendar}" +
                "&repeatInterval={repeatInterval}" +
                "&repeatCount={repeatCount}" +
                "&jsonData={jsonData}", null, map);
        final var jobDetail = scheduler.getJobDetail(new JobKey(jobName, groupName));
        assertThat(jobDetail).isNotNull();
        assertThat(jobDetail.getKey().getName()).isEqualTo(jobName);
        assertThat(jobDetail.getKey().getGroup()).isEqualTo(groupName);
        assertThat(jobDetail.getJobClass().getName()).isEqualTo(TestJob2.class.getName());
        final var trigger = scheduler.getTrigger(new TriggerKey(triggerName, groupName));
        assertThat(trigger).isNotNull();
        assertThat(trigger.getJobDataMap()).isNotNull();
        assertThat(trigger.getJobDataMap().isEmpty()).isFalse();
        assertThat(trigger.getJobDataMap().containsKey("someKey")).isTrue();
        assertThat(trigger.getJobDataMap().get("someKey").toString()).isEqualTo(someValue);
        /*
         verify that:
            - trigger is SimpleTriggerImpl
            - trigger has 5000 repeatInterval and not 10000 as set in TestJob2.getRepeatInterval()
            - trigger has 5 repeatCount and not REPEAT_INDEFINITELY (-1) as set in TestJob2.getRepeatCount()
         */
        assertThat(trigger).isInstanceOf(SimpleTriggerImpl.class);
        final var simpleTrigger = (SimpleTriggerImpl) trigger;
        assertThat(simpleTrigger.getRepeatInterval()).isEqualTo(5_000L);
        assertThat(simpleTrigger.getRepeatCount()).isEqualTo(5);
    }

    @Test
    public void testTriggerJobBadJson() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/jobs/" + TEST_JOB_NAME + "/trigger/",
                "{this-is-not-json}", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testTriggerJobBadGroup() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/BAD_GROUP_NAME" + "/jobs/" + "BAD_JOB_NAME/trigger", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testPauseResumeJob() {
        assertThat(restTemplate.postForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger/pause?groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, "", Map.class))
                .isNotNull().isNotEmpty();
        assertThat(restTemplate.postForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger/resume?groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, "", Map.class))
                .isNotNull().isNotEmpty();
    }

    @Test
    public void testPauseTriggerNotFound() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + "trigger-does-not-exist" + "/pause", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testResumeTriggerBadState() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + TEST_JOB_TRIGGER + "/resume", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testResumeTriggerNotFound() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + "trigger-does-not-exist" + "/resume", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testTriggerDetails() {
        final Map<String, Object> resultMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger?groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, Map.class);
        assertThat(resultMap).isNotNull().isNotEmpty();
        assertThat(TEST_JOB_TRIGGER).isEqualTo(((Map<String, Object>) resultMap.get("trigger")).get("name"));
        assertThat(GROUP_NAME).isEqualTo(((Map<String, Object>) resultMap.get("trigger")).get("group"));
        assertThat(resultMap.get("state")).isEqualTo(Trigger.TriggerState.NORMAL.name());
        assertThat((Integer) ((Map<String, Object>) resultMap.get("trigger")).get("timesTriggered")).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void testTriggerDetailsError() {
        assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/" + "group-does-not-exist" + "/triggers/" + TEST_JOB_TRIGGER, String.class))
                .contains("404", "Not Found");
    }

    @Test
    public void testDeleteTrigger() {
        restTemplate.delete(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + TEST_JOB_TRIGGER_REMOVABLE, Map.class);
    }

    @Test
    public void testDeleteTriggerError() {
        final var uri = URL_PREFIX + port + SCHEDULER_URI + "/group-does-not-exist" + "/triggers/" + TEST_JOB_TRIGGER;
        final var response = restTemplate.exchange(
                uri,
                HttpMethod.DELETE,
                new RequestEntity<>(HttpMethod.DELETE, URI.create(uri)),
                Map.class
        );
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testTriggerList() {
        final var resultList = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/triggers?groupName=" + GROUP_NAME, ArrayList.class);
        assertThat(resultList).isNotNull().isNotEmpty();
        final var triggers = List.of(
                ((Map<String, Object>) ((Map<String, Object>) resultList.get(0)).get("trigger")).get("name"),
                ((Map<String, Object>) ((Map<String, Object>) resultList.get(1)).get("trigger")).get("name")
        );
        assertThat(triggers).contains(TEST_JOB_TRIGGER, TEST_JOB_TRIGGER_REMOVABLE);
        assertThat(((Map<String, Object>) ((Map<String, Object>) resultList.get(0)).get("trigger")).get("group")).isEqualTo(GROUP_NAME);
    }

    @Test
    public void testGetMetrics() {
        assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/metrics", Map.class))
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    public void testJobList() {
        final var resultList = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/jobs?groupName=" + GROUP_NAME, ArrayList.class);
        assertThat(resultList).isNotNull().isNotEmpty();
        final var triggers = List.of(
                ((Map<String, Object>) resultList.get(0)).get("name"),
                ((Map<String, Object>) resultList.get(1)).get("name")
        );
        assertThat(triggers).contains(TEST_JOB_NAME, TEST_JOB_NAME_REMOVABLE);
        assertThat(((Map<String, Object>) resultList.get(0)).get("group")).isEqualTo(GROUP_NAME);
    }

    @Test
    public void testJobListError() {
        final var response = restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI + "/BAD_GROUP_NAME" + "/jobs", String.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @BeforeAll
    public static void setUpOnce() throws Exception {
        final var app = new SpringApplication(
                TestCoreConfigImport.class,
                QuartzPlusAutoConfiguration.class,
                QuartzAutoConfiguration.class,
                QuartzPlusFlywayConfiguration.class,
                TestConfig.class
        );
        final var embeddedPortRetriever = new EmbeddedPortRetriever(app);
        app.setDefaultProperties(H2Configuration.createQuartzConfigProperties());
        app.setBannerMode(Banner.Mode.OFF);
        appContext = app.run();
        port = embeddedPortRetriever.getRetrievedPort();
        scheduler = appContext.getBean(Scheduler.class);
        assertThat(COUNT_DOWN_LATCH.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(COUNT_DOWN_LATCH.getCount()).isZero();
    }

    @AfterAll
    public static void tearDownOnce() throws Exception {
        if (scheduler != null) {
            scheduler.unscheduleJob(new TriggerKey(TEST_JOB_TRIGGER, GROUP_NAME));
            scheduler.shutdown(false);
        }
        if (appContext != null) {
            appContext.close();
        }
    }

    @JobSpec(
            jobName = TEST_JOB_NAME,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_TRIGGER,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = SimpleTriggerable.REPEAT_INDEFINITELY,
                            repeatInterval = 10_000
                    )
            )
    )
    public static class TestJob extends Job {

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
            COUNT_DOWN_LATCH.countDown();
        }
    }

    public static class TestJob2 extends Job implements SimpleTriggerable {

        @Override
        public int getRepeatCount() {
            return SimpleTriggerable.REPEAT_INDEFINITELY;
        }

        @Override
        public long getRepeatInterval() {
            return 10_000;
        }

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) throws Exception {
        }
    }

    @JobSpec(
            jobName = TEST_JOB_NAME_REMOVABLE,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_TRIGGER_REMOVABLE,
            triggerState = @TriggerState(enabled = TriggerState.State.ENABLED),
            trigger = @TriggerSpec(
                    simpleTrigger = @SimpleTriggerSpec(
                            repeatCount = SimpleTriggerable.REPEAT_INDEFINITELY,
                            repeatInterval = 10000
                    )
            )
    )
    public static class RemovableTestJob extends Job {

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        JobsCollection schedulerRestControllerTestJobsCollection() {
            return () -> List.of(TestJob.class, RemovableTestJob.class);
        }
    }
}
