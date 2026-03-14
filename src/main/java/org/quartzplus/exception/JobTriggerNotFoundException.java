package org.quartzplus.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested Job or Trigger cannot be located within the Quartz scheduler.
 * <p>
 * This exception is primarily used by {@link org.quartzplus.resource.SchedulerRestController}
 * to indicate that a specific resource (identified by name or group) does not exist.
 * It results in an <b>HTTP 404 Not Found</b> response status.
 *
 * @see org.quartzplus.resource.SchedulerRestController
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Job/trigger not found")
public class JobTriggerNotFoundException extends Exception {

    public JobTriggerNotFoundException() {
        super();
    }

}
