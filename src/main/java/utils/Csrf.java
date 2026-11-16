package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * ZTF_J2EE - Csrf
 *
 * Cross-Site Request Forgery protection with a synchronizer token kept in the session:
 * 1. GET /api/auth/session returns the token to the frontend;
 * 2. the frontend sends it in the X-CSRF-Token header on every state-changing request;
 * 3. the Router validates it on POST/PUT/PATCH/DELETE before running the route (a route may
 *    opt out with "csrf": false in routes.json, e.g. APIs with another kind of authentication).
 *
 * A third-party site cannot read the token (same-origin policy), so it cannot forge the request
 * even though the browser sends the session cookie.
 */
public final class Csrf {

    public static final String SESSION_ATTR = "__ZTF_CSRF__";
    public static final String HEADER = "X-CSRF-Token";
    public static final String PARAM = "_csrf";

    private Csrf() {
    }

    /** Current session token (creates the session and the token when missing). */
    public static String token(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object current = session.getAttribute(SESSION_ATTR);
        if (current instanceof String && !((String) current).isEmpty()) {
            return (String) current;
        }
        String token = CodeGenerator.generateSecureToken(32);
        session.setAttribute(SESSION_ATTR, token);
        return token;
    }

    /** Generates a new token (use after login, when the session is recreated). */
    public static String rotate(HttpSession session) {
        String token = CodeGenerator.generateSecureToken(32);
        session.setAttribute(SESSION_ATTR, token);
        return token;
    }

    /** Methods that do not change state need no token. */
    public static boolean requiresToken(String httpMethod) {
        String m = httpMethod == null ? "" : httpMethod.toLowerCase();
        return !(m.equals("get") || m.equals("head") || m.equals("options") || m.equals("trace"));
    }

    /** Compares the sent token (header or parameter) with the session's, in constant time. */
    public static boolean isValid(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        HttpSession session = request.getSession(false);
        Object expected = session != null ? session.getAttribute(SESSION_ATTR) : null;
        if (!(expected instanceof String) || ((String) expected).isEmpty()) {
            return false;
        }
        String sent = request.getHeader(HEADER);
        if (sent == null || sent.isEmpty()) {
            sent = RequestHelper.getParam(request, PARAM, "");
        }
        if (sent == null || sent.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                ((String) expected).getBytes(StandardCharsets.UTF_8),
                sent.getBytes(StandardCharsets.UTF_8));
    }
}
