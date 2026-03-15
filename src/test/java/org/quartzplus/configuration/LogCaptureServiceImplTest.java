package org.quartzplus.configuration;

import ch.qos.logback.classic.Level;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogCaptureServiceImplTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogCaptureServiceImplTest.class);

    @Test
    void testEnableLogCapture() {
        try (var logCaptureService = new LogCaptureServiceImpl(Level.DEBUG, LogCaptureServiceImplTest.class)) {
            final var testValue = RandomStringUtils.secure().nextAlphanumeric(20);
            LOGGER.debug(testValue);
            assertThat(logCaptureService.getList()).hasSize(1);
            logCaptureService.getList().forEach(e -> assertThat(e.getFormattedMessage()).isEqualTo(testValue));
        }
    }
}