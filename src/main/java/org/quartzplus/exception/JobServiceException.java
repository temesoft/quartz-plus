package org.quartzplus.exception;

/**
 * Exception thrown by the job service when an error occurs during job management or execution.
 * <p>
 * This class acts as a high-level wrapper for various underlying issues encountered
 * while interacting with the Quartz scheduler or processing job-related logic.
 */
public class JobServiceException extends Exception {

    public JobServiceException(final String message) {
        super(message);
    }
}
