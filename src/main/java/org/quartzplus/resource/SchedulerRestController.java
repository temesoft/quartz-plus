package org.quartzplus.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartzplus.Job;
import org.quartzplus.annotation.CronTriggerSpec;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.OnErrorSpec;
import org.quartzplus.annotation.SimpleTriggerSpec;
import org.quartzplus.annotation.TriggerSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.configuration.Constants;
import org.quartzplus.configuration.QuartzPlusCommonConfiguration;
import org.quartzplus.configuration.QuartzPlusProperties;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.QuartzExecutionNode;
import org.quartzplus.domain.SchedulerInfo;
import org.quartzplus.domain.TriggerDefinition;
import org.quartzplus.exception.JobServiceException;
import org.quartzplus.exception.JobTriggerIncorrectState;
import org.quartzplus.exception.JobTriggerNotFoundException;
import org.quartzplus.service.JobExecutionLogService;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.service.QuartzExecutorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.quartzplus.configuration.Constants.PARAMETER_NAME_GROUP_NAME;
import static org.quartzplus.configuration.Constants.PARAMETER_NAME_TRIGGER_NAME;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

/**
 * This spring controller provides REST api endpoints for job/trigger management
 */
@RestController
@RequestMapping("${quartz-plus.api-uri:/" + SchedulerRestController.SERVICE_URI + "}")
public class SchedulerRestController {

    public static final String SERVICE_URI = "scheduler";

    private static final String SERVICE_INFO_URI = ""; // root level
    private static final String GROUPS_URI = "/groups";
    private static final String TRIGGERS_URI = "/triggers";
    private static final String TRIGGER_URI = "/trigger";
    private static final String PAUSE_URI = "/trigger/pause";
    private static final String RESUME_URI = "/trigger/resume";
    private static final String JOBS_URI = "/jobs";
    private static final String JOBS_EXEC_URI = "/trigger/execute";
    private static final String LOGS_URI = "/log";
    private static final String NODES_URI = "/nodes";
    private static final String CREATE_URI = "/create";
    private static final String METRICS_URI = "/metrics";
    private static final String CONFIG_URI = "/config";
    private static final String EMPTY_JSON = "{}";
    private static final String METRIC_PREFIX_WITH_DOT = Job.METRIC_PREFIX + ".";

    private final Environment environment;
    private final Scheduler scheduler;
    private final JobExecutionLogService jobExecutionLogService;
    private final List<Class<? extends Job>> jobClassList;
    private final QuartzExecutorService quartzExecutorService;
    private final MeterRegistry meterRegistry;
    private final String quartzPlusVersion;
    private final String webUri;
    private final String apiUri;

    public SchedulerRestController(final Environment environment,
                                   final Scheduler scheduler,
                                   final JobExecutionLogService jobExecutionLogService,
                                   final List<Class<? extends Job>> jobClassListValue,
                                   final List<JobsCollection> jobsCollections,
                                   final QuartzExecutorService quartzExecutorService,
                                   final MeterRegistry meterRegistry,
                                   final QuartzPlusProperties quartzPlusProperties,
                                   @Value("${quartz-plus-git.build.version}") final String quartzPlusVersion) {
        this.environment = environment;
        this.scheduler = scheduler;
        this.jobExecutionLogService = jobExecutionLogService;
        this.jobClassList = new ArrayList<>();
        this.jobClassList.addAll(jobClassListValue);
        jobsCollections.forEach(jobsCollection -> jobClassList.addAll(jobsCollection.getJobClassList()));
        this.jobClassList.sort(Comparator.comparing(Class::getName));
        this.quartzExecutorService = quartzExecutorService;
        this.meterRegistry = meterRegistry;
        this.quartzPlusVersion = quartzPlusVersion;
        this.webUri = quartzPlusProperties.getWebAdmin().getUri();
        this.apiUri = quartzPlusProperties.getApiUri();
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @RequestMapping(value = {SERVICE_INFO_URI, "/"}, method = GET)
    @Operation(summary = "Returns running quartz scheduler metadata", method = "GET")
    public ResponseEntity<SchedulerInfo> getSchedulerMetaData() throws SchedulerException {
        return new ResponseEntity<>(
                new SchedulerInfo(
                        scheduler.getMetaData(),
                        jobClassList,
                        Instant.now(),
                        jobExecutionLogService.getTablePrefix(),
                        jobExecutionLogService.getClass().getName(),
                        quartzPlusVersion,
                        webUri,
                        apiUri
                ),
                OK);
    }

    @RequestMapping(value = GROUPS_URI, method = GET)
    @Operation(summary = "Returns an array of group names", method = "GET")
    public ResponseEntity<List<String>> getGroupNames() throws SchedulerException {
        return new ResponseEntity<>(scheduler.getTriggerGroupNames(), OK);
    }

    @RequestMapping(value = TRIGGER_URI, method = GET)
    @Operation(summary = "Returns a serialized trigger definition object", method = "GET")
    public ResponseEntity<TriggerDefinition> getTrigger(
            @RequestParam(value = PARAMETER_NAME_GROUP_NAME) final String groupName,
            @RequestParam(value = PARAMETER_NAME_TRIGGER_NAME) final String triggerName
    ) throws SchedulerException, JobTriggerNotFoundException {
        final var key = new TriggerKey(triggerName, groupName);
        if (scheduler.checkExists(key)) {
            return new ResponseEntity<>(new TriggerDefinition(scheduler.getTrigger(key), scheduler.getTriggerState(key)), OK);
        } else {
            throw new JobTriggerNotFoundException();
        }
    }

    @RequestMapping(value = TRIGGER_URI, method = DELETE)
    @Operation(summary = "Removes specified trigger from the schedule", method = "DELETE")
    public ResponseEntity<Trigger> unScheduleTrigger(
            @RequestParam(value = PARAMETER_NAME_GROUP_NAME) final String groupName,
            @RequestParam(value = PARAMETER_NAME_TRIGGER_NAME) final String triggerName
    ) throws SchedulerException, JobTriggerNotFoundException {
        final var key = new TriggerKey(triggerName, groupName);
        if (scheduler.checkExists(key)) {
            final var trigger = scheduler.getTrigger(key);
            scheduler.unscheduleJob(key);
            return new ResponseEntity<>(trigger, HttpStatus.ACCEPTED);
        } else {
            throw new JobTriggerNotFoundException();
        }
    }

    @RequestMapping(value = JOBS_EXEC_URI, method = POST, consumes = "application/json")
    @Operation(summary = "Triggers specified job for immediate execution", method = "POST")
    public ResponseEntity<JobDetail> executeNow(
            @RequestParam(PARAMETER_NAME_GROUP_NAME) final String groupName,
            @RequestParam(PARAMETER_NAME_TRIGGER_NAME) final String triggerName,
            @RequestParam(Constants.PARAMETER_NAME_JOB_NAME) final String jobName,
            @RequestBody(required = false) final Map<String, Object> jsonDataMap
    ) throws SchedulerException, JobTriggerNotFoundException {
        final var jobDetail = scheduler.getJobDetail(new JobKey(jobName, groupName));
        final var trigger = scheduler.getTrigger(new TriggerKey(triggerName, groupName));
        if (jobDetail != null && trigger != null) {
            if (jsonDataMap != null && !jsonDataMap.isEmpty()) {
                jobDetail.getJobDataMap().putAll(jsonDataMap);
            }
            scheduler.triggerJob(new JobKey(jobName, groupName), jobDetail.getJobDataMap());
            return new ResponseEntity<>(jobDetail, HttpStatus.ACCEPTED);
        } else {
            throw new JobTriggerNotFoundException();
        }
    }

    @RequestMapping(value = PAUSE_URI, method = POST)
    @Operation(summary = "Pauses specified job", method = "POST")
    public ResponseEntity<Trigger> pauseJob(
            @RequestParam(value = PARAMETER_NAME_GROUP_NAME) final String groupName,
            @RequestParam(value = PARAMETER_NAME_TRIGGER_NAME) final String triggerName
    ) throws SchedulerException, JobTriggerNotFoundException, JobTriggerIncorrectState {
        final var key = new TriggerKey(triggerName, groupName);
        if (scheduler.checkExists(key)) {
            if (scheduler.getTriggerState(key) != Trigger.TriggerState.PAUSED) {
                scheduler.pauseTrigger(key);
                return new ResponseEntity<Trigger>(scheduler.getTrigger(key), HttpStatus.ACCEPTED);
            } else {
                throw new JobTriggerIncorrectState();
            }
        } else {
            throw new JobTriggerNotFoundException();
        }
    }

    @RequestMapping(value = RESUME_URI, method = POST)
    @Operation(summary = "Resumes specified paused job", method = "POST")
    public ResponseEntity<Trigger> resumeJob(
            @RequestParam(value = PARAMETER_NAME_GROUP_NAME) final String groupName,
            @RequestParam(value = PARAMETER_NAME_TRIGGER_NAME) final String triggerName
    ) throws SchedulerException, JobTriggerNotFoundException, JobTriggerIncorrectState {
        final var key = new TriggerKey(triggerName, groupName);
        if (scheduler.checkExists(key)) {
            if (scheduler.getTriggerState(key) == Trigger.TriggerState.PAUSED) {
                scheduler.resumeTrigger(key);
                return new ResponseEntity<Trigger>(scheduler.getTrigger(key), HttpStatus.ACCEPTED);
            } else {
                throw new JobTriggerIncorrectState();
            }
        } else {
            throw new JobTriggerNotFoundException();
        }
    }

    @GetMapping(JOBS_URI)
    @Operation(summary = "Returns a list of job details, and descriptions for specified group name, or all groups if none were specified",
            method = "GET")
    public ResponseEntity<List<JobDetail>> getGroupJobs(
            @RequestParam(value = Constants.PARAMETER_NAME_GROUP_NAME, required = false) final String groupName,
            @RequestParam(value = Constants.PARAMETER_NAME_JOB_NAME, required = false) final String jobName,
            @RequestParam(value = Constants.PARAMETER_NAME_INSTANCE_CLASS, required = false) final String instanceClass
    ) throws SchedulerException {
        final var result = Lists.<JobDetail>newArrayList();
        if (isNotBlank(instanceClass)) {
            try {
                final var instance = Class.forName(instanceClass);
                final var spec = instance.getAnnotation(JobSpec.class);
                result.add(scheduler.getJobDetail(new JobKey(spec.jobName(), spec.groupName())));
            } catch (final ClassNotFoundException e) {
                return new ResponseEntity<>(result, BAD_REQUEST);
            }
        } else if (isNotBlank(groupName) && isNotBlank(jobName)) {
            result.add(scheduler.getJobDetail(new JobKey(jobName, groupName)));
        } else {
            for (final var name : scheduler.getJobGroupNames()) {
                final var addGroup = !isNotBlank(groupName) || groupName.equals(name);
                if (addGroup) {
                    final var jobKeySet = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(name));
                    for (final var jobKey : jobKeySet) {
                        result.add(scheduler.getJobDetail(jobKey));
                    }
                }
            }
            result.sort((jd, jd2) -> {
                int result1 = jd.getKey().getGroup().compareToIgnoreCase(jd2.getKey().getGroup());
                if (result1 == 0) {
                    result1 = jd.getKey().getName().compareToIgnoreCase(jd2.getKey().getName());
                }
                return result1;
            });
        }
        return new ResponseEntity<>(result, OK);
    }


    @RequestMapping(value = TRIGGERS_URI, method = GET)
    @Operation(summary = "Returns a list of trigger objects", method = "GET")
    public ResponseEntity<TriggerDefinition[]> getTriggers(
            @RequestParam(value = Constants.PARAMETER_NAME_GROUP_NAME, required = false) final String groupName,
            @RequestParam(value = Constants.PARAMETER_NAME_JOB_NAME, required = false) final String jobName
    ) throws SchedulerException {
        final Set<TriggerKey> triggerKeys;
        if (groupName != null && jobName != null) {
            final var triggerList = scheduler.getTriggersOfJob(new JobKey(jobName, groupName));
            triggerKeys = Sets.newHashSet();
            for (final var trigger : triggerList) {
                triggerKeys.add(trigger.getKey());
            }
        } else if (groupName != null) {
            triggerKeys = scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(groupName));
        } else {
            triggerKeys = scheduler.getTriggerKeys(GroupMatcher.anyTriggerGroup());
        }
        final var triggerList = Lists.<TriggerDefinition>newArrayList();
        for (final var triggerKey : triggerKeys) {
            triggerList.add(new TriggerDefinition(scheduler.getTrigger(triggerKey), scheduler.getTriggerState(triggerKey)));
        }
        triggerList.sort((triggerDefinition, triggerDefinition2) -> {
            if (triggerDefinition == null || triggerDefinition2 == null) return 0;
            if (triggerDefinition.getTrigger() == null || triggerDefinition2.getTrigger() == null) return 0;
            int result = triggerDefinition.getTrigger().getKey().getGroup()
                    .compareToIgnoreCase(triggerDefinition2.getTrigger().getKey().getGroup());
            if (result == 0) {
                result = triggerDefinition.getTrigger().getKey().getName()
                        .compareToIgnoreCase(triggerDefinition2.getTrigger().getKey().getName());
            }
            return result;
        });
        return new ResponseEntity<>(triggerList.toArray(new TriggerDefinition[0]), OK);
    }

    @RequestMapping(value = LOGS_URI, method = GET)
    @Operation(description = "Returns an ordered, paginated list of latest JobExecutionLog entries", method = "GET")
    public ResponseEntity<?> getHistoryList(
            @RequestParam(value = Constants.PARAMETER_NAME_PAGE_SIZE, defaultValue = "10") final Integer pageSize,
            @RequestParam(value = Constants.PARAMETER_NAME_CURRENT_PAGE, defaultValue = "0") final Integer currentPage,
            @RequestParam(value = Constants.PARAMETER_NAME_GROUP_NAME, required = false) final String groupName,
            @RequestParam(value = Constants.PARAMETER_NAME_JOB_NAME, required = false) final String jobName,
            @RequestParam(value = Constants.PARAMETER_NAME_TRIGGER_NAME, required = false) final String triggerName,
            @RequestParam(value = Constants.PARAMETER_NAME_INSTANCE_CLASS, required = false) final String instanceClass,
            @RequestParam(value = Constants.PARAMETER_NAME_ID, required = false) final String jobExecutionLogId
    ) {
        if (isNotBlank(jobExecutionLogId)) {
            final var jobExecutionLog = jobExecutionLogService.getJobExecutionLog(jobExecutionLogId);
            if (jobExecutionLog.isPresent()) {
                return new ResponseEntity<>(jobExecutionLog.get(), OK);
            } else {
                return new ResponseEntity<List<JobExecutionLog>>(BAD_REQUEST);
            }
        } else {
            return new ResponseEntity<>(
                    jobExecutionLogService.getJobExecutionLogList(
                            pageSize,
                            currentPage,
                            groupName,
                            jobName,
                            triggerName,
                            instanceClass
                    ), OK);
        }
    }

    @RequestMapping(value = NODES_URI, method = GET)
    @Operation(summary = "Returns a list of scheduler execution nodes", method = "GET")
    public ResponseEntity<List<QuartzExecutionNode>> getAvailableNodes() {
        return new ResponseEntity<>(jobExecutionLogService.getQuartzExecutionNodeList(), OK);
    }

    @RequestMapping(value = METRICS_URI, method = GET)
    @Operation(summary = "Returns quartz scheduler metrics", method = "GET")
    public ResponseEntity<Map<String, ?>> getMetrics() throws SchedulerException {
        final var meters = meterRegistry.getMeters();
        final var jobList = Lists.<String>newArrayList();
        for (final var meter : meters) {
            final var meterKey = meter.getId().getName();
            if (meterKey.startsWith(METRIC_PREFIX_WITH_DOT)) {
                final var groupAndName = meterKey
                        .replace(Job.METRIC_PREFIX + ".", "")
                        .replace(".success", "")
                        .replace(".duration", "")
                        .replace(".failure", "");
                if (!jobList.contains(groupAndName)) {
                    jobList.add(groupAndName);
                }
            }
        }
        final Map<String, ?> map = Map.of(
                "instanceId", scheduler.getSchedulerInstanceId(),
                "clustered", scheduler.getMetaData().isJobStoreClustered(),
                "jobList", jobList,
                "meters", onlyExecutionRelatedMetrics(meterRegistry.getMeters(), false),
                "timers", onlyExecutionRelatedMetrics(meterRegistry.getMeters(), true)
        );
        return new ResponseEntity<>(map, OK);
    }

    @RequestMapping(value = CONFIG_URI, method = GET)
    @Operation(summary = "Returns all configuration properties used", method = "GET")
    public ResponseEntity<Map<String, Object>> getConfig() {
        final var sourceProps = QuartzPlusCommonConfiguration.getConfigProperties(environment);
        final var result = Maps.<String, Object>newTreeMap();
        for (final var key : sourceProps.keySet()) {
            if (key.contains("password") || key.contains("user") || key.contains("secret")) {
                result.put(key, "********");
            } else {
                result.put(key, sourceProps.get(key));
            }
        }
        return new ResponseEntity<>(result, OK);
    }

    private Map<String, ?> onlyExecutionRelatedMetrics(final List<Meter> meterList, final boolean timersOnly) {
        return meterList.stream()
                .filter(meter -> {
                    if (timersOnly) {
                        return meter instanceof Timer;
                    } else {
                        return true;
                    }
                })
                .filter(meter -> meter.getId().getName().startsWith(METRIC_PREFIX_WITH_DOT))
                .collect(Collectors.toMap(meter -> meter.getId().getName(), meter -> {
                    if (timersOnly) {
                        return new double[]{
                                ((Timer) meter).mean(TimeUnit.MILLISECONDS),
                                ((Timer) meter).max(TimeUnit.MILLISECONDS)
                        };
                    } else {
                        return meter.measure().iterator().next().getValue();
                    }
                }));
    }

    @RequestMapping(value = CREATE_URI, method = PUT)
    @Operation(summary = "Creates a new job/ trigger with specified parameters", method = "PUT")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> createJob(
            @RequestParam(value = Constants.PARAMETER_NAME_INSTANCE_CLASS) final String jobClass,
            @RequestParam(value = PARAMETER_NAME_GROUP_NAME) final String groupName,
            @RequestParam(value = Constants.PARAMETER_NAME_JOB_NAME) final String jobName,
            @RequestParam(value = PARAMETER_NAME_TRIGGER_NAME) final String triggerName,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_JOB_DESCRIPTION, defaultValue = "") final String jobDescription,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_START_PAUSED, defaultValue = "false") final boolean startPaused,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_CALENDAR, defaultValue = "org.quartzplus.calendar.NoRestrictionsCalendar") final String calendar,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_JSON_DATA, defaultValue = "{}") final String jsonData,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_CRON_EXP) final String cronExpression,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_TIME_ZONE, defaultValue = "UTC") final String timeZone,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_REPEAT_COUNT) final Integer repeatCount,
            @RequestParam(required = false, value = Constants.PARAMETER_NAME_REPEAT_INTERVAL) final Long repeatInterval
    ) throws SchedulerException, IllegalAccessException, JobServiceException, InstantiationException, IOException {
        try {
            final var clazz = (Class<? extends Job>) Class.forName(jobClass);
            final var triggerParameterOverridesBuilder = ImmutableMap.<String, Object>builder();
            if (isNotBlank(cronExpression)) {
                triggerParameterOverridesBuilder.put(Constants.PARAMETER_NAME_CRON_EXP, cronExpression);
            }
            if (isNotBlank(timeZone)) {
                triggerParameterOverridesBuilder.put(Constants.PARAMETER_NAME_TIME_ZONE, TimeZone.getTimeZone(timeZone));
            }
            if (repeatCount != null) {
                triggerParameterOverridesBuilder.put(Constants.PARAMETER_NAME_REPEAT_COUNT, repeatCount);
            }
            if (repeatInterval != null) {
                triggerParameterOverridesBuilder.put(Constants.PARAMETER_NAME_REPEAT_INTERVAL, repeatInterval);
            }
            @SuppressWarnings("ImmutableAnnotationChecker") // no format
            final var jobSpec = new JobSpec() {
                @Override
                public String jobName() {
                    return jobName;
                }

                @Override
                public String groupName() {
                    return groupName;
                }

                @Override
                public String triggerName() {
                    return triggerName;
                }

                @Override
                public String jobDescription() {
                    return jobDescription;
                }

                @Override
                public int hashCode() {
                    return super.hashCode();
                }

                @Override
                public boolean equals(final Object obj) {
                    return super.equals(obj);
                }

                @Override
                public TriggerState triggerState() {
                    return new TriggerState() {
                        @Override
                        public State enabled() {
                            return State.ENABLED;
                        }

                        @Override
                        public String enabledExp() {
                            return null;
                        }

                        @Override
                        public StartType startType() {
                            return startPaused ? StartType.PAUSED : StartType.UNPAUSED;
                        }

                        @Override
                        public String startTypeExp() {
                            return null;
                        }

                        @Override
                        public Class<? extends Annotation> annotationType() {
                            return TriggerState.class;
                        }

                        @Override
                        public int hashCode() {
                            return super.hashCode();
                        }

                        @Override
                        public boolean equals(final Object obj) {
                            return super.equals(obj);
                        }
                    };
                }

                @Override
                @SuppressWarnings("ImmutableAnnotationChecker")
                public TriggerSpec trigger() {
                    return new TriggerSpec() {
                        @Override
                        public SimpleTriggerSpec simpleTrigger() {
                            if (repeatCount != null && repeatInterval != null) {
                                return new SimpleTriggerSpec() {
                                    @Override
                                    public int repeatCount() {
                                        return repeatCount;
                                    }

                                    @Override
                                    public String repeatCountExp() {
                                        return "";
                                    }

                                    @Override
                                    public long repeatInterval() {
                                        return repeatInterval;
                                    }

                                    @Override
                                    public String repeatIntervalExp() {
                                        return "";
                                    }

                                    @Override
                                    public Class<? extends Annotation> annotationType() {
                                        return SimpleTriggerSpec.class;
                                    }

                                    @Override
                                    public int hashCode() {
                                        return super.hashCode();
                                    }

                                    @Override
                                    public boolean equals(final Object obj) {
                                        return super.equals(obj);
                                    }
                                };
                            }
                            return null;
                        }

                        @Override
                        public CronTriggerSpec cronTrigger() {
                            if (isNotBlank(cronExpression)) {
                                return new CronTriggerSpec() {
                                    @Override
                                    public String cronExpression() {
                                        return cronExpression;
                                    }

                                    @Override
                                    public String cronExpressionExp() {
                                        return "";
                                    }

                                    @Override
                                    public String timeZone() {
                                        return timeZone;
                                    }

                                    @Override
                                    public String timeZoneExp() {
                                        return "";
                                    }

                                    @Override
                                    public Class<? extends Annotation> annotationType() {
                                        return CronTriggerSpec.class;
                                    }

                                    @Override
                                    public int hashCode() {
                                        return super.hashCode();
                                    }

                                    @Override
                                    public boolean equals(final Object obj) {
                                        return super.equals(obj);
                                    }
                                };
                            }
                            return null;
                        }

                        @Override
                        public Class<? extends Annotation> annotationType() {
                            return null;
                        }

                        @Override
                        public int hashCode() {
                            return super.hashCode();
                        }

                        @Override
                        public boolean equals(final Object obj) {
                            return super.equals(obj);
                        }
                    };
                }

                @Override
                public OnErrorSpec onError() {
                    return new OnErrorSpec() {
                        @Override
                        public int onErrorRepeatCount() {
                            return 0;
                        }

                        @Override
                        public String onErrorRepeatCountExp() {
                            return "";
                        }

                        @Override
                        public int onErrorRepeatDelay() {
                            return 0;
                        }

                        @Override
                        public String onErrorRepeatDelayExp() {
                            return "";
                        }

                        @Override
                        public Class<? extends Annotation> annotationType() {
                            return OnErrorSpec.class;
                        }

                        @Override
                        public int hashCode() {
                            return super.hashCode();
                        }

                        @Override
                        public boolean equals(final Object obj) {
                            return super.equals(obj);
                        }
                    };
                }

                @Override
                public String calendarClass() {
                    return calendar;
                }

                @Override
                public Class<? extends Annotation> annotationType() {
                    return JobSpec.class;
                }
            };
            final var builder = ImmutableMap.<String, Object>builder();
            if (isNotBlank(jsonData) && !EMPTY_JSON.equals(jsonData)) {
                builder.putAll(OBJECT_MAPPER.readValue(jsonData, Map.class));
            }

            final var result = quartzExecutorService.registerJobClass(
                    scheduler,
                    clazz,
                    jobSpec,
                    builder.build(),
                    triggerParameterOverridesBuilder.build()
            );
            if (result.isRegistered()) {
                return new ResponseEntity<>(result.getMessage(), HttpStatus.CREATED);
            } else if (result.isAlreadyExist()) {
                return new ResponseEntity<>(result.getMessage(), HttpStatus.ALREADY_REPORTED);
            } else if (result.isDisabled()) {
                return new ResponseEntity<>(result.getMessage(), HttpStatus.NOT_MODIFIED);
            } else {
                // this should not happen, but just in case...BAD_REQUEST
                return new ResponseEntity<>(result.getMessage(), BAD_REQUEST);
            }
        } catch (final NoSuchMethodException | ClassNotFoundException | InvocationTargetException cnf) {
            return new ResponseEntity<>("Class invocation error: " + cnf.getMessage(), BAD_REQUEST);
        }
    }
}
