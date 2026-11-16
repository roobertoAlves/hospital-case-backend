package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * ZTF_J2EE - Csrf
 *
 * Protecao contra Cross-Site Request Forgery com token sincronizado na sessao:
 * 1. A JSP publica o token: {@code <%= Csrf.metaTag(request) %>} no head (para AJAX) ou
 *    {@code <%= Csrf.hiddenField(request) %>} dentro de um form.
 * 2. zait.ajax.js envia o token no cabecalho X-CSRF-Token em toda requisicao que altera estado.
 * 3. O Router valida o token em POST/PUT/PATCH/DELETE antes de executar a rota
 *    (rotas podem desligar com "csrf": false no routes.json, ex.: APIs com outra autenticacao).
 *
 * Um site de terceiros nao consegue ler o token (mesma origem), entao nao consegue forjar a
 * requisicao mesmo que o navegador envie o cookie de sessao.
 */
public final class Csrf {

    public static final String SESSION_ATTR = "__ZTF_CSRF__";
    public static final String HEADER = "X-CSRF-Token";
    public static final String PARAM = "_csrf";

    private Csrf() {
    }

    /** Token da sessao atual (cria sessao e token se ainda nao existirem). */
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

    /** Gera um token novo (use apos login, quando a sessao e recriada). */
    public static String rotate(HttpSession session) {
        String token = CodeGenerator.generateSecureToken(32);
        session.setAttribute(SESSION_ATTR, token);
        return token;
    }

    public static String metaTag(HttpServletRequest request) {
        return "<meta name=\"csrf-token\" content=\"" + Sanitize.escapeHtml(token(request)) + "\">";
    }

    public static String hiddenField(HttpServletRequest request) {
        return "<input type=\"hidden\" name=\"" + PARAM + "\" value=\"" + Sanitize.escapeHtml(token(request)) + "\">";
    }

    /** Metodos que nao alteram estado nao precisam de token. */
    public static boolean requiresToken(String httpMethod) {
        String m = httpMethod == null ? "" : httpMethod.toLowerCase();
        return !(m.equals("get") || m.equals("head") || m.equals("options") || m.equals("trace"));
    }

    /** Compara o token enviado (cabecalho ou parametro) com o da sessao, em tempo constante. */
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
