package org.quartzplus.configuration;

import org.junit.jupiter.api.Test;
import org.quartzplus.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import java.util.AbstractMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = Application.class
)
@AutoConfigureDataJpa
@TestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QuartzPlusSchedulerHealthIndicatorTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testHealthIndicator() {
        final var healthIndicator = applicationContext.getBean(QuartzPlusSchedulerHealthIndicator.class);
        final var attributes = healthIndicator.health(true);
        assertThat(attributes).isNotNull();
        assertThat(attributes.getDetails())
                .isNotNull()
                .containsKeys("version")
                .contains(new AbstractMap.SimpleEntry<>("clustered", true))
                .contains(new AbstractMap.SimpleEntry<>("shutdown", false))
                .contains(new AbstractMap.SimpleEntry<>("standby", false));

    }

}