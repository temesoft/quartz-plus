package org.quartzplus.configuration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Configuration class responsible for managing database schema migrations for QuartzPlus using Flyway.
 * <p>
 * This configuration automatically detects the database type (H2 or MySQL) from the {@link DataSource}
 * JDBC URL and applies the corresponding migration scripts. It supports custom table prefixes for both
 * standard Quartz tables and the execution log tables.
 * <p>
 * Migrations can be toggled via the {@code quartzplus.db-migration} property. By default, migrations
 * are enabled.
 */
@Configuration
@ConditionalOnProperty(name = QuartzPlusProperties.PREFIX + ".db-migration", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QuartzPlusProperties.class)
public class QuartzPlusFlywayConfiguration {

    private final QuartzPlusProperties quartzPlusProperties;

    public QuartzPlusFlywayConfiguration(final QuartzPlusProperties quartzPlusProperties) {
        this.quartzPlusProperties = quartzPlusProperties;
    }

    /**
     * Configures and executes Flyway migrations to set up the Quartz and QuartzPlus database schemas.
     * <p>
     * This bean initializes a {@link Flyway} instance using {@link ClassicConfiguration} and
     * immediately triggers {@link Flyway#migrate()}. It utilizes properties defined in
     * {@code QuartzPlusProperties} for script locations and the migration history table name.
     *
     * @param dataSource              The {@link DataSource} where the Quartz tables will be created.
     * @param quartzTablePrefix       The prefix for standard Quartz engine tables (e.g., QRTZ_JOB_DETAILS).
     *                                Defaults to {@code QRTZ_} if the placeholder is not provided.
     * @param executionLogTablePrefix The prefix for QuartzPlus-specific execution log tables.
     *                                Defaults to {@code QRTZ_} if the placeholder is not provided.
     * @return A fully initialized {@link Flyway} instance after successful migration.
     * @see QuartzPlusProperties#getDbMigrationLocation()
     * @see QuartzPlusProperties#getDbMigrationTable()
     */
    @Bean
    Flyway quartzFlyway(final DataSource dataSource,
                        @Value("${spring.flyway.placeholders.quartzTablePrefix:QRTZ_}") final String quartzTablePrefix,
                        @Value("${spring.flyway.placeholders.executionLogTablePrefix:QRTZ_}") final String executionLogTablePrefix) {
        final var config = new ClassicConfiguration();
        config.setLocationsAsStrings(quartzPlusProperties.getDbMigrationLocation());
        config.setDataSource(dataSource);
        config.setOutOfOrder(true);
        config.setBaselineOnMigrate(true);
        config.setTable(quartzPlusProperties.getDbMigrationTable());
        config.getPlaceholders().put("quartzTablePrefix", quartzTablePrefix);
        config.getPlaceholders().put("executionLogTablePrefix", executionLogTablePrefix);
        final var flywayJobs = new Flyway(config);
        flywayJobs.migrate();
        return flywayJobs;
    }
}
