package org.quartzplus;

import org.quartzplus.internal.ExecutionLogCleanupJob;
import org.quartzplus.service.JobsCollection;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

@SpringBootApplication
@Import(Application.AppConfig.class)
@EntityScan(basePackages = "org.quartzplus") // Force scan your library entities
@EnableJpaRepositories(basePackages = "org.quartzplus")
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, "--server.port=8080", "--spring.thymeleaf.cache=false");
    }

    @Configuration
    public static class AppConfig {
        @Bean
        JobsCollection appJobsCollection() {
            return () -> List.of(DemoJob.class, ExecutionLogCleanupJob.class);
        }
    }
}
