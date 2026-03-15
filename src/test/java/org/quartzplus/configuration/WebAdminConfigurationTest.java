package org.quartzplus.configuration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartzplus.DemoJob;
import org.quartzplus.internal.ExecutionLogCleanupJob;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.test.EmbeddedPortRetriever;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebAdminConfigurationTest {

    private static final String WEB_ADMIN_URI = "/test-scheduler/admin"; // set in test application.properties

    private static ConfigurableApplicationContext appContext;
    private static int port = -1;

    @Test
    void testAdminPortal() {
        final var restTemplate = new RestTemplate();
        var result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/index.html", String.class);
        assertThat(result).isNotNull().hasSizeGreaterThan(6000);
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/", String.class);
        assertThat(result).isNotNull().hasSizeGreaterThan(6000);
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI, String.class);
        assertThat(result).isNotNull().hasSizeGreaterThan(6000);
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/app.js", String.class);
        assertThat(result).isNotNull().hasSizeGreaterThan(3000);
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/logo.png", String.class);
        assertThat(result).isNotNull();
    }

    @BeforeAll
    static void setUpOnce() throws Exception {
        final var app = new SpringApplication(
                TestConfig.class,
                TestCoreConfigImport.class,
                QuartzPlusCommonConfiguration.class,
                QuartzAutoConfiguration.class
        );
        final var embeddedPortRetriever = new EmbeddedPortRetriever(app);
        app.setBannerMode(Banner.Mode.OFF);
        appContext = app.run();
        port = embeddedPortRetriever.getRetrievedPort();
    }

    @AfterAll
    static void tearDownOnce() {
        if (appContext != null) {
            appContext.close();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        JobsCollection jobsCollection() {
            return () -> List.of(DemoJob.class, ExecutionLogCleanupJob.class);
        }
    }
}
