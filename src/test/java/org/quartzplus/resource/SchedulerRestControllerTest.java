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
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.service.JobExecutionLogService;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.test.EmbeddedPortRetriever;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@SuppressWarnings("unchecked")
class SchedulerRestControllerTest {

    private static final String GROUP_NAME = "TestGroup";
    private static final String TEST_JOB_TRIGGER = "SchedulerRestControllerTest_TestJobTrigger";
    private static final String TEST_JOB_NAME = "SchedulerRestControllerTest_TestJob";
    private static final String TEST_JOB_NAME_REMOVABLE = "SchedulerRestControllerTest_RemovableTestJob";
    private static final String TEST_JOB_TRIGGER_REMOVABLE = "SchedulerRestControllerTest_RemovableTestJobTrigger";
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(1);
    private static final String URL_PREFIX = "http://localhost:";
    private static final String SCHEDULER_URI = "/test-scheduler-api";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final Set<String> INITIALISED_CLASSES = ConcurrentHashMap.newKeySet();

    static {
        restTemplate.setErrorHandler(response -> !response.getStatusCode().is2xxSuccessful());
    }

    private static ConfigurableApplicationContext appContext;
    private static Scheduler scheduler = null;
    private static int port = -1;

    @Test
    void testSchedulerMetadata() {
        final var resultMap = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/", Map.class);
        assertThat(resultMap).isNotNull();
        assertThat(((Map<String, Object>) resultMap.get("schedulerMetaData")).get("started")).isEqualTo(Boolean.TRUE);
        assertThat(((Map<String, Object>) resultMap.get("schedulerMetaData")).get("runningSince")).isNotNull();
        assertThat((List<Class<? extends Job>>) resultMap.get("jobsCollection")).isNotEmpty();
    }

    @Test
    void testGroupsList() {
        assertThat((List<String>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/groups", List.class))
                .contains(GROUP_NAME);
    }

    @Test
    void testExecutionLog() {
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
    void testExecutionLogBadEntity() {
        assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/log?id=BAD-ID-NOT-FOUND", String.class))
                .isNull();
    }

    @Test
    void testQuartzNodes() throws SchedulerException {
        if (scheduler.getMetaData().isJobStoreClustered()) {
            assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/nodes", List.class)).isNotNull().isNotEmpty();
        } else {
            assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/nodes", List.class)).isNotNull().isEmpty();
        }
    }

    @Test
    void testTriggerJob() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final var request = new HttpEntity<>("{\"date\": \"2010-01-01 01:01:01\"}", headers);
        assertThat(restTemplate.postForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger/execute?groupName=" +
                GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER + "&jobName=" + TEST_JOB_NAME, request, Map.class))
                .isNotEmpty().contains(new AbstractMap.SimpleEntry<>("name", TEST_JOB_NAME));
    }

    @Test
    void testCreateJob() throws SchedulerException {
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
    void testTriggerJobBadJson() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/jobs/" + TEST_JOB_NAME + "/trigger/",
                "{this-is-not-json}", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void testTriggerJobBadGroup() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/BAD_GROUP_NAME" + "/jobs/" + "BAD_JOB_NAME/trigger", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void testPauseResumeJob() {
        assertThat(restTemplate.postForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger/pause?groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, "", Map.class))
                .isNotNull().isNotEmpty();
        assertThat(restTemplate.postForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger/resume?groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, "", Map.class))
                .isNotNull().isNotEmpty();
    }

    @Test
    void testPauseTriggerNotFound() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + "trigger-does-not-exist" + "/pause", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void testResumeTriggerBadState() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + TEST_JOB_TRIGGER + "/resume", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void testResumeTriggerNotFound() {
        final var response = restTemplate.postForEntity(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + "trigger-does-not-exist" + "/resume", "", Map.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void testTriggerDetails() {
        final Map<String, Object> resultMap = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/trigger?groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, Map.class);
        assertThat(resultMap).isNotNull().isNotEmpty();
        assertThat(TEST_JOB_TRIGGER).isEqualTo(((Map<String, Object>) resultMap.get("trigger")).get("name"));
        assertThat(GROUP_NAME).isEqualTo(((Map<String, Object>) resultMap.get("trigger")).get("group"));
        assertThat(resultMap.get("state")).isEqualTo(Trigger.TriggerState.NORMAL.name());
        assertThat((Integer) ((Map<String, Object>) resultMap.get("trigger")).get("timesTriggered")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testTriggerDetailsError() {
        assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/" + "group-does-not-exist" + "/triggers/" + TEST_JOB_TRIGGER, String.class))
                .contains("404", "Not Found");
    }

    @Test
    void testDeleteTrigger() {
        restTemplate.delete(URL_PREFIX + port + SCHEDULER_URI + "/" + GROUP_NAME + "/triggers/" + TEST_JOB_TRIGGER_REMOVABLE, Map.class);
    }

    @Test
    void testDeleteTriggerError() {
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
    void testTriggerList() {
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
    void testGetMetrics() {
        assertThat(restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/metrics", Map.class))
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void testJobList() {
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
    void testJobListByInstanceClass() {
        final var resultList = restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI + "/jobs?instanceClass=" + TestJob.class.getName(), ArrayList.class);
        assertThat(resultList).hasSize(1);
        assertThat(((Map<String, Object>) resultList.get(0)).get("name")).isEqualTo(TEST_JOB_NAME);
    }

    @Test
    void testJobListByInstanceClassRefusesUnknownClassWithoutLoadingIt() {
        for (final var className : List.of(
                NotAJobWithStaticInitialiser.class.getName(),
                UnscheduledJobWithStaticInitialiser.class.getName(),
                "org.example.DoesNotExist"
        )) {
            final var response = restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI + "/jobs?instanceClass=" + className, String.class);
            assertThat(response.getStatusCode()).as(className).isEqualTo(BAD_REQUEST);
        }
        assertThat(INITIALISED_CLASSES).isEmpty();
    }

    @Test
    void testCreateJobRefusesNonJobClassWithoutInitialisingIt() {
        final var response = restTemplate.exchange(URL_PREFIX + port + SCHEDULER_URI + "/create" +
                        "?instanceClass={instanceClass}&groupName={groupName}&triggerName={triggerName}&jobName={jobName}",
                HttpMethod.PUT, null, String.class, Map.of(
                        "instanceClass", NotAJobWithStaticInitialiser.class.getName(),
                        "groupName", RandomStringUtils.secure().nextAlphabetic(10),
                        "triggerName", RandomStringUtils.secure().nextAlphabetic(10),
                        "jobName", RandomStringUtils.secure().nextAlphabetic(10)
                ));
        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).contains("should extend class " + Job.class.getName());
        assertThat(INITIALISED_CLASSES).isEmpty();
    }

    @Test
    void testCreateJobNeverLoadsCalendarParameterAsClass() throws SchedulerException {
        final var groupName = RandomStringUtils.secure().nextAlphabetic(10);
        final var jobName = RandomStringUtils.secure().nextAlphabetic(10);
        final var response = restTemplate.exchange(URL_PREFIX + port + SCHEDULER_URI + "/create" +
                        "?instanceClass={instanceClass}&groupName={groupName}&triggerName={triggerName}&jobName={jobName}" +
                        "&calendar={calendar}&repeatInterval=60000&repeatCount=0",
                HttpMethod.PUT, null, String.class, Map.of(
                        "instanceClass", TestJob2.class.getName(),
                        "groupName", groupName,
                        "triggerName", RandomStringUtils.secure().nextAlphabetic(10),
                        "jobName", jobName,
                        "calendar", NotAJobWithStaticInitialiser.class.getName()
                ));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(scheduler.getJobDetail(new JobKey(jobName, groupName))).isNotNull();
        assertThat(INITIALISED_CLASSES).isEmpty();
    }

    @Test
    void testExecutionLogFilteredByResultAndTimeNewestFirst() {
        final var group = "LogFilterTestGroup";
        final var now = Instant.now();
        insertLog(group, "FilterJob", true, 10, now.minus(Duration.ofMinutes(50)));
        insertLog(group, "FilterJob", false, 20, now.minus(Duration.ofMinutes(40)));
        insertLog(group, "FilterJob", true, 30, now.minus(Duration.ofMinutes(5)));

        final var all = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/log?groupName=" + group, Map.class);
        assertThat(all.get("totalElements")).isEqualTo(3);
        assertThat(((List<Map<String, Object>>) all.get("content")).stream().map(log -> log.get("duration")))
                .containsExactly(30, 20, 10);

        final var failures = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/log?success=false&groupName=" + group, Map.class);
        assertThat(failures.get("totalElements")).isEqualTo(1);
        assertThat(((List<Map<String, Object>>) failures.get("content")).get(0).get("success")).isEqualTo(false);

        final var since = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/log?groupName=" + group + "&from=" + now.minus(Duration.ofMinutes(45)), Map.class);
        assertThat(since.get("totalElements")).isEqualTo(2);

        final var window = (Map<String, Object>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/log?groupName=" + group + "&from=" + now.minus(Duration.ofMinutes(45))
                + "&to=" + now.minus(Duration.ofMinutes(30)), Map.class);
        assertThat(window.get("totalElements")).isEqualTo(1);
    }

    @Test
    void testExecutionStatsAddsRunsUpPerBucket() {
        final var group = "StatsTestGroup";
        final var from = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MINUTES);
        insertLog(group, "StatsJob", true, 100, from.plus(Duration.ofMinutes(1)));
        insertLog(group, "StatsJob", false, 300, from.plus(Duration.ofMinutes(2)));
        insertLog(group, "StatsJob", true, 50, from.plus(Duration.ofMinutes(11)));
        insertLog(group, "OtherJob", true, 1000, from.plus(Duration.ofMinutes(3)));

        final var buckets = (List<Map<String, Object>>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/stats?bucketSeconds=600&groupName=" + group + "&jobName=StatsJob"
                + "&from=" + from + "&to=" + from.plus(Duration.ofMinutes(25)), List.class);

        assertThat(buckets).hasSize(3);
        assertThat(Instant.parse((String) buckets.get(0).get("start"))).isEqualTo(from);
        assertThat(Instant.parse((String) buckets.get(2).get("end"))).isEqualTo(from.plus(Duration.ofMinutes(25)));
        assertThat(buckets.get(0)).containsEntry("successCount", 1).containsEntry("failureCount", 1)
                .containsEntry("totalDuration", 400).containsEntry("maxDuration", 300).containsEntry("averageDuration", 200);
        assertThat(buckets.get(1)).containsEntry("successCount", 1).containsEntry("failureCount", 0)
                .containsEntry("maxDuration", 50);
        assertThat(buckets.get(2)).containsEntry("successCount", 0).containsEntry("failureCount", 0);

        final var wholeGroup = (List<Map<String, Object>>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/stats?bucketSeconds=1800&groupName=" + group + "&from=" + from
                + "&to=" + from.plus(Duration.ofMinutes(30)), List.class);
        assertThat(wholeGroup).hasSize(1);
        assertThat(wholeGroup.get(0)).containsEntry("successCount", 3).containsEntry("maxDuration", 1000);
    }

    @Test
    void testExecutionStatsRefusesBadRanges() {
        final var now = Instant.now();
        for (final var query : List.of(
                "from=" + now + "&to=" + now.minusSeconds(60),
                "from=" + now.minus(Duration.ofDays(40)) + "&to=" + now,
                "from=" + now.minus(Duration.ofDays(1)) + "&to=" + now + "&bucketSeconds=60",
                "bucketSeconds=0"
        )) {
            final var response = restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI + "/stats?" + query, Map.class);
            assertThat(response.getStatusCode()).as(query).isEqualTo(BAD_REQUEST);
            assertThat(response.getBody()).as(query).containsKey("message");
        }
    }

    @Test
    void testExecutionTimelineLeavesOutJobDataAndOutput() {
        final var group = "TimelineTestGroup";
        final var id = insertLog(group, "TimelineJob", false, 75, Instant.now().minus(Duration.ofMinutes(10)));

        final var runs = (List<Map<String, Object>>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/log/timeline?groupName=" + group, List.class);

        assertThat(runs).hasSize(1);
        assertThat(runs.get(0)).containsEntry("id", id).containsEntry("duration", 75).containsEntry("success", false);
        assertThat(runs.get(0).get("stackTrace")).isNull();
        assertThat(runs.get(0).get("jsonData")).isNull();
        assertThat(restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI + "/log/timeline?limit=0", Map.class)
                .getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    void testNextFireTimesOfTrigger() {
        final var fireTimes = (List<String>) restTemplate.getForObject(URL_PREFIX + port + SCHEDULER_URI
                + "/trigger/fire-times?count=3&groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, List.class);

        assertThat(fireTimes).hasSize(3);
        final var times = fireTimes.stream().map(Instant::parse).toList();
        assertThat(Duration.between(times.get(0), times.get(1))).isEqualTo(Duration.ofSeconds(10));
        assertThat(Duration.between(times.get(1), times.get(2))).isEqualTo(Duration.ofSeconds(10));
        assertThat(restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI
                + "/trigger/fire-times?groupName=" + GROUP_NAME + "&triggerName=does-not-exist", String.class)
                .getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI
                + "/trigger/fire-times?count=0&groupName=" + GROUP_NAME + "&triggerName=" + TEST_JOB_TRIGGER, String.class)
                .getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    private static String insertLog(final String group, final String job, final boolean success, final long duration,
                                    final Instant finished) {
        final var log = new JobExecutionLog(null, group, job + "-Trigger", job, 5, success, success ? "Ok" : "Failed",
                "test-instance", TestJob.class.getName(), duration, "stack trace", "{\"key\":\"value\"}", finished);
        appContext.getBean(JobExecutionLogService.class).insertJobExecutionLog(log);
        return log.getId();
    }

    @Test
    void testJobListError() {
        final var response = restTemplate.getForEntity(URL_PREFIX + port + SCHEDULER_URI + "/BAD_GROUP_NAME" + "/jobs", String.class);
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND);
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
        final var embeddedPortRetriever = new EmbeddedPortRetriever(app);
        app.setBannerMode(Banner.Mode.OFF);
        appContext = app.run();
        port = embeddedPortRetriever.getRetrievedPort();
        scheduler = appContext.getBean(Scheduler.class);
        assertThat(COUNT_DOWN_LATCH.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(COUNT_DOWN_LATCH.getCount()).isZero();
    }

    @AfterAll
    static void tearDownOnce() throws Exception {
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

    public static class NotAJobWithStaticInitialiser {
        static {
            INITIALISED_CLASSES.add(NotAJobWithStaticInitialiser.class.getName());
        }
    }

    @JobSpec(jobName = "UnscheduledJobWithStaticInitialiser", groupName = GROUP_NAME, triggerName = "UnscheduledJobWithStaticInitialiserTrigger")
    public static class UnscheduledJobWithStaticInitialiser extends Job {
        static {
            INITIALISED_CLASSES.add(UnscheduledJobWithStaticInitialiser.class.getName());
        }

        @Override
        public void executeJob(final JobExecutionContext jobExecutionContext) {
        }
    }

    @JobSpec(
            jobName = TEST_JOB_NAME_REMOVABLE,
            groupName = GROUP_NAME,
            triggerName = TEST_JOB_TRIGGER_REMOVABLE,
            triggerState = @TriggerState(
                    enabledExp = "${test.job.enabled:ENABLED}",
                    startTypeExp = "${test.job.start-type:UNPAUSED}"),
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
