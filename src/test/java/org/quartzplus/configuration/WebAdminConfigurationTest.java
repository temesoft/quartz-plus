package org.quartzplus.configuration;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class WebAdminConfigurationTest {

    private static final String WEB_ADMIN_URI = "/test-scheduler/admin"; // set in test application.properties

    private static ConfigurableApplicationContext appContext;
    private static int port = -1;

    @Test
    void testAdminPortal() {
        final var restTemplate = new RestTemplate();
        var result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/index.html", String.class);
        assertThat(result).isNotNull().contains("<title>QuartzPlus</title>");
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/", String.class);
        assertThat(result).isNotNull().contains("<title>QuartzPlus</title>");
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI, String.class);
        assertThat(result).isNotNull().contains("<title>QuartzPlus</title>");
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/app.js", String.class);
        assertThat(result).isNotNull().hasSizeGreaterThan(3000);
        result = restTemplate.getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/logo.png", String.class);
        assertThat(result).isNotNull();
    }

    @Test
    void testIndexPageWithoutSpringSecurityHasNoCsrfMetaTags() {
        final var result = new RestTemplate().getForObject("http://localhost:" + port + WEB_ADMIN_URI + "/index.html", String.class);
        assertThat(result).contains("<title>QuartzPlus</title>").doesNotContain("_csrf").doesNotContain("quartz-plus:csrf-meta-tags");
    }

    @Test
    void testFilterRefusesPathTraversalWithoutLoadingAnything() throws Exception {
        for (final var path : List.of(
                "/../../../application.properties",
                "/..%2f..%2f..%2fapplication.properties",
                "/%2e%2e/%2e%2e/%2e%2e/application.properties",
                "/..\\..\\..\\application.properties",
                "/inc/../index.html",
                "/./index.html",
                "//index.html",
                "/%69ndex.html"
        )) {
            final var response = filter(WEB_ADMIN_URI + path, true);
            assertThat(response.getStatus()).as(path).isEqualTo(404);
            assertThat(response.getContentAsString()).as(path).doesNotContain("server.port").doesNotContain("<html");
        }
    }

    @Test
    void testFilterAnswersMissingFileAndUnknownFileType404() throws Exception {
        assertThat(filter(WEB_ADMIN_URI + "/missing.js", true).getStatus()).isEqualTo(404);
        assertThat(filter(WEB_ADMIN_URI + "/inc", true).getStatus()).isEqualTo(404);
        assertThat(filter(WEB_ADMIN_URI + "/index", true).getStatus()).isEqualTo(404);
    }

    @Test
    void testFilterServesIndexAndAppJsWithContentTypesAndSecurityHeaders() throws Exception {
        final var index = filter(WEB_ADMIN_URI + "/", true);
        assertThat(index.getStatus()).isEqualTo(200);
        assertThat(index.getContentType()).isEqualTo("text/html; charset=utf-8");
        assertThat(index.getContentAsString()).contains("<html");
        assertThat(index.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(index.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(index.getHeader("Content-Security-Policy"))
                .isEqualTo("default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; base-uri 'self'; "
                        + "form-action 'self'; frame-ancestors 'none'");
        assertThat(index.getHeader("Cache-Control")).isEqualTo("no-store");

        final var appJs = filter(WEB_ADMIN_URI + "/app.js", true);
        assertThat(appJs.getStatus()).isEqualTo(200);
        assertThat(appJs.getContentType()).isEqualTo("application/javascript");
        assertThat(appJs.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(appJs.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(appJs.getHeader("Cache-Control")).isNull();

        final var missing = filter(WEB_ADMIN_URI + "/missing.js", true);
        assertThat(missing.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void testIndexPageLoadsOnlyItsOwnFilesAndEachIsServed() throws Exception {
        final var index = filter(WEB_ADMIN_URI + "/index.html", true).getContentAsString();
        final var references = Pattern.compile("(?:src|href)=\"([^\"#]+)\"").matcher(index).results()
                .map(match -> match.group(1))
                .toList();
        assertThat(references).isNotEmpty().allSatisfy(reference -> assertThat(reference)
                .doesNotContain("//")
                .doesNotContain(":")
                .doesNotStartWith("/")
                .doesNotContain(".."));
        for (final var reference : references) {
            final var response = filter(WEB_ADMIN_URI + "/" + reference, true);
            assertThat(response.getStatus()).as(reference).isEqualTo(200);
            assertThat(response.getContentType()).as(reference).isNotBlank();
        }
        assertThat(filter(WEB_ADMIN_URI + "/vendor/bootstrap/fonts/glyphicons-halflings-regular.woff2", true).getContentType())
                .isEqualTo("font/woff2");
        assertThat(filter(WEB_ADMIN_URI + "/vendor/README.md", true).getStatus()).isEqualTo(404);
    }

    @Test
    void testFilterOmitsSecurityHeadersWhenSwitchedOff() throws Exception {
        final var index = filter(WEB_ADMIN_URI + "/index.html", false);
        assertThat(index.getStatus()).isEqualTo(200);
        assertThat(index.getHeader("X-Content-Type-Options")).isNull();
        assertThat(index.getHeader("X-Frame-Options")).isNull();
        assertThat(index.getHeader("Content-Security-Policy")).isNull();
        assertThat(index.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void testFilterPassesOnPathsThatOnlyShareThePrefix() throws Exception {
        final var request = new MockHttpServletRequest("GET", WEB_ADMIN_URI + "istration/index.html");
        final var response = new MockHttpServletResponse();
        final var chain = new MockFilterChain();
        webAdminFilter(true).doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void testFilterPassesOnApiPathsBelowTheWebAdminUri() throws Exception {
        final var properties = new QuartzPlusProperties();
        properties.setApiUri("/admin/scheduler/api");
        properties.getWebAdmin().setUri("/admin/scheduler");
        final var filter = new QuartzPlusWebAdminConfiguration(properties).jobsAdminFilter();
        for (final var path : List.of("/admin/scheduler/api", "/admin/scheduler/api/trigger/pause", "/admin/scheduler/api/jobs")) {
            final var request = new MockHttpServletRequest("GET", path);
            final var chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
        final var index = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/admin/scheduler/index.html"), index, new MockFilterChain());
        assertThat(index.getContentAsString()).contains("<title>QuartzPlus</title>");
    }

    @Test
    void testFilterRedirectsPrefixToIndexAndAnswersApiUri() throws Exception {
        final var redirect = filter(WEB_ADMIN_URI, true);
        assertThat(redirect.getRedirectedUrl()).isEqualTo(WEB_ADMIN_URI + "/index.html");
        final var api = filter(WEB_ADMIN_URI + "/org/quartzplus/api", true);
        assertThat(api.getContentAsString()).isEqualTo("/test-scheduler-api");
    }

    private static MockHttpServletResponse filter(final String requestUri, final boolean securityHeaders) throws Exception {
        final var request = new MockHttpServletRequest("GET", requestUri);
        final var response = new MockHttpServletResponse();
        final var chain = new MockFilterChain();
        webAdminFilter(securityHeaders).doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNull();
        return response;
    }

    private static Filter webAdminFilter(final boolean securityHeaders) {
        final var properties = new QuartzPlusProperties();
        properties.setApiUri("/test-scheduler-api");
        properties.getWebAdmin().setUri(WEB_ADMIN_URI);
        properties.getWebAdmin().setSecurityHeaders(securityHeaders);
        return new QuartzPlusWebAdminConfiguration(properties).jobsAdminFilter();
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
            return () -> List.of(ExecutionLogCleanupJob.class);
        }
    }
}
