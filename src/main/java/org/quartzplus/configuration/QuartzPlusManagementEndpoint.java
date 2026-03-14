package org.quartzplus.configuration;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartzplus.Job;
import org.quartzplus.domain.SchedulerInfo;
import org.quartzplus.service.JobExecutionLogService;
import org.quartzplus.service.JobsCollection;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.OK;

/**
 * A Spring Boot Actuator {@link Endpoint} that exposes the operational status and metadata of QuartzPlus.
 * <p>
 * This endpoint provides a comprehensive view of the scheduler's state, including
 * metadata from the Quartz {@link Scheduler}, the list of registered {@link Job} classes,
 * active log service configuration, and system URIs.
 * <p>
 * Access this endpoint via the {@code /actuator/quartz-plus} path (depending on your
 * Actuator configuration).
 *
 * @see SchedulerInfo
 * @see JobExecutionLogService
 */
@Endpoint(id = "quartz-plus")
public class QuartzPlusManagementEndpoint {

    private final Scheduler scheduler;
    private final List<JobsCollection> jobsCollections;
    private final JobExecutionLogService jobExecutionLogService;
    private final String quartzPlusVersion;
    private final String webUri;
    private final String apiUri;

    /**
     * Constructs the management endpoint with necessary infrastructure dependencies.
     *
     * @param scheduler              the Quartz scheduler to monitor.
     * @param jobsCollections        a list of collections containing the registered job classes.
     * @param jobExecutionLogService the service used for job execution logging.
     * @param quartzPlusVersion      the current version string of the QuartzPlus library.
     * @param webUri                 the base URI for the QuartzPlus Web UI.
     * @param apiUri                 the base URI for the QuartzPlus REST API.
     */
    public QuartzPlusManagementEndpoint(final Scheduler scheduler,
                                        final List<JobsCollection> jobsCollections,
                                        final JobExecutionLogService jobExecutionLogService,
                                        final String quartzPlusVersion,
                                        final String webUri,
                                        final String apiUri) {
        this.scheduler = scheduler;
        this.jobsCollections = jobsCollections;
        this.jobExecutionLogService = jobExecutionLogService;
        this.quartzPlusVersion = quartzPlusVersion;
        this.webUri = webUri;
        this.apiUri = apiUri;
    }

    /**
     * Performs a read operation to retrieve the current scheduler metadata and configuration.
     * <p>
     * This method aggregates all job classes from the provided {@link JobsCollection}s
     * and packages them into a {@link SchedulerInfo} DTO along with system status information.
     *
     * @return a {@link ResponseEntity} containing {@link SchedulerInfo} and an {@code OK} status.
     * @throws SchedulerException if there is an error retrieving metadata from the Quartz scheduler.
     */
    @ReadOperation
    public ResponseEntity<SchedulerInfo> schedulerMetaDataReadOperation() throws SchedulerException {
        final var jobClassList = new ArrayList<Class<? extends Job>>();
        jobsCollections.forEach(jobsCollection -> jobClassList.addAll(jobsCollection.getJobClassList()));
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
}
