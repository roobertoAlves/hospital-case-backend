package security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Locks the registry and governance routes: session required and the right permission for each
 * module (writes are POST only, with CSRF checked by the Router).
 */
class AdminRoutesTest {

    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("get /api/professionals", "profissionais.manage"),
            Map.entry("get /api/professionals/options", "profissionais.manage"),
            Map.entry("get /api/professionals/{id}", "profissionais.manage"),
            Map.entry("post /api/professionals", "profissionais.manage"),
            Map.entry("post /api/professionals/{id}", "profissionais.manage"),
            Map.entry("post /api/professionals/{id}/status", "profissionais.manage"),
            Map.entry("post /api/professionals/{id}/availability", "agenda.disponibilidade.manage"),
            Map.entry("get /api/specialties", "especialidades.manage"),
            Map.entry("post /api/specialties", "especialidades.manage"),
            Map.entry("post /api/specialties/{id}", "especialidades.manage"),
            Map.entry("post /api/specialties/{id}/status", "especialidades.manage"),
            Map.entry("get /api/audit-log", "auditoria.view"),
            Map.entry("get /api/audit-log/notifications", "auditoria.view"),
            Map.entry("get /api/appointments/{id}/reschedule", "consultas.remarcar"),
            Map.entry("post /api/appointments/{id}/reschedule", "consultas.remarcar"));

    @SuppressWarnings("unchecked")
    @Test
    void eachRouteRequiresSessionAndModulePermission() throws Exception {
        Map<String, Map<String, Map<String, Object>>> routes;
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            routes = new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            String[] mp = e.getKey().split(" ");
            Map<String, Map<String, Object>> byMethod = routes.get(mp[1]);
            assertNotNull(byMethod, "missing route: " + e.getKey());
            Map<String, Object> cfg = byMethod.get(mp[0]);
            assertNotNull(cfg, "wrong method: " + e.getKey());
            assertEquals(Boolean.TRUE, ((Map<String, Object>) cfg.get("session")).get("required"), e.getKey());
            assertEquals(List.of(e.getValue()), cfg.get("permissions"), e.getKey());
        }
    }

    /** Permissoes usadas nas rotas existem na carga do banco (senao ninguem alem do admin entra). */
    @Test
    void routePermissionsExistInScript() throws Exception {
        String sql = Files.readString(Path.of("src/main/webapp/WEB-INF/etc/script.sql"), StandardCharsets.UTF_8);
        for (String p : new java.util.HashSet<>(EXPECTED.values())) {
            assertTrue(sql.contains("'" + p + "'"), "permissao sem carga: " + p);
        }
    }
}
