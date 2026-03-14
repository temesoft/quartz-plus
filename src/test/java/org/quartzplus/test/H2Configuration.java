package org.quartzplus.test;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Creates a temporary H2 database inside temp directory and then removes it upon spring context destroy
 */
@Configuration
public class H2Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2Configuration.class);

    @SuppressWarnings("deprecation")
    private static final String TEMP_FOLDER = Files.createTempDir().getAbsolutePath();
    public static final String URL = "jdbc:h2:file:" + TEMP_FOLDER + "/h2";
    public static final String USERNAME = "SA";
    public static final String PASSWORD = "password";

    @PreDestroy
    public void destroy() {
        LOGGER.info("Removing temp H2 location: {}", TEMP_FOLDER);
        try {
            FileSystemUtils.deleteRecursively(Paths.get(TEMP_FOLDER));
        } catch (IOException e) {
            LOGGER.error("Unable to remove {}", TEMP_FOLDER, e);
        }
    }

    /**
     * Returns default H2 quartz configuration properties
     */
    @VisibleForTesting
    public static Properties createQuartzConfigProperties() {
        final var props = new Properties();
        LOGGER.info("Using H2 db: {}", H2Configuration.URL);
        props.setProperty("spring.datasource.url", H2Configuration.URL);
        props.setProperty("spring.datasource.driverClassName", "org.h2.Driver");
        props.setProperty("spring.datasource.username", USERNAME);
        props.setProperty("spring.datasource.password", PASSWORD);
        props.setProperty("spring.datasource.name", "JobsDataSource");
        props.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
        props.setProperty("spring.h2.console.enabled", "true");
        props.setProperty("spring.flyway.placeholders.quartzTablePrefix", "QRTZ_");
        props.setProperty("spring.flyway.placeholders.executionLogTablePrefix", "QRTZ_");
        return props;
    }
}
