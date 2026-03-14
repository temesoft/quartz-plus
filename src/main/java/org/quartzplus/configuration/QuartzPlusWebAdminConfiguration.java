package org.quartzplus.configuration;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.quartzplus.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.io.IOUtils.write;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Configuration class for the QuartzPlus Web Administration portal.
 * <p>
 * This class sets up a {@link Filter} that serves static web resources (HTML, JS, CSS, PNG)
 * for the administration interface directly from the classpath. It allows the portal
 * to be hosted at a configurable URI without requiring a separate web server or
 * complex static resource mapping.
 * <p>
 * The filter also handles API path resolution, mapping the internal web administration
 * request paths to the configured {@code quartz-plus.api-uri}.
 *
 * @see QuartzPlusProperties
 */
@Configuration
@EnableConfigurationProperties(QuartzPlusProperties.class)
public class QuartzPlusWebAdminConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzPlusWebAdminConfiguration.class);
    private static final String DEFAULT_PAGE = "/index.html";
    private static final Map<String, byte[]> CONTENT = new ConcurrentHashMap<>();
    private static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";
    private static final String CONTENT_TYPE_JS = "application/javascript";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_CSS = "text/css";

    private final QuartzPlusProperties quartzPlusProperties;

    /**
     * Constructs the configuration with application-specific properties.
     *
     * @param quartzPlusProperties the properties containing Web Admin and API URI settings.
     */
    public QuartzPlusWebAdminConfiguration(final QuartzPlusProperties quartzPlusProperties) {
        this.quartzPlusProperties = quartzPlusProperties;
    }

    /**
     * Defines a {@link Filter} bean that intercepts requests to the administration portal.
     * <p>
     * If the Web Admin is enabled, this filter:
     * <ul>
     *     <li>Redirects base URI requests to {@code index.html}.</li>
     *     <li>Serves static files from the {@code /webadmin} directory in the classpath.</li>
     *     <li>Sets appropriate {@code Content-Type} headers based on file extensions.</li>
     *     <li>Caches resources in memory after the first load for performance.</li>
     * </ul>
     *
     * @return a {@link Filter} for handling administration portal traffic.
     */
    @Bean
    protected Filter jobsAdminFilter() {
        return new Filter() {
            @Override
            public void init(final FilterConfig filterConfig) {
                if (quartzPlusProperties.getWebAdmin().isEnabled()) {
                    LOGGER.info(
                            "Initializing web administration portal filter: {}",
                            quartzPlusProperties.getWebAdmin().getUri()
                    );
                } else {
                    LOGGER.warn("Administration portal is DISABLED");
                }
            }

            @Override
            public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain filterChain) throws IOException, ServletException {
                if (quartzPlusProperties.getWebAdmin().isEnabled() && request instanceof HttpServletRequest) {
                    final var req = ((HttpServletRequest) request);
                    final var res = ((HttpServletResponse) response);
                    final String requestUri = req.getRequestURI();
                    if (requestUri.startsWith(quartzPlusProperties.getWebAdmin().getUri())) {
                        final var relativeUri = requestUri.replace(quartzPlusProperties.getWebAdmin().getUri(), "");
                        if (isNotBlank(relativeUri)) {
                            final String pageAddress;
                            final var apiRelativeUri = "/" + ClassUtils.classPackageAsResourcePath(Job.class) + "/api";
                            if (relativeUri.equals("/")) {
                                pageAddress = DEFAULT_PAGE;
                            } else if (relativeUri.equals(apiRelativeUri)) {
                                final var responseUrl = quartzPlusProperties.getApiUri() + relativeUri.replace(apiRelativeUri, "");
                                res.getWriter().write(responseUrl);
                                return;
                            } else {
                                pageAddress = relativeUri;
                            }
                            final var resourcePath = ClassUtils.classPackageAsResourcePath(Job.class) + "/webadmin" + pageAddress;
                            if (pageAddress.endsWith(".html")) {
                                response.setContentType(CONTENT_TYPE_HTML);
                            } else if (pageAddress.endsWith(".js")) {
                                response.setContentType(CONTENT_TYPE_JS);
                            } else if (pageAddress.endsWith(".png")) {
                                response.setContentType(CONTENT_TYPE_PNG);
                            } else if (pageAddress.endsWith(".css")) {
                                response.setContentType(CONTENT_TYPE_CSS);
                            }
                            if (CONTENT.containsKey(resourcePath)) {
                                write(CONTENT.get(resourcePath), response.getOutputStream());
                            } else {
                                try {
                                    final var buff = loadResource(resourcePath);
                                    write(buff, response.getOutputStream());
                                    CONTENT.put(resourcePath, buff);
                                } catch (final Exception e) {
                                    LOGGER.warn("Unable to load web resource: {}", resourcePath);
                                }
                            }
                        } else {
                            ((HttpServletResponse) response).sendRedirect(
                                    quartzPlusProperties.getWebAdmin().getUri() + DEFAULT_PAGE
                            );
                        }
                        return;
                    }
                }
                filterChain.doFilter(request, response);
            }

            @Override
            public void destroy() {
            }
        };
    }

    /**
     * Loads a resource from the classpath and converts it to a byte array.
     *
     * @param resourcePath the path to the resource relative to the classpath.
     * @return the resource content as a byte array.
     * @throws IOException if the resource cannot be found or read.
     */
    private byte[] loadResource(final String resourcePath) throws IOException {
        final var in = QuartzPlusCommonConfiguration.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Unable to open resource as stream from: " + resourcePath);
        }
        final var buff = toByteArray(in);
        closeQuietly(in);
        return buff;
    }
}
