package org.quartzplus;

import org.quartz.JobExecutionContext;
import org.quartzplus.annotation.JobSpec;
import org.quartzplus.annotation.OnErrorSpec;
import org.quartzplus.annotation.SimpleTriggerSpec;
import org.quartzplus.annotation.TriggerSpec;
import org.quartzplus.annotation.TriggerState;
import org.quartzplus.configuration.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

@JobSpec(jobName = "DemoJob",
        groupName = Constants.GROUP_NAME_DEMO,
        triggerName = "DemoJob-Trigger",
        jobDescription = "Demo job",
        triggerState = @TriggerState(enabledExp = "${demo-job.enabled:true}"),
        trigger = @TriggerSpec(
                simpleTrigger = @SimpleTriggerSpec(
                        repeatCountExp = "${demo-job.repeat-count:-1}",
                        repeatIntervalExp = "${demo-job.repeat-interval:7000}"
                )
        ),
        onError = @OnErrorSpec(
                onErrorRepeatCount = 2,
                onErrorRepeatDelay = 1000
        )
)
public class DemoJob extends Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoJob.class);

    @Override
    public void executeJob(final JobExecutionContext jobExecutionContext) {
        LOGGER.info("Demo job execution started, there is exactly 50% chance of having an exception thrown now...");
        if (new Random().nextBoolean()) {
            throw new IllegalArgumentException("There is some issue...");
        }
        LOGGER.info("Well, i guess it executed without errors, phew");
    }
}
