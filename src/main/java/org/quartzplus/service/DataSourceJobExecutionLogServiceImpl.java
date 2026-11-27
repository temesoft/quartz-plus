package org.quartzplus.service;

import jakarta.annotation.Nullable;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.JobExecutionStats;
import org.quartzplus.domain.QuartzExecutionNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Database-backed implementation of the {@link JobExecutionLogService} using Spring's {@link JdbcTemplate}.
 * <p>
 * This service handles the persistence and retrieval of job execution history and scheduler node
 * status using a relational database. It supports dynamic table prefixes to integrate with existing
 * Quartz schema naming conventions.
 *
 * @see JobExecutionLogService
 * @see QuartzExecutionNode
 */
public class DataSourceJobExecutionLogServiceImpl implements JobExecutionLogService {

    private final JdbcTemplate jdbcTemplate;
    private final String tablePrefix;

    private static final String SQL_EXEC_LOG_SELECT_PAGED_1 = "JOB_EXECUTION_LOG ";
    private static final String SQL_EXEC_LOG_SELECT_PAGED_2 = " ORDER BY createTime DESC limit ? offset ?";
    private static final String SQL_EXEC_LOG_SELECT_ENTITY = "JOB_EXECUTION_LOG WHERE id = ?";
    private static final String SQL_EXEC_LOG_INSERT = "JOB_EXECUTION_LOG(id,groupName,triggerName,jobName,priority,success,errorMessage,fireInstanceId,instanceClass,duration,stackTrace,jsonData,createTime) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ";
    private static final String SQL_EXEC_NODES_SELECT = "SCHEDULER_STATE ORDER BY INSTANCE_NAME ASC";
    private static final String SQL_EXEC_LOG_DELETE = "JOB_EXECUTION_LOG WHERE createTime < ?";

    private static final String SQL_EXEC_LOG_STATS_SELECT = " AS bucket, COUNT(id) AS runs, "
            + "SUM(CASE WHEN success THEN 1 ELSE 0 END) AS successes, SUM(duration) AS duration_sum, "
            + "MAX(duration) AS duration_max FROM ";
    private static final String SQL_EXEC_LOG_STATS_WHERE = "JOB_EXECUTION_LOG WHERE createTime >= ? AND createTime < ?";

    private static final String SQL_SELECT_PREFIX = "SELECT * FROM ";
    private static final String SQL_SELECT_COUNT_PREFIX = "SELECT count(id) FROM ";
    private static final String SQL_INSERT_PREFIX = "INSERT INTO ";
    private static final String SQL_DELETE_PREFIX = "DELETE FROM ";


    /**
     * Constructs a new service instance with a specified template and table prefix.
     *
     * @param jdbcTemplate the JDBC template used for database operations.
     * @param tablePrefix  the prefix for Quartz and QuartzPlus tables (e.g., "QRTZ_").
     */
    public DataSourceJobExecutionLogServiceImpl(final JdbcTemplate jdbcTemplate, final String tablePrefix) {
        this.jdbcTemplate = jdbcTemplate;
        this.tablePrefix = tablePrefix;
    }

    /**
     * Retrieves a paged list of job execution logs filtered by optional criteria.
     * <p>
     * Results are returned in descending order of creation time.
     *
     * @param pageSize      number of records per page.
     * @param currentPage   the zero-based page index.
     * @param groupName     (Optional) filter by job group name.
     * @param jobName       (Optional) filter by job name.
     * @param triggerName   (Optional) filter by trigger name.
     * @param instanceClass (Optional) filter by the job's implementation class name.
     * @return a {@link Page} containing the requested logs and total count.
     */
    @Override
    public Page<JobExecutionLog> getJobExecutionLogList(final Integer pageSize,
                                                        final Integer currentPage,
                                                        @Nullable final String groupName,
                                                        @Nullable final String jobName,
                                                        @Nullable final String triggerName,
                                                        @Nullable final String instanceClass) {
        return getJobExecutionLogList(pageSize, currentPage, groupName, jobName, triggerName, instanceClass, null, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Page<JobExecutionLog> getJobExecutionLogList(final Integer pageSize,
                                                        final Integer currentPage,
                                                        @Nullable final String groupName,
                                                        @Nullable final String jobName,
                                                        @Nullable final String triggerName,
                                                        @Nullable final String instanceClass,
                                                        @Nullable final Boolean success,
                                                        @Nullable final Instant from,
                                                        @Nullable final Instant to) {
        final var conditions = new Conditions()
                .add("groupName = ?", groupName, Types.VARCHAR)
                .add("jobName = ?", jobName, Types.VARCHAR)
                .add("triggerName = ?", triggerName, Types.VARCHAR)
                .add("instanceClass = ?", instanceClass, Types.VARCHAR)
                .add("success = ?", success, Types.BOOLEAN)
                .add("createTime >= ?", from == null ? null : Timestamp.from(from), Types.TIMESTAMP)
                .add("createTime < ?", to == null ? null : Timestamp.from(to), Types.TIMESTAMP);
        final var where = conditions.clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions.clauses);
        final var countResponse = jdbcTemplate.query(
                SQL_SELECT_COUNT_PREFIX + tablePrefix + SQL_EXEC_LOG_SELECT_PAGED_1 + where,
                conditions.arguments.toArray(new Object[0]),
                conditions.types.stream().mapToInt(Integer::intValue).toArray(),
                new SingleColumnRowMapper<>(Integer.class)
        ).get(0);
        conditions.arguments.add(pageSize);
        conditions.arguments.add(currentPage * pageSize);
        conditions.types.add(Types.NUMERIC);
        conditions.types.add(Types.NUMERIC);
        final var response = jdbcTemplate.query(
                SQL_SELECT_PREFIX + tablePrefix + SQL_EXEC_LOG_SELECT_PAGED_1 + where + SQL_EXEC_LOG_SELECT_PAGED_2,
                conditions.arguments.toArray(new Object[0]),
                conditions.types.stream().mapToInt(Integer::intValue).toArray(),
                new JobExecutionLogRowMapper()
        );
        return new PageImpl<>(response, PageRequest.of(currentPage, pageSize), countResponse);
    }

    /**
     * {@inheritDoc}
     * <p>
     * One query adds the runs of every bucket up in the database: each bucket is a range of the indexed
     * {@code createTime} column, so only the runs in {@code [from, to)} are read.</p>
     */
    @Override
    public List<JobExecutionStats> getJobExecutionStats(final Instant from,
                                                        final Instant to,
                                                        final Duration bucketSize,
                                                        @Nullable final String groupName,
                                                        @Nullable final String jobName) {
        final var accumulator = new ExecutionStatsAccumulator(from, to, bucketSize);
        final var filters = new Conditions()
                .add("groupName = ?", groupName, Types.VARCHAR)
                .add("jobName = ?", jobName, Types.VARCHAR);
        final var sql = new StringBuilder();
        final var arguments = new ArrayList<>();
        final var types = new ArrayList<Integer>();
        for (var bucket = 0; bucket < accumulator.bucketCount(); bucket++) {
            if (bucket > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT ").append(bucket).append(SQL_EXEC_LOG_STATS_SELECT).append(tablePrefix)
                    .append(SQL_EXEC_LOG_STATS_WHERE);
            filters.clauses.forEach(clause -> sql.append(" AND ").append(clause));
            arguments.add(Timestamp.from(accumulator.bucketStart(bucket)));
            arguments.add(Timestamp.from(accumulator.bucketEnd(bucket)));
            types.add(Types.TIMESTAMP);
            types.add(Types.TIMESTAMP);
            arguments.addAll(filters.arguments);
            types.addAll(filters.types);
        }
        jdbcTemplate.query(
                sql.toString(),
                arguments.toArray(new Object[0]),
                types.stream().mapToInt(Integer::intValue).toArray(),
                (RowCallbackHandler) rs -> {
                    final var runs = rs.getLong("runs");
                    if (runs > 0) {
                        final var successes = rs.getLong("successes");
                        accumulator.addBucket(
                                rs.getInt("bucket"),
                                successes,
                                runs - successes,
                                rs.getLong("duration_sum"),
                                rs.getLong("duration_max")
                        );
                    }
                }
        );
        return accumulator.toStats();
    }

    /**
     * Fetches a specific job execution log by its unique identifier.
     *
     * @param jobExecutionLogId the UUID or unique ID of the log entry.
     * @return an {@link Optional} containing the log if found, or empty otherwise.
     */
    @Override
    public Optional<JobExecutionLog> getJobExecutionLog(final String jobExecutionLogId) {
        Optional<JobExecutionLog> optionalResult;
        try {
            optionalResult = Optional.ofNullable(jdbcTemplate.queryForObject(
                    SQL_SELECT_PREFIX + tablePrefix + SQL_EXEC_LOG_SELECT_ENTITY,
                    new Object[]{jobExecutionLogId},
                    new int[]{Types.VARCHAR},
                    new JobExecutionLogRowMapper()
            ));
        } catch (EmptyResultDataAccessException e) {
            optionalResult = Optional.empty();
        }
        return optionalResult;
    }

    /**
     * Persists a new job execution log entry into the database.
     *
     * @param jobExecutionLog the log data to insert.
     */
    @Override
    public void insertJobExecutionLog(final JobExecutionLog jobExecutionLog) {
        jdbcTemplate.update(
                connection -> {
                    final PreparedStatement ps = connection.prepareStatement(SQL_INSERT_PREFIX + tablePrefix + SQL_EXEC_LOG_INSERT);
                    ps.setString(1, jobExecutionLog.getId());
                    ps.setString(2, jobExecutionLog.getGroupName());
                    ps.setString(3, jobExecutionLog.getTriggerName());
                    ps.setString(4, jobExecutionLog.getJobName());
                    ps.setInt(5, jobExecutionLog.getPriority());
                    ps.setBoolean(6, jobExecutionLog.getSuccess());
                    ps.setString(7, jobExecutionLog.getErrorMessage());
                    ps.setString(8, jobExecutionLog.getFireInstanceId());
                    ps.setString(9, jobExecutionLog.getInstanceClass());
                    ps.setLong(10, jobExecutionLog.getDuration());
                    ps.setString(11, jobExecutionLog.getStackTrace());
                    ps.setString(12, jobExecutionLog.getJsonData());
                    ps.setTimestamp(13, Timestamp.from(jobExecutionLog.getCreateTime()));
                    return ps;
                }
        );
    }

    /**
     * Deletes log entries created before the specified timestamp.
     *
     * @param until the cutoff timestamp for deletion.
     * @return the number of records deleted.
     */
    @Override
    public int clearJobExecutionLog(final Instant until) {
        return jdbcTemplate.update(
                SQL_DELETE_PREFIX + tablePrefix + SQL_EXEC_LOG_DELETE,
                until);
    }

    /**
     * Retrieves the status of all registered Quartz scheduler nodes in the cluster.
     * Reads from the {@code SCHEDULER_STATE} table.
     *
     * @return a list of {@link QuartzExecutionNode} objects.
     */
    @Override
    public List<QuartzExecutionNode> getQuartzExecutionNodeList() {
        return jdbcTemplate.query(
                SQL_SELECT_PREFIX + tablePrefix + SQL_EXEC_NODES_SELECT,
                new Object[]{},
                new int[]{},
                rowMapperExecNode());
    }

    /**
     * Custom result set row mapper for JobExecutionLog
     */
    static class JobExecutionLogRowMapper implements RowMapper<JobExecutionLog> {
        @Override
        public JobExecutionLog mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return new JobExecutionLog(
                    rs.getString("id"),
                    rs.getString("groupName"),
                    rs.getString("triggerName"),
                    rs.getString("jobName"),
                    rs.getInt("priority"),
                    rs.getBoolean("success"),
                    rs.getString("errorMessage"),
                    rs.getString("fireInstanceId"),
                    rs.getString("instanceClass"),
                    rs.getLong("duration"),
                    rs.getString("stackTrace"),
                    rs.getString("jsonData"),
                    rs.getTimestamp("createTime").toInstant());
        }
    }

    /**
     * Returns the database table prefix used by this service.
     *
     * @return the table prefix string.
     */
    @Override
    public String getTablePrefix() {
        return tablePrefix;
    }

    /**
     * SQL conditions with their arguments; a condition whose value is {@code null} is left out.
     */
    private static final class Conditions {
        private final List<String> clauses = new ArrayList<>();
        private final List<Object> arguments = new ArrayList<>();
        private final List<Integer> types = new ArrayList<>();

        private Conditions add(final String clause, @Nullable final Object value, final int type) {
            if (value != null) {
                clauses.add(clause);
                arguments.add(value);
                types.add(type);
            }
            return this;
        }
    }

    /**
     * Internal {@link RowMapper} to map database rows to {@link JobExecutionLog} objects.
     */
    private RowMapper<QuartzExecutionNode> rowMapperExecNode() {
        return (rs, i) -> new QuartzExecutionNode(
                rs.getString("SCHED_NAME"),
                rs.getString("INSTANCE_NAME"),
                Instant.ofEpochMilli(rs.getLong("LAST_CHECKIN_TIME")),
                rs.getLong("CHECKIN_INTERVAL"));
    }
}
