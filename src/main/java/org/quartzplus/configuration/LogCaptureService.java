package org.quartzplus.configuration;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;

/**
 * Service interface to captures Logback logging events for a specific class.
 * <p>
 * This service attaches a custom appender to a logger, allowing programmatic access to log messages
 * generated during its lifecycle.
 */
public interface LogCaptureService {

    /**
     * Returns a list of all logging events captured since initialization or the last clear.
     *
     * @return A {@link List} of {@link ILoggingEvent} objects.
     */
    List<ILoggingEvent> getList();

}
