package org.quartzplus.configuration;

import org.junit.jupiter.api.Test;
import org.quartz.SchedulerException;
import org.quartzplus.Application;
import org.quartzplus.domain.SchedulerInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@AutoConfigureDataJpa
@TestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuartzPlusManagementEndpointTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testSchedulerMetaDataReadOperation() throws SchedulerException {
        assertThat(applicationContext.getBeansOfType(QuartzPlusManagementEndpoint.class)).isNotEmpty();
        final var quartzManagementEndpoint = applicationContext.getBean(QuartzPlusManagementEndpoint.class);
        final var metadata = quartzManagementEndpoint.schedulerMetaDataReadOperation();
        assertThat(metadata.getBody()).isNotNull().isInstanceOf(SchedulerInfo.class);
    }
}