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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.io.IOUtils.write;
import static org.springframework.web.util.HtmlUtils.htmlEscape;

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
    private static final String CONTENT_TYPE_TEXT = "text/plain; charset=utf-8";
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "application/javascript",
            "css", "text/css",
            "png", "image/png",
            "svg", "image/svg+xml",
            "woff", "font/woff",
            "woff2", "font/woff2",
            "ttf", "font/ttf",
            "eot", "application/vnd.ms-fontobject"
    );
    // One or more "/"-separated segments of plain characters; no segment may start with a dot, which rules out
    // ".", ".." and hidden files. Encoded characters ("%2e", "%2f") and backslashes never match.
    private static final Pattern WEB_ADMIN_PATH = Pattern.compile("(/[A-Za-z0-9_-][A-Za-z0-9._-]*)+");
    private static final String WEB_ADMIN_RESOURCE_ROOT = ClassUtils.classPackageAsResourcePath(Job.class) + "/webadmin";
    // everything the UI loads is its own; inline styles are allowed for the pages' style attributes and data: images
    // for the toaster's icons, scripts are not (AngularJS runs in ng-csp mode, without eval)
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
    private static final String CSRF_META_TAGS_MARKER = "<!-- quartz-plus:csrf-meta-tags -->";
    private static final boolean SPRING_SECURITY_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.web.csrf.CsrfToken",
            QuartzPlusWebAdminConfiguration.class.getClassLoader()
    );

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
     *     <li>Serves static files from the {@code /webadmin} directory in the classpath, and only those: a path
     *     that is not a plain relative path to such a file ({@code ..}, a backslash, an encoded character), a
     *     missing file and a file type the UI does not ship are all answered {@code 404}.</li>
     *     <li>Sets appropriate {@code Content-Type} headers based on file extensions.</li>
     *     <li>Adds security headers against content sniffing and framing, unless
     *     {@code quartz-plus.web-admin.security-headers} is {@code false}, and {@code Cache-Control: no-store}
     *     on {@code index.html}.</li>
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
                            "Initializing quartz-plus web administration portal filter: {}",
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
                    final var requestUri = req.getRequestURI();
                    final var webAdminUri = quartzPlusProperties.getWebAdmin().getUri();
                    if (requestUri.startsWith(webAdminUri)) {
                        final var relativeUri = requestUri.substring(webAdminUri.length());
                        final var apiUri = quartzPlusProperties.getApiUri();
                        // a path that only shares the prefix, or the API's own path when it lies below the UI's
                        if (!relativeUri.isEmpty() && !relativeUri.startsWith("/")
                                || requestUri.equals(apiUri) || requestUri.startsWith(apiUri + "/")) {
                            filterChain.doFilter(request, response);
                            return;
                        }
                        if (quartzPlusProperties.getWebAdmin().isSecurityHeaders()) {
                            addSecurityHeaders(res);
                        }
                        if (relativeUri.isEmpty()) {
                            res.sendRedirect(webAdminUri + DEFAULT_PAGE);
                            return;
                        }
                        final var apiRelativeUri = "/" + ClassUtils.classPackageAsResourcePath(Job.class) + "/api";
                        if (relativeUri.equals(apiRelativeUri)) {
                            res.setContentType(CONTENT_TYPE_TEXT);
                            res.getWriter().write(quartzPlusProperties.getApiUri());
                            return;
                        }
                        final var pageAddress = relativeUri.equals("/") ? DEFAULT_PAGE : relativeUri;
                        final var contentType = contentTypeOf(pageAddress);
                        final var content = contentType == null ? null : loadWebAdminResource(pageAddress);
                        if (content == null) {
                            res.sendError(HttpServletResponse.SC_NOT_FOUND);
                            return;
                        }
                        res.setContentType(contentType);
                        if (pageAddress.equals(DEFAULT_PAGE)) {
                            res.setHeader("Cache-Control", "no-store");
                            write(renderIndexPage(content, req), res.getOutputStream());
                        } else {
                            write(content, res.getOutputStream());
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
     * Resolves the {@code Content-Type} of a web admin file from its extension.
     *
     * @param pageAddress the requested path relative to the web admin URI
     * @return the content type, or {@code null} for a file type the UI does not ship
     */
    private static String contentTypeOf(final String pageAddress) {
        final var dot = pageAddress.lastIndexOf('.');
        return dot < 0 ? null : CONTENT_TYPES.get(pageAddress.substring(dot + 1));
    }

    /**
     * Loads a file of the web admin UI from the class path, caching its bytes after the first load.
     *
     * @param pageAddress the requested path relative to the web admin URI, starting with {@code /}
     * @return the file content, or {@code null} if the path is not a plain relative path or no such file exists
     * @throws IOException if the file exists but cannot be read
     */
    private static byte[] loadWebAdminResource(final String pageAddress) throws IOException {
        if (!WEB_ADMIN_PATH.matcher(pageAddress).matches()) {
            return null;
        }
        final var resourcePath = WEB_ADMIN_RESOURCE_ROOT + pageAddress;
        final var cached = CONTENT.get(resourcePath);
        if (cached != null) {
            return cached;
        }
        try (final var in = QuartzPlusWebAdminConfiguration.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.debug("Web admin resource not found: {}", resourcePath);
                return null;
            }
            final var content = toByteArray(in);
            CONTENT.put(resourcePath, content);
            return content;
        }
    }

    /**
     * Renders {@code index.html} for one request: the CSRF meta tags marker in its {@code <head>} is replaced
     * with Spring Security's {@code _csrf} and {@code _csrf_header} meta tags when the request carries a CSRF token,
     * and removed otherwise. The rendered page is never cached, since the token belongs to one visitor.
     *
     * @param content the raw bytes of {@code index.html}
     * @param request the request the page is served for
     * @return the page to send
     */
    private static byte[] renderIndexPage(final byte[] content, final HttpServletRequest request) {
        final var metaTags = SPRING_SECURITY_PRESENT ? CsrfMetaTags.of(request) : "";
        return new String(content, StandardCharsets.UTF_8)
                .replace(CSRF_META_TAGS_MARKER, metaTags)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Adds the headers that stop browsers from sniffing content types, from framing the UI in another site, and from
     * loading anything into the UI from another origin.
     *
     * @param response the response to add the headers to
     */
    private static void addSecurityHeaders(final HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
    }

    /**
     * Reads the CSRF token Spring Security put on the request. Kept in its own class so that Spring Security's
     * classes are only loaded when they are on the class path.
     */
    private static final class CsrfMetaTags {

        private static String of(final HttpServletRequest request) {
            if (request.getAttribute(CsrfToken.class.getName()) instanceof final CsrfToken csrfToken) {
                return "<meta name=\"_csrf\" content=\"" + htmlEscape(csrfToken.getToken()) + "\">\n"
                        + "    <meta name=\"_csrf_header\" content=\"" + htmlEscape(csrfToken.getHeaderName()) + "\">";
            }
            return "";
        }
    }
}
