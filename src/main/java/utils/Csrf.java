package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class Csrf {

    public static final String SESSION_ATTR = "__ZTF_CSRF__";
    public static final String HEADER = "X-CSRF-Token";
    public static final String PARAM = "_csrf";

    private Csrf() {
    }

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

    public static String rotate(HttpSession session) {
        String token = CodeGenerator.generateSecureToken(32);
        session.setAttribute(SESSION_ATTR, token);
        return token;
    }

    public static boolean requiresToken(String httpMethod) {
        String m = httpMethod == null ? "" : httpMethod.toLowerCase();
        return !(m.equals("get") || m.equals("head") || m.equals("options") || m.equals("trace"));
    }

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
