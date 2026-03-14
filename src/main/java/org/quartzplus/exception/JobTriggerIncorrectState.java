package org.quartzplus.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an operation is requested on a Job or Trigger that is in an
 * invalid state for that action (e.g., attempting to pause an already paused trigger).
 * <p>
 * When thrown within the {@link org.quartzplus.resource.SchedulerRestController},
 * it results in an <b>HTTP 400 Bad Request</b> response to the client.
 *
 * @see org.quartzplus.resource.SchedulerRestController
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Job/trigger state is incorrect")
public class JobTriggerIncorrectState extends Exception {

    public JobTriggerIncorrectState() {
        super();
    }

}
