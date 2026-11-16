package controllers;

import java.io.IOException;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import models.User;
import utils.Sanitize;

/**
 * ZTF_J2EE - SessionFilter
 *
 * Keeps session data consistent with the database (RN-AUT-06). Groups, permissions and status
 * would otherwise stay frozen until the next login, so a blocked user could keep working.
 * When the administrator changes groups, permissions or status, User.invalidateAcl() bumps a
 * global version; here every session holding an older version is reloaded once (or ended,
 * if the user was deactivated/removed).
 *
 * It lives in the controllers package because it depends on the User model (utils does not
 * depend on models). The version lives in JVM memory: with several servers it would have to
 * be shared.
 */
@WebFilter(filterName = "SessaoFilter", urlPatterns = {"/*"})
public class SessionFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            updateSession((HttpServletRequest) req);
        }
        chain.doFilter(req, res);
    }

    public static void updateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object raw;
        try {
            raw = session.getAttribute("user");
        } catch (IllegalStateException invalidated) {
            return;
        }
        if (!(raw instanceof Map<?, ?>)) {
            return;
        }
        Map<?, ?> user = (Map<?, ?>) raw;
        long sessionVersion = user.get("acl_versao") instanceof Number ? ((Number) user.get("acl_versao")).longValue() : -1L;
        if (sessionVersion == User.versionAcl()) {
            return;
        }
        int userId = Sanitize.intVal(user.get("id_usuario"), 0);
        Map<String, Object> updated = userId > 0 ? new User().loadSession(userId) : null;
        if (updated == null) {
            session.invalidate();
        } else {
            session.setAttribute("user", updated);
        }
    }

    @Override
    public void destroy() {
    }
}
