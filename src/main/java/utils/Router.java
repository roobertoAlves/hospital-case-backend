package utils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import database.DBConnection;
import database.DBQuery;
import database.DatabaseProvisioner;

/**
 * ZTF_J2EE - Router
 *
 * Central route manager (routes.json) with:
 * - dynamic URI parameters (e.g. /api/files/{hash})
 * - HTTP verbs (GET, POST, PUT, DELETE, OPTIONS, HEAD)
 * - session ACL (groups, permissions, users, strictGroup, allowFirstAccess)
 * - CSRF check on state-changing requests and CORS
 * - JSON answers for every route (the frontend is a separate application)
 */
public class Router {

    private static final Gson GSON = new Gson();
    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    public static final String ERR_PASSWORD_CHANGE_REQUIRED = "ERR_PASSWORD_CHANGE_REQUIRED";

    /**
     * Cache of the default routes (the webapp routes.json), so requests do not re-read and
     * re-parse the file. In development the cache is dropped when the file changes
     * (lastModified); in production it lasts until the application restarts.
     */
    private static volatile Map<String, Map<String, Map<String, Object>>> cachedRoutes;
    private static volatile long cachedRoutesModified = -1L;

    private Map<String, Map<String, Map<String, Object>>> routes = new LinkedHashMap<>();
    private final String routesFilePath;
    private final String basePath;

    public Router() {
        this(null, null);
    }

    public Router(String routesFilePath, String basePath) {
        this.basePath = (basePath != null && !basePath.isEmpty()) ? basePath : Config.getWebappRoot();
        this.routesFilePath = (routesFilePath != null && !routesFilePath.isEmpty()) ? routesFilePath : null;
        this.loadRoutes();
    }

    public final void loadRoutes() {
        try {
            String content;
            if (this.routesFilePath != null) {
                File file = new File(this.routesFilePath);
                if (!file.isFile()) {
                    throw new RuntimeException("Routes file not found: " + this.routesFilePath);
                }
                content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } else {
                this.routes = copyRoutes(loadDefaultRoutesCached());
                return;
            }
            Type type = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Object>>>>() {}.getType();
            Map<String, Map<String, Map<String, Object>>> decoded = GSON.fromJson(content, type);
            if (decoded == null) {
                throw new RuntimeException("Could not decode routes.json: empty or invalid.");
            }
            this.routes = decoded;
        } catch (Exception e) {
            throw new RuntimeException("Could not load routes.json: " + e.getMessage(), e);
        }
    }

    private static Map<String, Map<String, Map<String, Object>>> loadDefaultRoutesCached() throws java.io.IOException {
        String relRoutes = Config.getString("paths.routes", "WEB-INF/etc/routes.json");
        long modified = routesLastModified(relRoutes);
        Map<String, Map<String, Map<String, Object>>> current = cachedRoutes;
        boolean development = !"production".equalsIgnoreCase(Config.getString("app.env", "development"));
        if (current != null && (!development || modified == cachedRoutesModified)) {
            return current;
        }
        synchronized (Router.class) {
            if (cachedRoutes != null && (!development || modified == cachedRoutesModified)) {
                return cachedRoutes;
            }
            Type type = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, Object>>>>() {}.getType();
            Map<String, Map<String, Map<String, Object>>> decoded = GSON.fromJson(Config.readResourceText(relRoutes), type);
            if (decoded == null) {
                throw new RuntimeException("Could not decode routes.json: empty or invalid.");
            }
            cachedRoutes = decoded;
            cachedRoutesModified = modified;
            return decoded;
        }
    }

    private static long routesLastModified(String relRoutes) {
        File file = new File(Config.getWebappRoot(), relRoutes);
        return file.isFile() ? file.lastModified() : 0L;
    }

    /** Two-level copy: addRoute() on one instance does not change the shared cache. */
    private static Map<String, Map<String, Map<String, Object>>> copyRoutes(Map<String, Map<String, Map<String, Object>>> source) {
        Map<String, Map<String, Map<String, Object>>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : source.entrySet()) {
            copy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        return copy;
    }

    /** Drops the route cache (used by tests, or after editing routes.json in production). */
    public static void clearRouteCache() {
        cachedRoutes = null;
        cachedRoutesModified = -1L;
    }

    public Map<String, Map<String, Map<String, Object>>> getRoutes() {
        return this.routes;
    }

    public void addRoute(String uri, String method, Map<String, Object> config) {
        String clean = "/" + (uri == null ? "" : uri.replaceAll("^/+|/+$", ""));
        if ("//".equals(clean)) {
            clean = "/";
        }
        this.routes.computeIfAbsent(clean, k -> new LinkedHashMap<>())
                   .put(method.toLowerCase(), config);
    }

    public boolean isAjaxRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String xReq = request.getHeader("X-Requested-With");
        if (xReq != null && "xmlhttprequest".equalsIgnoreCase(xReq.trim())) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("application/json");
    }

    public static class RouteMatch {
        public final Map<String, Map<String, Object>> methods;
        public final Map<String, String> params;

        public RouteMatch(Map<String, Map<String, Object>> methods, Map<String, String> params) {
            this.methods = methods;
            this.params = params;
        }
    }

    public RouteMatch matchRoute(String uri) {
        if (this.routes.containsKey(uri)) {
            return new RouteMatch(this.routes.get(uri), new LinkedHashMap<>());
        }

        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : this.routes.entrySet()) {
            String pattern = entry.getKey();
            if (!pattern.contains("{")) {
                continue;
            }

            List<String> paramNames = new ArrayList<>();
            Matcher m = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(pattern);
            StringBuilder regexBuilder = new StringBuilder("^");
            int lastEnd = 0;
            while (m.find()) {
                regexBuilder.append(Pattern.quote(pattern.substring(lastEnd, m.start())));
                String paramName = m.group(1);
                paramNames.add(paramName);
                regexBuilder.append("([^/]+)");
                lastEnd = m.end();
            }
            regexBuilder.append(Pattern.quote(pattern.substring(lastEnd))).append("$");

            Matcher uriMatcher = Pattern.compile(regexBuilder.toString()).matcher(uri);
            if (uriMatcher.matches()) {
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < paramNames.size(); i++) {
                    params.put(paramNames.get(i), uriMatcher.group(i + 1));
                }
                return new RouteMatch(entry.getValue(), params);
            }
        }
        return null;
    }

    public Object dispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return dispatch(request, response, null, null, null);
    }

    public Object dispatch(HttpServletRequest request, HttpServletResponse response, Object targetController) throws IOException {
        return dispatch(request, response, null, null, targetController);
    }

    public Object dispatch(HttpServletRequest request, HttpServletResponse response, String overrideUri, String overrideMethod) throws IOException {
        return dispatch(request, response, overrideUri, overrideMethod, null);
    }

    public Object dispatch(HttpServletRequest request, HttpServletResponse response, String overrideUri, String overrideMethod, Object targetController) throws IOException {
        String normalizedUri = normalizeUri(request, overrideUri);
        String httpMethod = (overrideMethod != null ? overrideMethod : (request != null ? request.getMethod() : "GET")).toLowerCase();
        if ("head".equals(httpMethod)) {
            httpMethod = "get";
        }

        // 1. Database health check and automatic provisioning
        Map<String, Object> dbStatus = DatabaseProvisioner.checkAndProvision();
        if (!Boolean.TRUE.equals(dbStatus.get("ok"))) {
            return handleDatabaseError(request, response, dbStatus);
        }

        // 2. Route lookup
        RouteMatch match = matchRoute(normalizedUri);
        if (match == null) {
            return responseError(request, response, 404, "ERR_NOT_FOUND", "Rota '" + normalizedUri + "' não encontrada.");
        }

        if (request != null && !match.params.isEmpty()) {
            request.setAttribute("__ZTF_ROUTE_PARAMS__", match.params);
        }

        // 3. HTTP verb
        if (!match.methods.containsKey(httpMethod)) {
            if ("options".equals(httpMethod)) {
                return handlePreflight(request, response, match.methods);
            }
            return responseError(request, response, 405, "ERR_METHOD_NOT_ALLOWED",
                    "Método HTTP '" + httpMethod + "' não suportado para esta rota.");
        }

        Map<String, Object> routeConfig = match.methods.get(httpMethod);

        // 4. CORS
        handleCors(response, routeConfig.get("cors"), request != null ? request.getHeader("Origin") : null);

        // 5. CSRF: state-changing requests must carry the session token
        if (request != null && Csrf.requiresToken(httpMethod)
                && !Boolean.FALSE.equals(routeConfig.get("csrf")) && !Csrf.isValid(request)) {
            return JsonResponse.error(response, 403, "ERR_CSRF",
                    "Requisição recusada: token de segurança ausente ou inválido. Recarregue a página e tente novamente.");
        }

        // 6. Session ACL
        Map<String, Object> userSession = RequestHelper.getSessionUser(request);
        Map<String, Object> authCheck = checkAccessControl(routeConfig, userSession);
        if (!Boolean.TRUE.equals(authCheck.get("allowed"))) {
            int statusCode = Sanitize.intVal(authCheck.get("statusCode"), 403);
            String errorCode = String.valueOf(authCheck.getOrDefault("errorCode", "ERR_FORBIDDEN"));
            String message = String.valueOf(authCheck.getOrDefault("message", "Acesso negado."));
            return responseError(request, response, statusCode, errorCode, message);
        }

        // 7. Execution
        return executeRoute(request, response, routeConfig, match.params, targetController);
    }

    public Map<String, Object> checkAccessControl(Map<String, Object> routeConfig, Map<String, Object> userSession) {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<?, ?> sessionConfig = (routeConfig.get("session") instanceof Map<?, ?>)
                ? (Map<?, ?>) routeConfig.get("session")
                : Collections.emptyMap();

        boolean isAuthRequired = Boolean.TRUE.equals(sessionConfig.get("required"));

        if (isAuthRequired) {
            if (userSession == null || userSession.isEmpty()) {
                result.put("allowed", false);
                result.put("statusCode", 401);
                result.put("errorCode", "ERR_UNAUTHORIZED");
                result.put("message", "Autenticação necessária para acessar este recurso.");
                return result;
            }

            List<String> requiredKeys = toStringList(sessionConfig.get("keys"));
            if (requiredKeys.isEmpty()) {
                requiredKeys = Collections.singletonList("id_usuario");
            }

            for (String key : requiredKeys) {
                if (!userSession.containsKey(key) || userSession.get(key) == null) {
                    result.put("allowed", false);
                    result.put("statusCode", 401);
                    result.put("errorCode", "ERR_INVALID_SESSION");
                    result.put("message", "Sessão inválida: chave obrigatória '" + key + "' não encontrada.");
                    return result;
                }
            }

            // RN-AUT-05: with a temporary password, only routes marked "allowFirstAccess"
            // (password change, session and logout) are allowed.
            if (Sanitize.intVal(userSession.get("primeiro_acesso"), 0) == 1
                    && !Boolean.TRUE.equals(routeConfig.get("allowFirstAccess"))) {
                result.put("allowed", false);
                result.put("statusCode", 403);
                result.put("errorCode", ERR_PASSWORD_CHANGE_REQUIRED);
                result.put("message", "Troque a senha temporária antes de continuar.");
                return result;
            }
        }

        if (userSession != null && !userSession.isEmpty()) {
            List<String> userGroups = toStringList(userSession.get("grupos"));
            List<String> userPermissions = toStringList(userSession.get("permissoes"));
            int userId = Sanitize.intVal(userSession.get("id_usuario"), -1);

            boolean isSuperAdmin = userGroups.contains("admin");
            boolean strictGroup = Boolean.TRUE.equals(routeConfig.get("strictGroup"));

            if (!isSuperAdmin || strictGroup) {
                List<Integer> allowedUsers = toIntList(routeConfig.get("users"));
                if (!allowedUsers.isEmpty() && !allowedUsers.contains(userId)) {
                    result.put("allowed", false);
                    result.put("statusCode", 403);
                    result.put("errorCode", "ERR_FORBIDDEN_USER");
                    result.put("message", "Seu usuário não possui permissão para este recurso.");
                    return result;
                }

                List<String> requiredGroups = toStringList(routeConfig.get("groups"));
                if (!requiredGroups.isEmpty()) {
                    boolean hasGroup = false;
                    for (String rg : requiredGroups) {
                        if (userGroups.contains(rg)) {
                            hasGroup = true;
                            break;
                        }
                    }
                    if (!hasGroup) {
                        result.put("allowed", false);
                        result.put("statusCode", 403);
                        result.put("errorCode", "ERR_FORBIDDEN_GROUP");
                        result.put("message", "Você não pertence ao grupo autorizado para acessar esta funcionalidade.");
                        return result;
                    }
                }

                List<String> requiredPermissions = toStringList(routeConfig.get("permissions"));
                if (!requiredPermissions.isEmpty()) {
                    boolean hasPerm = false;
                    for (String rp : requiredPermissions) {
                        if (userPermissions.contains(rp)) {
                            hasPerm = true;
                            break;
                        }
                    }
                    if (!hasPerm) {
                        result.put("allowed", false);
                        result.put("statusCode", 403);
                        result.put("errorCode", "ERR_FORBIDDEN_PERMISSION");
                        result.put("message", "Você não possui as permissões necessárias para esta operação.");
                        return result;
                    }
                }
            }
        }

        result.put("allowed", true);
        return result;
    }

    private Object executeRoute(HttpServletRequest request, HttpServletResponse response,
                                Map<String, Object> routeConfig, Map<String, String> params,
                                Object targetController) throws IOException {

        try {
            // 1. Controller & action (through Controller.handle)
            if (routeConfig.containsKey("controller") && routeConfig.get("controller") != null) {
                String ctrlName = String.valueOf(routeConfig.get("controller"));
                String action = String.valueOf(routeConfig.getOrDefault("action", "index"));
                String fullClassName = ctrlName.contains(".") ? ctrlName : "controllers." + ctrlName;

                Object instance;
                Class<?> clazz;
                if (targetController != null && (targetController.getClass().getName().equals(fullClassName)
                        || targetController.getClass().getSimpleName().equals(ctrlName))) {
                    instance = targetController;
                    clazz = targetController.getClass();
                } else {
                    clazz = Class.forName(fullClassName);
                    instance = clazz.getDeclaredConstructor().newInstance();
                }

                try {
                    Method handleMethod = clazz.getMethod("handle", String.class, HttpServletRequest.class, HttpServletResponse.class);
                    return handleMethod.invoke(instance, action, request, response);
                } catch (NoSuchMethodException ignored) {
                }

                try {
                    Method m = clazz.getMethod(action, HttpServletRequest.class, HttpServletResponse.class);
                    return m.invoke(instance, request, response);
                } catch (NoSuchMethodException e) {
                    Method m = clazz.getMethod(action);
                    return m.invoke(instance);
                }
            }

            // 2. Declarative SQL query
            if (routeConfig.containsKey("query") && routeConfig.get("query") instanceof Map<?, ?>) {
                return executeDeclarativeQuery(request, response, (Map<?, ?>) routeConfig.get("query"));
            }

            // 3. Declarative DBQuery
            if (routeConfig.containsKey("dbquery") && routeConfig.get("dbquery") instanceof Map<?, ?>) {
                return executeDeclarativeDBQuery(response, (Map<?, ?>) routeConfig.get("dbquery"));
            }

            throw new RuntimeException("Nenhum manipulador válido definido para esta rota.");

        } catch (Exception e) {
            // Technical details go to the server log; the client only gets a tracking code
            // (no SQL, paths or class names are exposed).
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            String errorId = UUID.randomUUID().toString().substring(0, 8);
            LOG.log(Level.SEVERE, "Error " + errorId + " in " + normalizeUri(request, null), cause);
            return responseError(request, response, 500, "ERR_SERVER_ERROR",
                    "Erro interno no servidor (código " + errorId + ").");
        }
    }

    private String executeDeclarativeQuery(HttpServletRequest request, HttpServletResponse response, Map<?, ?> queryConfig) throws Exception {
        String sql = String.valueOf(queryConfig.get("sql") != null ? queryConfig.get("sql") : "");
        List<String> paramNames = toStringList(queryConfig.get("params"));
        Map<String, Object> requestData = RequestHelper.getRequestPayload(request);

        List<Map<String, Object>> data = new ArrayList<>();
        Connection conn = DBConnection.getStaticConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < paramNames.size(); i++) {
                ps.setObject(i + 1, requestData.get(paramNames.get(i)));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    data.add(row);
                }
            }
        }
        return JsonResponse.success(response, data, "Consulta executada com sucesso.");
    }

    private String executeDeclarativeDBQuery(HttpServletResponse response, Map<?, ?> dbqConfig) {
        String table = String.valueOf(dbqConfig.get("tableName") != null ? dbqConfig.get("tableName") : "");
        Object fieldsObj = dbqConfig.get("fieldsName") != null ? dbqConfig.get("fieldsName") : "*";
        String fieldsStr;
        if (fieldsObj instanceof List<?>) {
            fieldsStr = String.join(", ", toStringList(fieldsObj));
        } else {
            fieldsStr = String.valueOf(fieldsObj);
        }

        DBQuery dbQuery = new DBQuery(table, fieldsStr);
        Object whereObj = dbqConfig.get("where");
        if (whereObj instanceof Map<?, ?>) {
            Map<?, ?> whereMap = (Map<?, ?>) whereObj;
            for (Map.Entry<?, ?> entry : whereMap.entrySet()) {
                dbQuery.addCondition("AND", String.valueOf(entry.getKey()), "=", entry.getValue());
            }
        }

        List<Map<String, Object>> data = dbQuery.select();
        return JsonResponse.success(response, data, "Consulta DBQuery executada com sucesso.");
    }

    public Map<String, Object> checkDatabase() {
        return DatabaseProvisioner.checkAndProvision();
    }

    private Object handleDatabaseError(HttpServletRequest request, HttpServletResponse response, Map<String, Object> dbStatus) throws IOException {
        String errCode = String.valueOf(dbStatus.getOrDefault("error", "ERR_DATABASE_ERROR"));
        String msg = String.valueOf(dbStatus.getOrDefault("message", "Falha na conexão com o banco de dados."));
        if (response == null) {
            return JsonResponse.error(500, errCode, msg, dbStatus.get("details"));
        }
        return JsonResponse.error(response, 500, errCode, msg, dbStatus.get("details"));
    }

    private Object responseError(HttpServletRequest request, HttpServletResponse response,
                                 int statusCode, String errorCode, String message) throws IOException {
        return JsonResponse.error(response, statusCode, errorCode, message);
    }

    /**
     * Access-Control-Allow-Origin accepts ONE origin. Returns the request origin when it is in
     * the list, "*" when the list allows any origin, or null (no CORS) when the origin is not
     * allowed.
     */
    static String resolveAllowedOrigin(List<String> allowed, String requestOrigin) {
        if (allowed == null || allowed.isEmpty()) {
            return null;
        }
        if (requestOrigin != null && allowed.contains(requestOrigin)) {
            return requestOrigin;
        }
        return allowed.contains("*") ? "*" : null;
    }

    private void handleCors(HttpServletResponse response, Object corsConfigObj, String requestOrigin) {
        if (response == null) {
            return;
        }
        Map<?, ?> globalCors = (Config.get("cors") instanceof Map<?, ?>)
                ? (Map<?, ?>) Config.get("cors")
                : Collections.emptyMap();
        Map<?, ?> routeCors = (corsConfigObj instanceof Map<?, ?>)
                ? (Map<?, ?>) corsConfigObj
                : Collections.emptyMap();

        boolean enabled = routeCors.containsKey("enable")
                ? Boolean.TRUE.equals(routeCors.get("enable"))
                : Boolean.TRUE.equals(globalCors.get("default_enable"));

        if (!enabled) {
            return;
        }

        List<String> origins = toStringList(routeCors.containsKey("origins") ? routeCors.get("origins") : globalCors.get("allowed_origins"));
        String allowOrigin = resolveAllowedOrigin(origins, requestOrigin);
        if (allowOrigin == null) {
            return;
        }

        List<String> methods = toStringList(globalCors.get("allowed_methods"));
        if (methods.isEmpty()) methods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");

        List<String> headers = toStringList(globalCors.get("allowed_headers"));
        if (headers.isEmpty()) headers = Arrays.asList("Content-Type", "Authorization", "X-Requested-With", "Accept");

        response.setHeader("Access-Control-Allow-Origin", allowOrigin);
        if (!"*".equals(allowOrigin)) {
            response.setHeader("Vary", "Origin");
        }
        response.setHeader("Access-Control-Allow-Methods", String.join(", ", methods));
        response.setHeader("Access-Control-Allow-Headers", String.join(", ", headers));
    }

    /**
     * Preflight (OPTIONS): CORS is only enabled when the configuration allows it (globally or on
     * one of the URI's routes); an OPTIONS request never turns on "*" by itself.
     */
    private Object handlePreflight(HttpServletRequest request, HttpServletResponse response,
                                   Map<String, Map<String, Object>> methods) {
        Object routeCors = null;
        for (Map<String, Object> cfg : methods.values()) {
            if (cfg != null && cfg.get("cors") != null) {
                routeCors = cfg.get("cors");
                break;
            }
        }
        handleCors(response, routeCors, request != null ? request.getHeader("Origin") : null);
        if (response != null) {
            response.setStatus(204);
        }
        return true;
    }

    public String normalizeUri(HttpServletRequest request, String overrideUri) {
        String rawUri = overrideUri;
        if (rawUri == null && request != null) {
            rawUri = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && !"/".equals(contextPath) && rawUri.startsWith(contextPath)) {
                rawUri = rawUri.substring(contextPath.length());
            }
        }
        if (rawUri == null || rawUri.isEmpty()) {
            return "/";
        }
        int qIdx = rawUri.indexOf('?');
        if (qIdx >= 0) {
            rawUri = rawUri.substring(0, qIdx);
        }
        String clean = "/" + rawUri.replaceAll("^/+|/+$", "");
        return "//".equals(clean) ? "/" : clean;
    }

    private static List<String> toStringList(Object obj) {
        List<String> list = new ArrayList<>();
        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                if (item != null) list.add(String.valueOf(item));
            }
        } else if (obj instanceof String && !((String) obj).isEmpty()) {
            list.add((String) obj);
        }
        return list;
    }

    private static List<Integer> toIntList(Object obj) {
        List<Integer> list = new ArrayList<>();
        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                list.add(Sanitize.intVal(item, -1));
            }
        }
        return list;
    }

	public String getBasePath() {
		return basePath;
	}
}
