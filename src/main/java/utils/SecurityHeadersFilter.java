package utils;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebFilter(filterName = "SecurityHeadersFilter", urlPatterns = {"/*"})
public class SecurityHeadersFilter implements Filter {

    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest && res instanceof HttpServletResponse) {
            apply((HttpServletRequest) req, (HttpServletResponse) res);
        }
        chain.doFilter(req, res);
    }

    static void apply(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    @Override
    public void destroy() {
    }
}
