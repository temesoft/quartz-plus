package org.quartzplus.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for QuartzPlus.
 * <p>
 * This class maps properties with the {@code quartz-plus} prefix (e.g., from {@code application.yml}
 * or {@code application.properties}) to strongly-typed Java objects. It controls database
 * migrations, logging strategies, and web administration settings.
 */
@ConfigurationProperties(prefix = QuartzPlusProperties.PREFIX)
public class QuartzPlusProperties {

    /** The prefix used for all QuartzPlus related configuration properties. */
    public static final String PREFIX = "quartz-plus";

    private boolean dbMigration = true;
    private String dbMigrationLocation = "classpath:org/quartzplus/mysql";
    private String dbMigrationTable = "flyway_quartz_schema_history";
    private List<String> calendars;
    private JobExecutionLogProperties jobExecutionLog = new JobExecutionLogProperties();
    private String apiUri = "/scheduler";
    private WebAdmin webAdmin = new WebAdmin();

    /**
     * Settings for the QuartzPlus Web Administration interface.
     */
    public static class WebAdmin {
        private boolean enabled = true;
        private String uri = "/scheduler/admin";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(final String uri) {
            this.uri = uri;
        }
    }

    /**
     * Settings for job execution logging behavior.
     */
    public static class JobExecutionLogProperties {
        private JobExecutionLogType type = JobExecutionLogType.DataSource;
        private int inMemoryMaxSize = 1000;

        public JobExecutionLogType getType() {
            return type;
        }

        public void setType(final JobExecutionLogType type) {
            this.type = type;
        }

        public int getInMemoryMaxSize() {
            return inMemoryMaxSize;
        }

        public void setInMemoryMaxSize(final int inMemoryMaxSize) {
            this.inMemoryMaxSize = inMemoryMaxSize;
        }
    }

    /**
     * Enumeration of supported log storage types.
     */
    public enum JobExecutionLogType {
        /** Logs are stored in a volatile internal list. */
        InMemory,
        /** Logs are persisted to a database via JDBC. */
        DataSource
    }

    public boolean isDbMigration() {
        return dbMigration;
    }

    public void setDbMigration(final boolean dbMigration) {
        this.dbMigration = dbMigration;
    }

    public String getDbMigrationLocation() {
        return dbMigrationLocation;
    }

    public void setDbMigrationLocation(final String dbMigrationLocation) {
        this.dbMigrationLocation = dbMigrationLocation;
    }

    public String getDbMigrationTable() {
        return dbMigrationTable;
    }

    public void setDbMigrationTable(final String dbMigrationTable) {
        this.dbMigrationTable = dbMigrationTable;
    }

    public List<String> getCalendars() {
        return calendars;
    }

    public void setCalendars(final List<String> calendars) {
        this.calendars = calendars;
    }

    public JobExecutionLogProperties getJobExecutionLog() {
        return jobExecutionLog;
    }

    public void setJobExecutionLog(final JobExecutionLogProperties jobExecutionLog) {
        this.jobExecutionLog = jobExecutionLog;
    }

    public String getApiUri() {
        return apiUri;
    }

    public void setApiUri(final String apiUri) {
        this.apiUri = apiUri;
    }

    public WebAdmin getWebAdmin() {
        return webAdmin;
    }

    public void setWebAdmin(final WebAdmin webAdmin) {
        this.webAdmin = webAdmin;
    }
}
