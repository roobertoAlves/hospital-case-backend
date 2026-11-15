package support;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Request/response/sessao minimos para testes unitarios, feitos com java.lang.reflect.Proxy
 * (sem dependencia de bibliotecas de mock). Metodos nao tratados devolvem valores neutros.
 */
public final class FakeHttp {

    private FakeHttp() {
    }

    /** Resposta que guarda cabecalhos e status para inspecao. */
    public static final class Response {
        public final Map<String, String> headers = new LinkedHashMap<>();
        public int status = 200;
        public final HttpServletResponse proxy;

        Response() {
            this.proxy = (HttpServletResponse) Proxy.newProxyInstance(
                    FakeHttp.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                    (p, m, args) -> {
                        switch (m.getName()) {
                            case "setHeader":
                            case "addHeader":
                                headers.put((String) args[0], (String) args[1]);
                                return null;
                            case "getHeader":
                                return headers.get((String) args[0]);
                            case "setStatus":
                                status = (Integer) args[0];
                                return null;
                            case "getStatus":
                                return status;
                            case "isCommitted":
                                return false;
                            default:
                                return neutral(m.getReturnType());
                        }
                    });
        }
    }

    public static Response response() {
        return new Response();
    }

    public static HttpSession session(Map<String, Object> attributes) {
        Map<String, Object> attrs = attributes != null ? attributes : new HashMap<>();
        return (HttpSession) Proxy.newProxyInstance(
                FakeHttp.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (p, m, args) -> {
                    switch (m.getName()) {
                        case "getAttribute":
                            return attrs.get((String) args[0]);
                        case "setAttribute":
                            attrs.put((String) args[0], args[1]);
                            return null;
                        case "removeAttribute":
                            attrs.remove((String) args[0]);
                            return null;
                        case "getId":
                            return "sessao-teste";
                        default:
                            return neutral(m.getReturnType());
                    }
                });
    }

    /**
     * @param method   GET, POST...
     * @param uri      URI completa (com contextPath)
     * @param headers  cabecalhos HTTP
     * @param params   parametros de formulario/query
     * @param session  sessao (ou null)
     */
    public static HttpServletRequest request(String method, String uri, Map<String, String> headers,
                                             Map<String, String> params, HttpSession session) {
        Map<String, String> h = headers != null ? headers : new HashMap<>();
        Map<String, String> prm = params != null ? params : new HashMap<>();
        Map<String, Object> attributes = new HashMap<>();
        return (HttpServletRequest) Proxy.newProxyInstance(
                FakeHttp.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (p, m, args) -> {
                    switch (m.getName()) {
                        case "getMethod":
                            return method;
                        case "getRequestURI":
                            return uri;
                        case "getContextPath":
                            return "/app";
                        case "getHeader":
                            return h.get((String) args[0]);
                        case "getParameter":
                            return prm.get((String) args[0]);
                        case "getParameterMap": {
                            Map<String, String[]> map = new HashMap<>();
                            prm.forEach((k, v) -> map.put(k, new String[]{v}));
                            return map;
                        }
                        case "getSession":
                            return session;
                        case "getAttribute":
                            return attributes.get((String) args[0]);
                        case "setAttribute":
                            attributes.put((String) args[0], args[1]);
                            return null;
                        case "isSecure":
                            return false;
                        case "getRemoteAddr":
                            return "127.0.0.1";
                        default:
                            return neutral(m.getReturnType());
                    }
                });
    }

    private static Object neutral(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == short.class) {
            return type == long.class ? 0L : (type == short.class ? (short) 0 : 0);
        }
        return null;
    }
}
