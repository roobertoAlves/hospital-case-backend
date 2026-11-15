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

/**
 * ZTF_J2EE - SecurityHeadersFilter
 *
 * Aplica cabecalhos HTTP de seguranca a TODAS as respostas (controllers, JSPs acessadas
 * diretamente e arquivos estaticos), num unico lugar.
 *
 * A CSP permite 'unsafe-inline' em script/style porque as JSPs do framework usam scripts
 * inline; ainda assim bloqueia scripts de outros dominios, plugins, frames e o envio de
 * formularios para fora da aplicacao.
 */
@WebFilter(filterName = "SecurityHeadersFilter", urlPatterns = {"/*"})
public class SecurityHeadersFilter implements Filter {

    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'none'";

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
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        if (!isStaticAsset(request)) {
            // Paginas e JSON podem conter dados pessoais: nao guardar em cache.
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
        }
    }

    static boolean isStaticAsset(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        String path = (uri != null && ctx != null && uri.startsWith(ctx)) ? uri.substring(ctx.length()) : String.valueOf(uri);
        return path.startsWith("/assets/") || path.equals("/manual.html") || path.equals("/manual");
    }

    @Override
    public void destroy() {
    }
}
