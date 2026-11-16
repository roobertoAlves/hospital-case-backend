package utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

/**
 * ZTF_J2EE - RequestHelper
 *
 * Reads parameters from the query string, URL-encoded forms, multipart form data and JSON bodies
 * through a single API.
 */
public class RequestHelper {

    private static final String CACHED_PARAMS_ATTR = "__ZTF_CACHED_PARAMS__";
    private static final Gson GSON = new Gson();

    public static Map<String, Object> getRequestPayload(HttpServletRequest request) {
        if (request == null) {
            return new LinkedHashMap<>();
        }

        Object cached = request.getAttribute(CACHED_PARAMS_ATTR);
        if (cached instanceof Map<?, ?>) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) cached).entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return copy;
        }

        Map<String, Object> payload = new LinkedHashMap<>();

        // 1. Parametros padrao (GET / POST urlencoded)
        try {
            Map<String, String[]> paramMap = request.getParameterMap();
            if (paramMap != null) {
                for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                    String key = entry.getKey();
                    String[] vals = entry.getValue();
                    if (vals != null) {
                        if (key.endsWith("[]")) {
                            String cleanKey = key.substring(0, key.length() - 2);
                            List<String> list = new ArrayList<>();
                            for (String v : vals) list.add(v);
                            payload.put(cleanKey, list);
                        } else if (vals.length == 1) {
                            payload.put(key, vals[0]);
                        } else if (vals.length > 1) {
                            List<String> list = new ArrayList<>();
                            for (String v : vals) list.add(v);
                            payload.put(key, list);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Multipart FormData (ZTF.ajax.submitForm)
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            try {
                Collection<Part> parts = request.getParts();
                if (parts != null) {
                    for (Part part : parts) {
                        String name = part.getName();
                        if (part.getSubmittedFileName() == null && part.getSize() < 10 * 1024 * 1024) {
                            try (InputStream is = part.getInputStream()) {
                                String val = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                                payload.put(name, val);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. JSON body (application/json, or raw as a fallback)
        if ((contentType != null && contentType.toLowerCase().contains("application/json")) || payload.isEmpty()) {
            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader reader = request.getReader();
                if (reader != null) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                String raw = sb.toString().trim();
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    Type type = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();
                    Map<String, Object> jsonMap = GSON.fromJson(raw, type);
                    if (jsonMap != null) {
                        payload.putAll(jsonMap);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 4. Dynamic route attributes (e.g. {hash})
        Object routeParams = request.getAttribute("__ZTF_ROUTE_PARAMS__");
        if (routeParams instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) routeParams).entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    payload.putIfAbsent(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        }

        request.setAttribute(CACHED_PARAMS_ATTR, payload);
        return payload;
    }

    public static String getParam(HttpServletRequest request, String key, String defaultValue) {
        Map<String, Object> payload = getRequestPayload(request);
        Object val = payload.get(key);
        if (val == null && request != null) {
            val = request.getParameter(key);
        }
        return val != null ? String.valueOf(val) : defaultValue;
    }

    public static String getParam(HttpServletRequest request, String key) {
        return getParam(request, key, "");
    }

    public static Map<String, Object> getSessionUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute("user");
        if (user instanceof Map<?, ?>) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) user).entrySet()) {
                if (entry.getKey() != null) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return map;
        }
        return null;
    }

    public static int getSessionUserId(HttpServletRequest request) {
        Map<String, Object> user = getSessionUser(request);
        if (user == null) {
            return 0;
        }
        return Sanitize.intVal(user.get("id_usuario"), 0);
    }

    public static List<String> getSessionUserGroups(HttpServletRequest request) {
        Map<String, Object> user = getSessionUser(request);
        if (user == null) {
            return new ArrayList<>();
        }
        Object g = user.get("grupos");
        if (g instanceof List<?>) {
            List<String> res = new ArrayList<>();
            for (Object item : (List<?>) g) {
                if (item != null) res.add(String.valueOf(item));
            }
            return res;
        }
        return new ArrayList<>();
    }

    public static Map<String, String> getRouteParams(HttpServletRequest request) {
        if (request == null) {
            return new LinkedHashMap<>();
        }
        Object routeParams = request.getAttribute("__ZTF_ROUTE_PARAMS__");
        if (routeParams instanceof Map<?, ?>) {
            Map<String, String> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) routeParams).entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
            return map;
        }
        return new LinkedHashMap<>();
    }
}
