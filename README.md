# quartz-plus

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0%2B-brightgreen)](https://spring.io/projects/spring-boot)
[![Javadoc](https://javadoc.io/badge2/io.github.temesoft/quartz-plus/javadoc.svg)](https://javadoc.io/doc/io.github.temesoft/quartz-plus)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.temesoft/quartz-plus.svg)](https://central.sonatype.com/artifact/io.github.temesoft/quartz-plus)
[![Java CI](https://github.com/temesoft/quartz-plus/actions/workflows/main.yml/badge.svg)](https://github.com/temesoft/quartz-plus/actions/workflows/main.yml)


**quartz-plus** is a Spring Boot auto-configuration library that enriches the
standard [Quartz Scheduler](https://quartz-scheduler.org/) with declarative annotation-driven job registration,
automatic execution logging, retry-on-error support, Micrometer metrics, and a built-in web administration UI - all
without boilerplate.

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Core Concepts](#core-concepts)
    - [The `Job` Base Class](#the-job-base-class)
    - [The `@JobSpec` Annotation](#the-jobspec-annotation)
    - [Registering Jobs with `JobsCollection`](#registering-jobs-with-jobscollection)
- [Trigger Types](#trigger-types)
    - [Simple Trigger](#simple-trigger)
    - [Cron Trigger](#cron-trigger)
    - [Dynamic Configuration with SpEL and Property Placeholders](#dynamic-configuration-with-spel-and-property-placeholders)
- [Trigger Lifecycle](#trigger-lifecycle)
- [Error Handling and Retry](#error-handling-and-retry)
- [Trigger Interfaces (Programmatic Configuration)](#trigger-interfaces-programmatic-configuration)
- [Execution Logging](#execution-logging)
- [Metrics](#metrics)
- [Business Calendars](#business-calendars)
- [Web Administration UI](#web-administration-ui)
- [REST API Reference](#rest-api-reference)
- [Database Migration (Flyway)](#database-migration-flyway)
- [Configuration Properties Reference](#configuration-properties-reference)
- [Internal Jobs](#internal-jobs)
- [Advanced: Clustered Scheduling](#advanced-clustered-scheduling)
- [Examples](#examples)
- [Contributing](#contributing)
- [License](#license)

---

## Features

| Feature                            | Description                                                               |
|------------------------------------|---------------------------------------------------------------------------|
| Annotation-driven job registration | Declare all job metadata with `@JobSpec` - no XML, no boilerplate         |
| Cron and Simple triggers           | Both trigger types with static or dynamic (SpEL/properties) values        |
| Retry on error                     | Configurable retry count and delay when `executeJob()` throws             |
| Execution log                      | Per-run audit trail with duration, status, stack trace, and captured logs |
| Micrometer metrics                 | Success counter, failure counter, and duration timer per job              |
| Web Administration UI              | Built-in SPA for viewing, pausing, resuming, and triggering jobs          |
| REST API                           | Full JSON API with Swagger annotations for all scheduler operations       |
| Spring autowiring in jobs          | `@Autowired` fields work natively inside job classes                      |
| Clustered support                  | Works with Quartz JDBC JobStore for multi-node clusters                   |
| Flyway migrations                  | Bundled SQL migrations for H2, MySQL, and PostgreSQL                      |
| Business calendar support          | Plug in any Quartz `Calendar` to exclude time windows                     |

---

## Requirements

- Java 17+
- Spring Boot 4.0+
- A configured `DataSource` bean (for JDBC-backed execution log and clustered mode; optional for in-memory mode)

---

## Installation

### Maven installation

```xml

<dependency>
    <groupId>io.github.temesoft</groupId>
    <artifactId>quartz-plus</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle installation

```gradle
testImplementation 'io.github.temesoft:quartz-plus:1.0.1'
```


No `@EnableScheduling` or extra `@Import` is required - quartz-plus registers itself through Spring Boot's
auto-configuration mechanism.

---

## Quick Start

### 1. Create a Job

Extend `org.quartzplus.Job` and implement `executeJob()`. Annotate the class with `@JobSpec`.

```java
import org.quartz.JobExecutionContext;
import org.quartzplus.Job;
import org.quartzplus.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JobSpec(
        jobName = "HelloWorldJob",
        groupName = "ExampleGroup",
        triggerName = "HelloWorldJob-Trigger",
        jobDescription = "Prints a greeting every minute",
        trigger = @TriggerSpec(
                cronTrigger = @CronTriggerSpec(cronExpression = "0 * * * * ?")
        )
)
public class HelloWorldJob extends Job {

    private static final Logger LOG = LoggerFactory.getLogger(HelloWorldJob.class);

    @Override
    public void executeJob(JobExecutionContext ctx) {
        LOG.info("Hello, World!");
    }
}
```

### 2. Register the Job

Expose a `JobsCollection` bean so quartz-plus can discover your job at startup:

```java
import org.quartzplus.Job;
import org.quartzplus.service.JobsCollection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class JobConfiguration {

    @Bean
    public JobsCollection myJobs() {
        return () -> List.of(HelloWorldJob.class);
    }
}
```

### 3. Add Quartz and DataSource configuration (application.yml)

```yaml
spring:
  quartz:
    job-store-type: jdbc          # Use 'memory' for in-memory only
    wait-for-jobs-to-complete-on-shutdown: true
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: user
    password: secret

quartz-plus:
  db-migration: true              # Run Flyway migrations on startup
```

That's it. The job will be automatically registered, scheduled, and visible in the web UI at
`http://localhost:8080/scheduler/admin`.

---

## Core Concepts

### The `Job` Base Class

All quartz-plus jobs extend `org.quartzplus.Job`, which extends Quartz's `QuartzJobBean`. You never override `execute()`
directly; instead, you implement:

```java
public abstract void executeJob(JobExecutionContext ctx) throws Exception;
```

The base class transparently wraps this method with:

- **Log capture** - all SLF4J log messages at or above `getLoggerLevel()` (default: `INFO`) produced during the run are
  captured and stored in the execution log.
- **Retry logic** - if `executeJob()` throws, the retry policy defined by `@OnErrorSpec` or the `OnErrorRepeatable`
  interface is applied automatically.
- **Metrics** - success counter, failure counter, and a duration timer are recorded via Micrometer.
- **Lifecycle hooks** - `onSuccess(ctx)` and `onFailure(ctx, throwable)` are called after the final outcome.

**Override the log capture level:**

```java

@Override
public ch.qos.logback.classic.Level getLoggerLevel() {
    return Level.DEBUG; // capture DEBUG and above
}
```

**Override lifecycle hooks:**

```java

@Override
public void onSuccess(JobExecutionContext ctx) {
    LOG.info("Job finished cleanly at {}", Instant.now());
}

@Override
public void onFailure(JobExecutionContext ctx, Throwable e) {
    alertingService.send("Job failed: " + e.getMessage());
}
```

---

### The `@JobSpec` Annotation

`@JobSpec` is placed on the job class and defines its identity, schedule, trigger state, error handling, and calendar.
It is the single source of truth for job configuration.

```java
@JobSpec(
        jobName = "MyJob",
        groupName = "MyGroup",          // default: "MainGroup"
        triggerName = "MyJob-Trigger",
        jobDescription = "Does something important",
        triggerState = @TriggerState(...),
        trigger = @TriggerSpec(...),
        onError = @OnErrorSpec(...),
        calendarClass = "com.example.HolidayCalendar"
)
```

| Attribute        | Type            | Default                  | Description                                                                |
|------------------|-----------------|--------------------------|----------------------------------------------------------------------------|
| `jobName`        | `String`        | *(required)*             | Unique name for the Quartz `JobDetail`.                                    |
| `groupName`      | `String`        | `"MainGroup"`            | Logical group for both the job and its trigger.                            |
| `triggerName`    | `String`        | *(required)*             | Unique name for the Quartz `Trigger`.                                      |
| `jobDescription` | `String`        | `""`                     | Human-readable description stored in `JobDetail`.                          |
| `triggerState`   | `@TriggerState` | enabled, unpaused        | Controls whether the trigger is registered and whether it starts paused.   |
| `trigger`        | `@TriggerSpec`  | -                        | Defines the scheduling strategy (cron or simple).                          |
| `onError`        | `@OnErrorSpec`  | no retries               | Defines error retry behaviour.                                             |
| `calendarClass`  | `String`        | `NoRestrictionsCalendar` | Fully qualified class name of a Quartz `Calendar` to exclude time windows. |

---

### Registering Jobs with `JobsCollection`

`JobsCollection` is a functional interface. Any `@Bean` that returns it will have its jobs discovered and registered at
startup.

```java

@Bean
public JobsCollection reportingJobs() {
    return () -> List.of(
            DailyReportJob.class,
            WeeklyReportJob.class,
            MonthlyReportJob.class
    );
}
```

You can declare as many `JobsCollection` beans as you need - they are all collected at startup. Every class in the list
**must** be annotated with `@JobSpec` and extend `Job`.

---

## Trigger Types

### Simple Trigger

A `SimpleTrigger` fires at a fixed interval for a specified number of repetitions.

Configure via `@SimpleTriggerSpec` inside `@TriggerSpec`:

```java
trigger =@TriggerSpec(
        simpleTrigger = @SimpleTriggerSpec(
                repeatCount = SimpleTrigger.REPEAT_INDEFINITELY, // -1 = forever
                repeatInterval = 60_000L                            // every 60 seconds
        )
)
```

| Attribute           | Type     | Default | Description                                                                                                                    |
|---------------------|----------|---------|--------------------------------------------------------------------------------------------------------------------------------|
| `repeatCount`       | `int`    | `0`     | Number of additional repetitions after the first run. `-1` (or `REPEAT_INDEFINITELY`) repeats forever. `0` fires exactly once. |
| `repeatCountExp`    | `String` | `""`    | SpEL or property placeholder for repeat count, overrides `repeatCount` if set.                                                 |
| `repeatInterval`    | `long`   | `0`     | Interval between executions in **milliseconds**.                                                                               |
| `repeatIntervalExp` | `String` | `""`    | SpEL or property placeholder for interval, overrides `repeatInterval` if set.                                                  |

---

### Cron Trigger

A `CronTrigger` fires according to a standard Quartz cron expression.

```java
trigger =@TriggerSpec(
        cronTrigger = @CronTriggerSpec(
                cronExpression = "0 0 8 * * MON-FRI",  // 8 AM, weekdays
                timeZone = "America/New_York"
        )
)
```

| Attribute           | Type     | Default | Description                                                                 |
|---------------------|----------|---------|-----------------------------------------------------------------------------|
| `cronExpression`    | `String` | `""`    | Static Quartz cron expression.                                              |
| `cronExpressionExp` | `String` | `""`    | SpEL or property placeholder for cron, overrides `cronExpression` if set.   |
| `timeZone`          | `String` | `""`    | Time zone ID (e.g. `"UTC"`, `"Europe/London"`). Empty means system default. |
| `timeZoneExp`       | `String` | `""`    | SpEL or property placeholder for time zone, overrides `timeZone` if set.    |

**Common Cron Expression Examples:**

| Expression               | Meaning                                 |
|--------------------------|-----------------------------------------|
| `0 * * * * ?`            | Every minute                            |
| `0 0 * * * ?`            | Top of every hour                       |
| `0 0 8 * * MON-FRI`      | 8:00 AM, Monday–Friday                  |
| `0 0/5 * * * ?`          | Every 5 minutes                         |
| `0 30 10-13 ? * WED,FRI` | 10:30, 11:30, 12:30, 13:30 on Wed & Fri |
| `0 0 0 1 * ?`            | Midnight on the 1st of each month       |
| `0 0 2 * * ?`            | 2:00 AM every day                       |

---

### Dynamic Configuration with SpEL and Property Placeholders

Every `*Exp` attribute supports Spring property placeholders (`${...}`) and SpEL expressions (`#{...}`). This allows
schedules and trigger behaviour to be externalized into `application.yml` or environment variables without recompiling.

```java
// In the @JobSpec annotation:
triggerState =@TriggerState(enabledExp = "${my-job.enabled:true}"),
trigger =@TriggerSpec(
        cronTrigger = @CronTriggerSpec(
                cronExpressionExp = "${my-job.cron:0 0 * * * ?}",
                timeZoneExp = "${my-job.timezone:UTC}"
        )
)
```

```yaml
# application.yml
my-job:
  enabled: true
  cron: "0 0 6 * * MON-FRI"
  timezone: "Europe/London"
```

The `*Exp` variant always **overrides** its static counterpart when non-blank.

---

## Trigger Lifecycle

Controlled by `@TriggerState`:

```java
triggerState =@TriggerState(
        enabled = TriggerState.State.ENABLED,       // or DISABLED
        startType = TriggerState.StartType.UNPAUSED   // or PAUSED
)
```

Or with dynamic values:

```java
triggerState =@TriggerState(
        enabledExp = "${my-job.enabled:ENABLED}",
        startTypeExp = "${my-job.start-type:UNPAUSED}"
)
```

| Attribute      | Type        | Default    | Description                                                                                                       |
|----------------|-------------|------------|-------------------------------------------------------------------------------------------------------------------|
| `enabled`      | `State`     | `ENABLED`  | `ENABLED` - trigger is registered and fires. `DISABLED` - trigger is not created at all.                          |
| `enabledExp`   | `String`    | `""`       | SpEL/property override for `enabled`. Expects a `State` enum name.                                                |
| `startType`    | `StartType` | `UNPAUSED` | `UNPAUSED` - trigger fires on schedule. `PAUSED` - trigger is registered but suspended; must be resumed manually. |
| `startTypeExp` | `String`    | `""`       | SpEL/property override for `startType`. Expects a `StartType` enum name.                                          |

**Using `PAUSED` start type is useful for:**

- Jobs that should exist in the scheduler but only be triggered manually or by an external event.
- Feature-flagging a job during a deployment without removing it from the codebase.

---

## Error Handling and Retry

Configure via `@OnErrorSpec`:

```java
onError =@OnErrorSpec(
        onErrorRepeatCount = 3,      // retry up to 3 times
        onErrorRepeatDelay = 5000    // wait 5 seconds between retries
)
```

Or with dynamic values:

```java
onError =@OnErrorSpec(
        onErrorRepeatCountExp = "${my-job.retry.count:3}",
        onErrorRepeatDelayExp = "${my-job.retry.delay:5000}"
)
```

| Attribute               | Type     | Default | Description                                                                  |
|-------------------------|----------|---------|------------------------------------------------------------------------------|
| `onErrorRepeatCount`    | `int`    | `0`     | Number of retry attempts if `executeJob()` throws. `0` means no retries.     |
| `onErrorRepeatCountExp` | `String` | `""`    | SpEL/property override for retry count.                                      |
| `onErrorRepeatDelay`    | `int`    | `0`     | Delay in **milliseconds** between retry attempts. `0` means immediate retry. |
| `onErrorRepeatDelayExp` | `String` | `""`    | SpEL/property override for retry delay.                                      |

When retries are exhausted, `onFailure(ctx, throwable)` is called and a `JobExecutionException` is thrown. Each retry
attempt stores the attempt count and last exception class/message in the `JobDataMap`, accessible in `onFailure()`:

```java

@Override
public void onFailure(JobExecutionContext ctx, Throwable e) {
    int retryCount = ctx.getMergedJobDataMap().getInt("RetryCount");
    String lastError = (String) ctx.getMergedJobDataMap().get("LastThrowableMessage");
    LOG.error("Job failed after {} retries: {}", retryCount, lastError);
}
```

---

## Trigger Interfaces (Programmatic Configuration)

As an alternative to annotations, jobs can implement one or more interfaces to provide trigger configuration
programmatically at runtime. These interfaces are checked by the executor service and take precedence over static
annotation values.

### `SimpleTriggerable`

```java

@JobSpec(jobName = "MyJob", triggerName = "MyJob-Trigger")
public class MyJob extends Job implements SimpleTriggerable {

    @Value("${my-job.interval:30000}")
    private long interval;

    @Override
    public int getRepeatCount() {
        return REPEAT_INDEFINITELY;
    }

    @Override
    public long getRepeatInterval() {
        return interval;
    }

    @Override
    public void executeJob(JobExecutionContext ctx) { /* ... */ }
}
```

### `CronTriggerable`

```java

@JobSpec(jobName = "MyJob", triggerName = "MyJob-Trigger")
public class MyJob extends Job implements CronTriggerable {

    @Value("${my-job.cron}")
    private String cron;

    @Override
    public String getCronExpression() {
        return cron;
    }

    @Override
    public TimeZone getTriggerTimeZone() {
        return TimeZone.getTimeZone("UTC");
    }

    @Override
    public void executeJob(JobExecutionContext ctx) { /* ... */ }
}
```

### `OnErrorRepeatable`

```java
public class MyJob extends Job implements OnErrorRepeatable {

    @Override
    public int getOnErrorRepeatCount() {
        return 5;
    }

    @Override
    public int getOnErrorRepeatDelay() {
        return 2000;
    } // 2 seconds

    @Override
    public void executeJob(JobExecutionContext ctx) { /* ... */ }
}
```

### `TimeConstrainable`

Applies a start and/or end boundary to any trigger type:

```java
public class MyJob extends Job implements TimeConstrainable {

    @Override
    public Instant getStartTime() {
        return Instant.parse("2025-01-01T00:00:00Z"); // null = no start constraint
    }

    @Override
    public Instant getEndTime() {
        return Instant.parse("2025-12-31T23:59:59Z"); // null = no end constraint
    }

    @Override
    public void executeJob(JobExecutionContext ctx) { /* ... */ }
}
```

---

## Execution Logging

Every job execution is recorded in a `JobExecutionLog` entry containing:

| Field            | Description                                                               |
|------------------|---------------------------------------------------------------------------|
| `id`             | Unique UUID for the log record                                            |
| `groupName`      | Group the job belongs to                                                  |
| `jobName`        | Name of the job                                                           |
| `triggerName`    | Name of the trigger that fired                                            |
| `instanceClass`  | Fully qualified class name of the job                                     |
| `success`        | `true` if the job completed without error                                 |
| `errorMessage`   | Error message if the job failed                                           |
| `stackTrace`     | Full stack trace on failure                                               |
| `jsonData`       | Serialized `JobDataMap` at the time of execution (includes captured logs) |
| `duration`       | Execution time in milliseconds                                            |
| `fireInstanceId` | Quartz-assigned unique ID for the firing instance                         |
| `priority`       | Trigger priority                                                          |
| `createTime`     | Timestamp of when the log record was written                              |

### Storage Modes

**DataSource (default):** Persisted to the database via JDBC. Survives restarts. Automatically cleaned up by the
internal `ExecutionLogCleanupJob`.

**InMemory:** Stored in a bounded in-memory list. Fast but volatile. Suitable for development or when a database is not
available.

Configure via `application.yml`:

```yaml
quartz-plus:
  job-execution-log:
    type: DataSource       # or InMemory
    in-memory-max-size: 1000  # maximum entries when using InMemory (default: 1000)
```

### Table Prefix

The execution log table is created by Flyway with the prefix `QRTZ_` by default. To change it:

```yaml
spring:
  flyway:
    placeholders:
      executionLogTablePrefix: MY_PREFIX_
```

---

## Metrics

quartz-plus automatically publishes Micrometer metrics for every job using the naming pattern:

```
jobs.<groupName>.<jobName>.success   (Counter)
jobs.<groupName>.<jobName>.failure   (Counter)
jobs.<groupName>.<jobName>.duration  (Timer)
```

These integrate with any Micrometer-compatible backend (Prometheus, Datadog, CloudWatch, etc.).

If no `MeterRegistry` bean is found, a `SimpleMeterRegistry` is created automatically.

**Prometheus example scrape output:**

```
jobs_ExampleGroup_HelloWorldJob_success_total 42.0
jobs_ExampleGroup_HelloWorldJob_failure_total 1.0
jobs_ExampleGroup_HelloWorldJob_duration_seconds_max 0.023
```

---

## Business Calendars

Quartz Calendars allow you to exclude specific time windows from trigger firing (e.g., holidays, maintenance windows).

### Using the Built-in Calendar

The default `NoRestrictionsCalendar` imposes no exclusions. To use a custom calendar, set `calendarClass` in `@JobSpec`:

```java

@JobSpec(
        jobName = "HolidayAwareJob",
        triggerName = "HolidayAwareJob-Trigger",
        calendarClass = "com.example.scheduler.CompanyHolidayCalendar"
)
public class HolidayAwareJob extends Job { /* ... */
}
```

### Registering Calendars

Register calendar class names in `application.yml`:

```yaml
quartz-plus:
  calendars:
    - com.example.scheduler.CompanyHolidayCalendar
    - com.example.scheduler.MaintenanceWindowCalendar
```

The calendar class must implement `org.quartz.Calendar` and have a no-arg constructor.

---

## Web Administration UI

A built-in single-page administration interface is available at `/scheduler/admin` (default). It provides:

- **Scheduler** - metadata, instance ID, cluster status
- **Triggers** - list all triggers with state, next/previous fire times
- **Jobs** - browse and inspect job details
- **Logs** - paginated execution history with full log capture, duration, and stack traces
- **Timeline** - graphical execution timeline
- **Nodes** - cluster node visualization
- **Metrics** - real-time success/failure/duration metrics
- **Configuration** - view all active quartz-plus and Quartz properties (passwords are masked)

Configure the UI:

```yaml
quartz-plus:
  web-admin:
    enabled: true          # default: true
    uri: /scheduler/admin  # default: /scheduler/admin
```

To disable the web UI while keeping the REST API:

```yaml
quartz-plus:
  web-admin:
    enabled: false
```

---

## REST API Reference

All endpoints are mounted at the `api-uri` prefix (default: `/scheduler`). Full OpenAPI/Swagger annotations are included
for auto-generated API documentation.

| Method   | Path                         | Description                                                                          |
|----------|------------------------------|--------------------------------------------------------------------------------------|
| `GET`    | `/scheduler`                 | Scheduler metadata and info                                                          |
| `GET`    | `/scheduler/groups`          | List all trigger group names                                                         |
| `GET`    | `/scheduler/triggers`        | List all triggers (optional `?groupName=`)                                           |
| `GET`    | `/scheduler/trigger`         | Get a specific trigger (`?groupName=&triggerName=`)                                  |
| `DELETE` | `/scheduler/trigger`         | Remove a trigger (`?groupName=&triggerName=`)                                        |
| `GET`    | `/scheduler/jobs`            | List jobs (optional `?groupName=`, `?jobName=`, `?instanceClass=`)                   |
| `POST`   | `/scheduler/trigger/execute` | Immediately fire a job with optional JSON body                                       |
| `POST`   | `/scheduler/trigger/pause`   | Pause a trigger (`?groupName=&triggerName=`)                                         |
| `POST`   | `/scheduler/trigger/resume`  | Resume a paused trigger (`?groupName=&triggerName=`)                                 |
| `GET`    | `/scheduler/log`             | Paginated execution log (`?pageSize=&currentPage=&groupName=&jobName=&triggerName=`) |
| `GET`    | `/scheduler/nodes`           | List cluster execution nodes                                                         |
| `GET`    | `/scheduler/metrics`         | Job execution metrics                                                                |
| `GET`    | `/scheduler/config`          | Active configuration properties (passwords masked)                                   |
| `PUT`    | `/scheduler/create`          | Dynamically register a new job/trigger                                               |

### Execute a Job Immediately

```bash
curl -X POST "http://localhost:8080/scheduler/trigger/execute?groupName=ExampleGroup&triggerName=HelloWorldJob-Trigger&jobName=HelloWorldJob" \
     -H "Content-Type: application/json" \
     -d '{"myParam": "value"}'
```

### Create a Job via REST

```bash
curl -X PUT "http://localhost:8080/scheduler/create" \
     -G \
     --data-urlencode "instanceClass=com.example.HelloWorldJob" \
     --data-urlencode "groupName=ExampleGroup" \
     --data-urlencode "jobName=HelloWorldJob" \
     --data-urlencode "triggerName=HelloWorldJob-Trigger" \
     --data-urlencode "cronExpression=0 * * * * ?" \
     --data-urlencode "timeZone=UTC"
```

---

## Database Migration (Flyway)

quartz-plus ships with Flyway migration scripts for three databases. They create both the standard Quartz tables and the
execution log table.

| Database        | Migration Location                    |
|-----------------|---------------------------------------|
| MySQL / MariaDB | `classpath:org/quartzplus/mysql`      |
| PostgreSQL      | `classpath:org/quartzplus/postgresql` |
| H2 (testing)    | `classpath:org/quartzplus/h2`         |

Configure:

```yaml
quartz-plus:
  db-migration: true                                   # default: true; set false to disable
  db-migration-location: classpath:org/quartzplus/mysql  # default; change for your DB
  db-migration-table: flyway_quartz_schema_history     # default Flyway history table name
```

The Flyway migration runs in its own history table (`flyway_quartz_schema_history`) separate from your application's
Flyway history table, so the two do not interfere.

To use your own table prefix for Quartz and the execution log tables:

```yaml
spring:
  flyway:
    placeholders:
      quartzTablePrefix: QRTZ_
      executionLogTablePrefix: QRTZ_
```

---

## Configuration Properties Reference

### `quartz-plus.*` Properties

| Property                                           | Type           | Default                          | Description                                                                                                                          |
|----------------------------------------------------|----------------|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `quartz-plus.db-migration`                         | `boolean`      | `true`                           | Enable or disable automatic Flyway schema migration on startup.                                                                      |
| `quartz-plus.db-migration-location`                | `String`       | `classpath:org/quartzplus/mysql` | Classpath location of the Flyway SQL migration scripts. Change to `postgresql` or `h2` as needed.                                    |
| `quartz-plus.db-migration-table`                   | `String`       | `flyway_quartz_schema_history`   | Name of the Flyway schema history table used by quartz-plus migrations.                                                              |
| `quartz-plus.calendars`                            | `List<String>` | `null`                           | Fully qualified class names of Quartz `Calendar` implementations to register at startup.                                             |
| `quartz-plus.api-uri`                              | `String`       | `/scheduler`                     | Base URI for the REST API endpoints.                                                                                                 |
| `quartz-plus.web-admin.enabled`                    | `boolean`      | `true`                           | Enable or disable the web administration UI.                                                                                         |
| `quartz-plus.web-admin.uri`                        | `String`       | `/scheduler/admin`               | URI where the web administration UI is served.                                                                                       |
| `quartz-plus.job-execution-log.type`               | `enum`         | `DataSource`                     | Log storage strategy: `DataSource` (persisted to DB) or `InMemory` (volatile, bounded list).                                         |
| `quartz-plus.job-execution-log.in-memory-max-size` | `int`          | `1000`                           | Maximum number of log entries retained when using the `InMemory` storage type. Oldest entries are evicted when the limit is reached. |

### Standard Spring Quartz Properties (`spring.quartz.*`)

These are standard Spring Boot properties that quartz-plus respects:

| Property                                              | Type      | Default  | Description                                                                                          |
|-------------------------------------------------------|-----------|----------|------------------------------------------------------------------------------------------------------|
| `spring.quartz.job-store-type`                        | `enum`    | `memory` | `memory` for in-process scheduling; `jdbc` for database-backed persistent scheduling and clustering. |
| `spring.quartz.wait-for-jobs-to-complete-on-shutdown` | `boolean` | `false`  | Wait for currently executing jobs to finish before the scheduler shuts down.                         |
| `spring.quartz.properties.*`                          | `Map`     | -        | Pass-through to Quartz native properties (e.g., thread pool size, misfire threshold).                |

### Native Quartz Properties (`org.quartz.*`)

All standard Quartz properties are supported. Key examples:

| Property                                     | Default           | Description                                                                             |
|----------------------------------------------|-------------------|-----------------------------------------------------------------------------------------|
| `org.quartz.threadPool.threadCount`          | `10`              | Number of worker threads in the Quartz thread pool.                                     |
| `org.quartz.jobStore.misfireThreshold`       | `60000`           | Milliseconds a trigger can be late before it is considered misfired.                    |
| `org.quartz.jobStore.isClustered`            | `false`           | Enable clustered mode. Requires `jdbc` job store.                                       |
| `org.quartz.jobStore.clusterCheckinInterval` | `20000`           | Interval in ms at which the scheduler checks in to the cluster.                         |
| `org.quartz.scheduler.instanceId`            | `AUTO`            | Unique ID for this scheduler instance in a cluster. `AUTO` generates one automatically. |
| `org.quartz.scheduler.instanceName`          | `QuartzScheduler` | Name of the scheduler.                                                                  |

### Internal Job Properties

| Property                                                 | Default       | Description                                                  |
|----------------------------------------------------------|---------------|--------------------------------------------------------------|
| `job-execution-log-cleanup-job.enabled`                  | `true`        | Enable or disable the `ExecutionLogCleanupJob`.              |
| `job-execution-log-cleanup-job.cron-expression`          | `0 0 2 * * ?` | Cron schedule for the cleanup job (default: 2:00 AM daily).  |
| `job-execution-log-cleanup-job.time-zone`                | `UTC`         | Time zone for the cleanup job's cron schedule.               |
| `org.quartzplus.internal.ExecutionLogCleanupJob.daysAgo` | `30`          | Execution log records older than this many days are deleted. |

### Flyway Placeholder Properties

| Property                                             | Default | Description                                    |
|------------------------------------------------------|---------|------------------------------------------------|
| `spring.flyway.placeholders.quartzTablePrefix`       | `QRTZ_` | Table name prefix for Quartz schema tables.    |
| `spring.flyway.placeholders.executionLogTablePrefix` | `QRTZ_` | Table name prefix for the execution log table. |

---

## Internal Jobs

quartz-plus registers one internal job automatically:

### `ExecutionLogCleanupJob`

- **Group:** `INTERNAL`
- **Default schedule:** 2:00 AM UTC daily
- **Purpose:** Deletes execution log records older than a configurable number of days to prevent unbounded growth.
- **Dynamic parameter:** Accepts `{"daysAgo": 7}` in the `JobDataMap` (via the REST execute endpoint) to override the
  retention period for a single run.

To run the cleanup immediately for the last 7 days:

```bash
curl -X POST "http://localhost:8080/scheduler/trigger/execute?groupName=INTERNAL&triggerName=ExecutionLogCleanupJob-Trigger&jobName=ExecutionLogCleanupJob" \
     -H "Content-Type: application/json" \
     -d '{"daysAgo": 7}'
```

---

## Advanced: Clustered Scheduling

To run quartz-plus in a clustered environment:

```yaml
spring:
  quartz:
    job-store-type: jdbc

  datasource:
    url: jdbc:mysql://db-host:3306/scheduler_db
    username: scheduler_user
    password: secret

  flyway:
    locations: classpath:db/migration,classpath:org/quartzplus/mysql

org:
  quartz:
    scheduler:
      instanceName: MyClusteredScheduler
      instanceId: AUTO
    jobStore:
      isClustered: true
      clusterCheckinInterval: 20000
      misfireThreshold: 60000
    threadPool:
      threadCount: 5
```

All nodes in the cluster must share the same database and use the same `instanceName`. The `AUTO` instanceId generates a
unique ID per node.

---

## Examples

### Example 1: Simple Interval Job with Spring Injection

```java
import org.quartz.JobExecutionContext;
import org.quartzplus.Job;
import org.quartzplus.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

@JobSpec(
        jobName = "CacheWarmupJob",
        groupName = "Infrastructure",
        triggerName = "CacheWarmupJob-Trigger",
        trigger = @TriggerSpec(
                simpleTrigger = @SimpleTriggerSpec(
                        repeatCount = SimpleTrigger.REPEAT_INDEFINITELY,
                        repeatInterval = 300_000L // every 5 minutes
                )
        )
)
public class CacheWarmupJob extends Job {

    @Autowired
    private CacheService cacheService;

    @Override
    public void executeJob(JobExecutionContext ctx) throws Exception {
        cacheService.warmUp();
    }
}
```

### Example 2: Cron Job with Externalized Schedule and Retry

```java

@JobSpec(
        jobName = "NightlyReportJob",
        groupName = "Reporting",
        triggerName = "NightlyReportJob-Trigger",
        jobDescription = "Generates the nightly business report",
        triggerState = @TriggerState(enabledExp = "${reporting.nightly.enabled:true}"),
        trigger = @TriggerSpec(
                cronTrigger = @CronTriggerSpec(
                        cronExpressionExp = "${reporting.nightly.cron:0 0 1 * * ?}",
                        timeZoneExp = "${reporting.timezone:UTC}"
                )
        ),
        onError = @OnErrorSpec(
                onErrorRepeatCount = 3,
                onErrorRepeatDelay = 60_000 // retry after 1 minute
        )
)
public class NightlyReportJob extends Job {

    @Autowired
    private ReportService reportService;

    @Override
    public void executeJob(JobExecutionContext ctx) throws Exception {
        reportService.generateNightlyReport();
    }

    @Override
    public void onFailure(JobExecutionContext ctx, Throwable e) {
        notificationService.alertOpsTeam("NightlyReportJob failed: " + e.getMessage());
    }
}
```

**application.yml:**

```yaml
reporting:
  nightly:
    enabled: true
    cron: "0 0 1 * * ?"
  timezone: "America/Chicago"
```

### Example 3: Job That Starts Paused

```java

@JobSpec(
        jobName = "ManualOnlyJob",
        triggerName = "ManualOnlyJob-Trigger",
        triggerState = @TriggerState(startType = TriggerState.StartType.PAUSED),
        trigger = @TriggerSpec(
                simpleTrigger = @SimpleTriggerSpec(
                        repeatCount = SimpleTrigger.REPEAT_INDEFINITELY,
                        repeatInterval = 10_000L
                )
        )
)
public class ManualOnlyJob extends Job {
    @Override
    public void executeJob(JobExecutionContext ctx) { /* ... */ }
}
```

Resume it via REST when ready:

```bash
curl -X POST "http://localhost:8080/scheduler/trigger/resume?groupName=MainGroup&triggerName=ManualOnlyJob-Trigger"
```

### Example 4: Multiple Job Collections

```java

@Configuration
public class SchedulerConfiguration {

    @Bean
    public JobsCollection reportingJobs() {
        return () -> List.of(DailyReportJob.class, WeeklyReportJob.class);
    }

    @Bean
    public JobsCollection maintenanceJobs() {
        return () -> List.of(CacheWarmupJob.class, DbCleanupJob.class);
    }
}
```

### Example 5: Time-Constrained Job

```java

@JobSpec(
        jobName = "CampaignJob",
        triggerName = "CampaignJob-Trigger",
        trigger = @TriggerSpec(
                cronTrigger = @CronTriggerSpec(cronExpression = "0 0 9 * * MON-FRI")
        )
)
public class CampaignJob extends Job implements TimeConstrainable {

    @Override
    public Instant getStartTime() {
        return Instant.parse("2025-06-01T00:00:00Z");
    }

    @Override
    public Instant getEndTime() {
        return Instant.parse("2025-08-31T23:59:59Z");
    }

    @Override
    public void executeJob(JobExecutionContext ctx) { /* ... */ }
}
```

### Example 6: Full application.yml

```yaml
spring:
  quartz:
    job-store-type: jdbc
    wait-for-jobs-to-complete-on-shutdown: true
  datasource:
    url: jdbc:mysql://localhost:3306/scheduler
    username: app
    password: secret
  flyway:
    placeholders:
      quartzTablePrefix: QRTZ_
      executionLogTablePrefix: QRTZ_

quartz-plus:
  db-migration: true
  db-migration-location: classpath:org/quartzplus/mysql
  db-migration-table: flyway_quartz_schema_history
  api-uri: /scheduler
  web-admin:
    enabled: true
    uri: /scheduler/admin
  job-execution-log:
    type: DataSource
    in-memory-max-size: 1000
  calendars:
    - com.example.HolidayCalendar

org:
  quartz:
    scheduler:
      instanceName: MyScheduler
      instanceId: AUTO
    threadPool:
      threadCount: 10
    jobStore:
      isClustered: false
      misfireThreshold: 60000

job-execution-log-cleanup-job:
  enabled: true
  cron-expression: "0 0 2 * * ?"
  time-zone: UTC

org.quartzplus.internal.ExecutionLogCleanupJob.daysAgo: 30
```

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to
discuss what you would like to change.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

* the freedom to use the software for any purpose,
* the freedom to change the software to suit your needs,
* the freedom to share the software with your friends and neighbors
* the freedom to share the changes you make.

