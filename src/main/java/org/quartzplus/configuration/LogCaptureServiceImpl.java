package org.quartzplus.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of {@link LogCaptureService} that captures Logback logging events for a specific class.
 * <p>
 * This service attaches a custom appender to a logger, allowing programmatic access to log messages
 * generated during its lifecycle. It implements {@link AutoCloseable} to ensure that the original
 * logging configuration is restored and resources are cleaned up.
 * </p>
 *
 * @see LogCaptureService
 * @see AutoCloseable
 */
public class LogCaptureServiceImpl implements LogCaptureService, AutoCloseable {

    private static final Level DEFAULT_LEVEL = Level.DEBUG;
    private final List<ILoggingEvent> LOG_QUEUE = new ArrayList<>();
    private final PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
    private final AtomicReference<Level> originalLevel = new AtomicReference<>();
    private final AtomicReference<Logger> loggerRef = new AtomicReference<>();

    /**
     * Custom Logback appender that redirects logging events into the local queue.
     */
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(final ILoggingEvent eventObject) {
            LOG_QUEUE.add(eventObject);
        }
    };

    /**
     * Constructs a new service instance, attaches the appender to the specified class logger,
     * and sets the desired logging level.
     *
     * @param providedLevel The {@link Level} to capture; defaults to {@code DEBUG} if null.
     * @param clazz         The class whose logs should be intercepted.
     */
    public LogCaptureServiceImpl(final Level providedLevel, final Class<?> clazz) {
        LOG_QUEUE.clear();

        patternLayoutEncoder.setPattern("%date %level [%thread] %logger{10} [%file:%line] %msg%n");
        final var loggerFactory = (LoggerContext) LoggerFactory.getILoggerFactory();
        patternLayoutEncoder.setContext(loggerFactory);
        patternLayoutEncoder.start();

        appender.setContext(loggerFactory);
        appender.start();
        final var logger = (Logger) LoggerFactory.getLogger(clazz);
        originalLevel.set(logger.getLevel());
        logger.addAppender(appender);
        logger.setLevel(providedLevel == null ? DEFAULT_LEVEL : providedLevel);
        logger.setAdditive(true);
        loggerRef.set(logger);
    }

    /**
     * Returns an immutable snapshot of all logging events captured since initialization or the last clear.
     *
     * @return A {@link List} of {@link ILoggingEvent} objects.
     */
    @Override
    public List<ILoggingEvent> getList() {
        return List.copyOf(LOG_QUEUE);
    }

    /**
     * Closes the service and Detaches the appender, restores the logger's original level,
     * stops the encoder, and clears the captured log queue.
     */
    @Override
    public void close() {
        LOG_QUEUE.clear();
        loggerRef.get().setLevel(originalLevel.get());
        loggerRef.get().detachAppender(appender);
        patternLayoutEncoder.stop();
    }
}
