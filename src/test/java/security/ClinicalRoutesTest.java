package security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Locks the clinical routes in routes.json: they require a session, a clinical group with
 * strictGroup (the administrator stays out - decision P1) and a permission; writes are POST only
 * (the Router validates the CSRF token on every POST).
 */
class ClinicalRoutesTest {

    private static final List<String> PREFIXES = List.of("/api/triage", "/api/triages", "/api/encounters",
            "/api/prescriptions", "/api/exam-requests");

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> routes() throws Exception {
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
    }

    private static boolean clinical(String path) {
        return path.endsWith("/history") || PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void clinicalRoutesRequireStrictClinicalGroupAndPermission() throws Exception {
        int reviewed = 0;
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : routes().entrySet()) {
            if (!clinical(e.getKey())) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Map<String, Object> cfg = m.getValue();
                String route = m.getKey().toUpperCase() + " " + e.getKey();
                assertEquals(Boolean.TRUE, ((Map<String, Object>) cfg.get("session")).get("required"), route);
                List<String> groups = (List<String>) cfg.get("groups");
                assertFalse(groups == null || groups.isEmpty(), route + " without groups");
                assertFalse(groups.contains("admin"), route + " liberada ao admin");
                assertEquals(Boolean.TRUE, cfg.get("strictGroup"), route + " without strictGroup");
                assertFalse(((List<String>) cfg.get("permissions")).isEmpty(), route + " without permission");
                reviewed++;
            }
        }
        assertTrue(reviewed >= 18, "rotas clinicas conferidas: " + reviewed);
    }
}
