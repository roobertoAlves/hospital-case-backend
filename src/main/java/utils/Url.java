package utils;

import java.io.File;
import javax.servlet.http.HttpServletRequest;

/**
 * ZTF_J2EE - Url
 *
 * Resolve dinamicamente o protocolo, host, porta e contextPath (rootPath)
 * diretamente a partir da requisicao HTTP (HttpServletRequest), sem valores fixos,
 * permitindo renomear a pasta/contexto do projeto ou mudar de servidor/porta livremente.
 */
public class Url {

    private static final ThreadLocal<HttpServletRequest> CURRENT_REQUEST = new ThreadLocal<>();
    private static volatile String detectedContextPath = null;
    private static String overrideBasePath = null;

    /**
     * Registra a requisicao HTTP ativa na thread atual (invocado pelo Controller.service() e views).
     */
    public static void bindRequest(HttpServletRequest request) {
        if (request != null) {
            CURRENT_REQUEST.set(request);
            try {
                String ctx = request.getContextPath();
                if (ctx != null) {
                    detectedContextPath = ctx;
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Limpa a referencia da requisicao ao final do ciclo HTTP.
     */
    public static void clearRequest() {
        CURRENT_REQUEST.remove();
    }

    /**
     * Registra o contextPath detectado no boot do ServletContext.
     */
    public static void setDetectedContextPath(String contextPath) {
        detectedContextPath = contextPath;
    }

    public static void setOverrideBasePath(String basePath) {
        overrideBasePath = basePath;
    }

    /**
     * Retorna o rootPath / basePath do projeto com barra final garantida.
     * Prioridade:
     * 1. Config "app.base_path" (se explicitamente definido)
     * 2. overrideBasePath (se definido programaticamente)
     * 3. request.getContextPath() da requisicao HTTP atual
     * 4. ContextPath detectado pelo ServletContext
     * 5. Nome dinamico da pasta do projeto (quando executado via CLI fora do container HTTP)
     */
    public static String base(HttpServletRequest request) {
        if (request != null) {
            bindRequest(request);
        }

        String configured = Config.getString("app.base_path", null);
        if (configured != null && !configured.trim().isEmpty()) {
            return normalizeBasePath(configured);
        }

        if (overrideBasePath != null && !overrideBasePath.isEmpty()) {
            return normalizeBasePath(overrideBasePath);
        }

        HttpServletRequest activeReq = (request != null) ? request : CURRENT_REQUEST.get();
        if (activeReq != null) {
            String contextPath = activeReq.getContextPath();
            if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
                return "/";
            }
            return normalizeBasePath(contextPath);
        }

        if (detectedContextPath != null) {
            if (detectedContextPath.isEmpty() || "/".equals(detectedContextPath)) {
                return "/";
            }
            return normalizeBasePath(detectedContextPath);
        }

        return detectProjectFolderBase();
    }

    public static String base() {
        return base(null);
    }

    /**
     * Retorna o rootPath sem a barra final (ou vazio se na raiz do dominio).
     */
    public static String rootPath(HttpServletRequest request) {
        String b = base(request);
        if ("/".equals(b)) {
            return "";
        }
        return b.replaceAll("/+$", "");
    }

    public static String rootPath() {
        return rootPath(null);
    }

    /**
     * Gera uma URL relativa a raiz da aplicacao respeitando o contextPath dinamico.
     */
    public static String to(HttpServletRequest request, String path) {
        String b = base(request);
        String trimmedPath = (path == null) ? "" : path.replaceAll("^/+", "");
        if (trimmedPath.isEmpty()) {
            return b;
        }
        return b.replaceAll("/+$", "") + "/" + trimmedPath;
    }

    public static String to(String path) {
        return to(null, path);
    }

    /**
     * Retorna a URL absoluta completa (esquema + host + porta + contextPath + path)
     * extraida dinamicamente da requisicao HTTP atual (incluindo suporte a proxies reversos).
     */
    public static String full(HttpServletRequest request, String path) {
        HttpServletRequest activeReq = (request != null) ? request : CURRENT_REQUEST.get();
        if (activeReq != null) {
            String forwardedProto = activeReq.getHeader("X-Forwarded-Proto");
            String scheme = (forwardedProto != null && !forwardedProto.trim().isEmpty())
                    ? forwardedProto.split(",")[0].trim()
                    : (activeReq.getScheme() != null ? activeReq.getScheme() : "http");

            String forwardedHost = activeReq.getHeader("X-Forwarded-Host");
            String hostHeader = (forwardedHost != null && !forwardedHost.trim().isEmpty())
                    ? forwardedHost.split(",")[0].trim()
                    : activeReq.getHeader("Host");

            if (hostHeader != null && !hostHeader.trim().isEmpty()) {
                return scheme + "://" + hostHeader.trim() + to(activeReq, path);
            }

            String serverName = activeReq.getServerName() != null ? activeReq.getServerName() : "localhost";
            int port = activeReq.getServerPort();
            boolean isDefaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443)
                    || port <= 0;
            String portPart = isDefaultPort ? "" : ":" + port;
            return scheme + "://" + serverName + portPart + to(activeReq, path);
        }

        String configuredUrl = Config.getString("app.url", null);
        if (configuredUrl != null && !configuredUrl.trim().isEmpty()) {
            String baseOrigin = configuredUrl.trim().replaceAll("/+$", "");
            String trimmedPath = (path == null) ? "" : path.replaceAll("^/+", "");
            return trimmedPath.isEmpty() ? baseOrigin + "/" : baseOrigin + "/" + trimmedPath;
        }

        return "http://localhost" + to(null, path);
    }

    public static String full(String path) {
        return full(null, path);
    }

    private static String normalizeBasePath(String raw) {
        String trimmed = raw.trim().replace('\\', '/').replaceAll("/+$", "");
        if (trimmed.isEmpty() || "/".equals(trimmed) || ".".equals(trimmed)) {
            return "/";
        }
        return (trimmed.startsWith("/") ? trimmed : "/" + trimmed) + "/";
    }

    /**
     * Detecta dinamicamente o nome da pasta raiz do projeto quando executado em modo CLI/Testes
     * (sem HttpServletRequest ativo), evitando qualquer nome de projeto fixo no codigo.
     */
    private static String detectProjectFolderBase() {
        try {
            File dir = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
            String folderName = dir.getName();
            if (folderName != null && !folderName.isEmpty() && !"/".equals(folderName)) {
                return "/" + folderName + "/";
            }
        } catch (Exception ignored) {
        }
        return "/";
    }
}
