package security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

/**
 * The backend is API-only: every route lives under /api/, points to an existing controller action
 * (or a declarative query) and, apart from the few public endpoints, requires a session.
 */
class ApiRoutesTest {

    private static final Set<String> PUBLIC = Set.of(
            "POST /api/auth/login", "POST /api/auth/register", "POST /api/auth/forgot-password",
            "GET /api/auth/reset-password", "POST /api/auth/reset-password", "GET /api/auth/session",
            "POST /api/auth/logout", "GET /api/public/catalog", "POST /api/portal/register",
            "GET /api/files/{hash}"); // file downloads check their own ACL (public files have no owner session)

    private static Map<String, Map<String, Map<String, Object>>> routes() throws Exception {
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyRouteIsAnApiRouteWithAnExistingAction() throws Exception {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : routes().entrySet()) {
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                String name = m.getKey().toUpperCase() + " " + e.getKey();
                Map<String, Object> cfg = m.getValue();
                checked++;
                if (!e.getKey().startsWith("/api/")) {
                    problems.add(name + ": outside /api/");
                }
                if (cfg.containsKey("path") || "html".equals(cfg.get("responseType"))) {
                    problems.add(name + ": HTML page route");
                }
                Object controller = cfg.get("controller");
                if (controller == null) {
                    if (!cfg.containsKey("query") && !cfg.containsKey("dbquery")) {
                        problems.add(name + ": no controller and no declarative query");
                    }
                } else {
                    try {
                        Class.forName("controllers." + controller)
                                .getMethod(String.valueOf(cfg.get("action")), HttpServletRequest.class, HttpServletResponse.class);
                    } catch (ReflectiveOperationException ex) {
                        problems.add(name + ": " + controller + "." + cfg.get("action") + " not found");
                    }
                }
                Map<String, Object> session = (Map<String, Object>) cfg.get("session");
                boolean required = session != null && Boolean.TRUE.equals(session.get("required"));
                if (!required && !PUBLIC.contains(name)) {
                    problems.add(name + ": public route not in the allow list");
                }
            }
        }
        assertEquals(List.of(), problems);
        assertTrue(checked >= 100, "routes checked: " + checked);
    }
}
