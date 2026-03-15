package org.quartzplus.configuration;

import org.junit.jupiter.api.Test;
import org.quartzplus.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@AutoConfigureDataJpa
@TestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuartzPlusCommonConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void testGetConfigProperties() {
        var props = QuartzPlusCommonConfiguration.getConfigProperties(environment);
        assertThat(props).containsKeys(
                "spring.quartz.job-store-type",
                "spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval",
                "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass",
                "spring.quartz.properties.org.quartz.jobStore.isClustered",
                "spring.quartz.properties.org.quartz.jobStore.misfireThreshold",
                "spring.quartz.properties.org.quartz.jobStore.useProperties",
                "spring.quartz.properties.org.quartz.scheduler.instanceId",
                "spring.quartz.properties.org.quartz.scheduler.instanceName"
        );
    }
}