package utils;

import java.io.File;
import javax.servlet.http.HttpServletRequest;

/**
 * ZTF_J2EE - Url
 *
 * Resolves the protocol, host, port and context path (rootPath) from the HTTP request, with no
 * hard-coded values, so the project folder/context, server or port can change freely.
 */
public class Url {

    private static final ThreadLocal<HttpServletRequest> CURRENT_REQUEST = new ThreadLocal<>();
    private static volatile String detectedContextPath = null;
    private static String overrideBasePath = null;

    /**
     * Binds the active HTTP request to the current thread (called by Controller.service()).
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
     * Clears the request reference at the end of the HTTP cycle.
     */
    public static void clearRequest() {
        CURRENT_REQUEST.remove();
    }

    /**
     * Stores the context path detected when the ServletContext starts.
     */
    public static void setDetectedContextPath(String contextPath) {
        detectedContextPath = contextPath;
    }

    public static void setOverrideBasePath(String basePath) {
        overrideBasePath = basePath;
    }

    /**
     * Returns the project's rootPath / basePath, always ending with a slash.
     * Priority:
     * 1. config "app.base_path" (when explicitly set)
     * 2. overrideBasePath (when set programmatically)
     * 3. request.getContextPath() of the current HTTP request
     * 4. context path detected by the ServletContext
     * 5. the project folder name (when running from the CLI, outside the HTTP container)
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
     * Returns the rootPath without the trailing slash (or empty at the domain root).
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
     * Builds a URL relative to the application root, honouring the dynamic context path.
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
     * Returns the full absolute URL (scheme + host + port + context path + path) taken from the
     * current HTTP request (reverse proxies included).
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
     * Detects the project's root folder name when running from the CLI/tests (no active
     * HttpServletRequest), so no project name is hard-coded.
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
