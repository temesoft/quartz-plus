package org.quartzplus.service;

import org.quartzplus.Job;
import org.quartzplus.annotation.JobSpec;

import java.util.List;

/**
 * A marker interface used to aggregate and expose Quartz job classes for automatic registration.
 * <p>This interface acts as a provider for the {@code QuartzExecutorService}. At startup, the service
 * scans the Spring application context for all beans implementing {@code JobsCollection} and
 * registers every job class returned by {@link #getJobClassList()}.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * &#64;Configuration
 * public class MyJobConfiguration {
 *     &#64;Bean
 *     public JobsCollection myJobs() {
 *         return () -> List.of(MyCustomJob.class, AnotherJob.class);
 *     }
 * }
 * </pre>
 *
 * @see JobSpec
 * @see org.quartzplus.Job
 */
public interface JobsCollection {

    /**
     * Provides a list of job classes to be registered with the Quartz scheduler.
     * <b>Constraints:</b>
     * <ul>
     *   <li>Every class in the list <strong>must</strong> be annotated with {@link JobSpec}.</li>
     *   <li>Every class <strong>must</strong> implement the Quartz {@link org.quartzplus.Job} interface.</li>
     * </ul>
     *
     * @return a non-null {@link List} of fully qualified job classes ready for scheduling.
     */
    List<Class<? extends Job>> getJobClassList();
}
