package com.sismics.docs.rest.filter;

import com.sismics.docs.core.util.ConfigUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dynamically serves /robots.txt and attaches X-Robots-Tag headers based on
 * the administrator's public/private search engine indexing setting.
 */
public class RobotsFilter implements Filter {
    public static final String ROBOTS_PRIVATE = "User-agent: *\nDisallow: /\n";
    public static final String ROBOTS_PUBLIC = "User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /share.html\n";
    public static final String NOINDEX_HEADER_VALUE = "noindex, nofollow, noarchive";

    /**
     * Returns the robots.txt content for the given public-indexing flag.
     */
    public static String buildRobotsTxt(boolean indexingEnabled) {
        return indexingEnabled ? ROBOTS_PUBLIC : ROBOTS_PRIVATE;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;
        String contextPath = httpReq.getContextPath() != null ? httpReq.getContextPath() : "";
        String uri = httpReq.getRequestURI() != null ? httpReq.getRequestURI() : "";
        String path = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

        boolean indexingEnabled = ConfigUtil.isRobotsIndexingEnabled();

        if ("/robots.txt".equals(path)) {
            String body = buildRobotsTxt(indexingEnabled);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            httpRes.setStatus(HttpServletResponse.SC_OK);
            httpRes.setContentType("text/plain; charset=UTF-8");
            httpRes.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            httpRes.setContentLength(bytes.length);
            httpRes.getOutputStream().write(bytes);
            return;
        }

        // Always prevent indexing on private mode, or on /api/* and /share.html even in public mode
        if (!indexingEnabled || path.startsWith("/api/") || "/api".equals(path) || "/share.html".equals(path)) {
            httpRes.setHeader("X-Robots-Tag", NOINDEX_HEADER_VALUE);
        }

        chain.doFilter(request, response);
    }
}
