package org.quartzplus.configuration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartzplus.internal.ExecutionLogCleanupJob;
import org.quartzplus.service.JobsCollection;
import org.quartzplus.test.EmbeddedPortRetriever;
import org.quartzplus.test.TestCoreConfigImport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.config.Customizer.withDefaults;

class WebAdminCsrfTest {

    private static final String WEB_ADMIN_URI = "/admin/scheduler";
    private static final String API_URI = "/admin/scheduler/api";
    private static final String BASIC_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("admin:admin-password".getBytes(StandardCharsets.UTF_8));
    private static final String PAUSE_URI = API_URI + "/trigger/pause?groupName=Internal"
            + "&triggerName=ExecutionLogCleanupJob-Trigger";
    private static final String RESUME_URI = API_URI + "/trigger/resume?groupName=Internal"
            + "&triggerName=ExecutionLogCleanupJob-Trigger";
    private static final Pattern CSRF_TOKEN = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\">");
    private static final Pattern CSRF_HEADER = Pattern.compile("<meta name=\"_csrf_header\" content=\"([^\"]+)\">");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static ConfigurableApplicationContext appContext;
    private static int port = -1;

    @Test
    void testIndexPageCarriesCsrfMetaTagsWithDifferentTokenPerRequest() throws Exception {
        final var first = getIndexPage(null);
        final var second = getIndexPage(sessionCookie(first));
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).doesNotContain("quartz-plus:csrf-meta-tags");
        assertThat(csrfHeader(first)).isEqualTo("X-CSRF-TOKEN");
        assertThat(csrfToken(first)).isNotBlank();
        assertThat(csrfToken(second)).isNotBlank().isNotEqualTo(csrfToken(first));
        assertThat(first.headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @Test
    void testPauseAndResumeWithCsrfTokenFromPageAreAccepted() throws Exception {
        final var page = getIndexPage(null);
        final var pause = post(PAUSE_URI, sessionCookie(page), csrfHeader(page), csrfToken(page));
        final var resume = post(RESUME_URI, sessionCookie(page), csrfHeader(page), csrfToken(page));
        assertThat(pause.statusCode()).isEqualTo(202);
        assertThat(resume.statusCode()).isEqualTo(202);
    }

    @Test
    void testPauseWithoutCsrfTokenIsRefused() throws Exception {
        final var page = getIndexPage(null);
        assertThat(post(PAUSE_URI, sessionCookie(page), null, null).statusCode()).isEqualTo(403);
        assertThat(post(PAUSE_URI, null, null, null).statusCode()).isEqualTo(403);
        assertThat(post(PAUSE_URI, sessionCookie(page), csrfHeader(page), "wrong-token").statusCode()).isEqualTo(403);
    }

    private static HttpResponse<String> getIndexPage(final String cookie) throws Exception {
        final var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + WEB_ADMIN_URI + "/index.html"))
                .header("Authorization", BASIC_AUTH);
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return HTTP_CLIENT.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(final String uri, final String cookie, final String header, final String token) throws Exception {
        final var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .header("Authorization", BASIC_AUTH);
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (header != null) {
            request.header(header, token);
        }
        return HTTP_CLIENT.send(request.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String sessionCookie(final HttpResponse<String> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
    }

    private static String csrfToken(final HttpResponse<String> response) {
        final var matcher = CSRF_TOKEN.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String csrfHeader(final HttpResponse<String> response) {
        final var matcher = CSRF_HEADER.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @BeforeAll
    static void setUpOnce() throws Exception {
        final var app = new SpringApplication(
                TestConfig.class,
                SecurityConfig.class,
                TestCoreConfigImport.class,
                QuartzPlusAutoConfiguration.class,
                QuartzAutoConfiguration.class,
                QuartzPlusFlywayConfiguration.class
        );
        final var embeddedPortRetriever = new EmbeddedPortRetriever(app);
        app.setBannerMode(Banner.Mode.OFF);
        appContext = app.run("--quartz-plus.web-admin.uri=" + WEB_ADMIN_URI, "--quartz-plus.api-uri=" + API_URI);
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
            return () -> List.of(ExecutionLogCleanupJob.class);
        }
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(
                final HttpSecurity http,
                @Value("${quartz-plus.web-admin.uri:/scheduler/admin}") final String webAdminUri,
                @Value("${quartz-plus.api-uri:/scheduler}") final String apiUri
        ) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(webAdminUri, webAdminUri + "/**", apiUri, apiUri + "/**").hasRole("ADMIN")
                            .anyRequest().permitAll())
                    .httpBasic(withDefaults());
            return http.build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("admin").password("{noop}admin-password").roles("ADMIN").build()
            );
        }

        @Bean
        DelegatingFilterProxyRegistrationBean securityFilterChainRegistration() {
            final var registration = new DelegatingFilterProxyRegistrationBean("springSecurityFilterChain");
            registration.setOrder(-100);
            return registration;
        }
    }
}
