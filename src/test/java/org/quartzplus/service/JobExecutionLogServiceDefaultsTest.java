package org.quartzplus.service;

import org.junit.jupiter.api.Test;
import org.quartzplus.domain.JobExecutionLog;
import org.quartzplus.domain.QuartzExecutionNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobExecutionLogServiceDefaultsTest {

    private final JobExecutionLogService service = new MinimalJobExecutionLogService();

    @Test
    void testFilteredListWithoutResultOrTimeFilterUsesTheBasicList() {
        final var page = service.getJobExecutionLogList(10, 0, "group", null, null, null, null, null, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void testFilteredListWithResultOrTimeFilterIsUnsupported() {
        assertThatThrownBy(() -> service.getJobExecutionLogList(10, 0, null, null, null, null, false, null, null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.getJobExecutionLogList(10, 0, null, null, null, null, null, Instant.now(), null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testStatsAreUnsupportedByDefault() {
        assertThatThrownBy(() -> service.getJobExecutionStats(Instant.now().minusSeconds(60), Instant.now(), Duration.ofSeconds(10), null, null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not provide execution statistics");
    }

    private static final class MinimalJobExecutionLogService implements JobExecutionLogService {

        @Override
        public Page<JobExecutionLog> getJobExecutionLogList(final Integer pageSize, final Integer currentPage,
                                                            final String groupName, final String jobName,
                                                            final String triggerName, final String instanceClass) {
            return new PageImpl<>(List.of());
        }

        @Override
        public Optional<JobExecutionLog> getJobExecutionLog(final String jobExecutionLogId) {
            return Optional.empty();
        }

        @Override
        public void insertJobExecutionLog(final JobExecutionLog jobExecutionLog) {
        }

        @Override
        public int clearJobExecutionLog(final Instant until) {
            return 0;
        }

        @Override
        public List<QuartzExecutionNode> getQuartzExecutionNodeList() {
            return List.of();
        }

        @Override
        public String getTablePrefix() {
            return "";
        }
    }
}
