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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Patient portal: only sign-up and the catalog are public; everything else requires the paciente
 * group with strictGroup and the portal.paciente permission. And the paciente group never reaches
 * staff routes (the only shared ones are free-slot lookups, with no personal data).
 */
class PortalRoutesTest {

    private static final Set<String> PUBLIC = Set.of("/api/portal/register", "/api/public/catalog");
    private static final Set<String> SHARED = Set.of("/api/appointments/doctors", "/api/appointments/slots", "/api/exams/slots", "/api/vaccines/slots");

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> routes() throws Exception {
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void portalRoutesRequireStrictPatientGroup() throws Exception {
        int reviewed = 0;
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : routes().entrySet()) {
            boolean portal = e.getKey().startsWith("/api/portal/") || e.getKey().startsWith("/api/public/");
            if (!portal) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Map<String, Object> cfg = m.getValue();
                String name = m.getKey() + " " + e.getKey();
                if (PUBLIC.contains(e.getKey())) {
                    assertFalse(Boolean.TRUE.equals(((Map<String, Object>) cfg.get("session")).get("required")), name);
                    continue;
                }
                assertEquals(List.of("paciente"), cfg.get("groups"), name);
                assertEquals(Boolean.TRUE, cfg.get("strictGroup"), name + ": admin does not enter a patient's portal");
                assertEquals(List.of("portal.paciente"), cfg.get("permissions"), name);
                reviewed++;
            }
        }
        assertTrue(reviewed >= 18, "rotas do portal conferidas: " + reviewed);
    }

    @SuppressWarnings("unchecked")
    @Test
    void patientCannotReachStaffRoutes() throws Exception {
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : routes().entrySet()) {
            if (e.getKey().startsWith("/api/portal/") || SHARED.contains(e.getKey())) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Object perms = m.getValue().get("permissions");
                Object groups = m.getValue().get("groups");
                assertFalse(perms instanceof List<?> && ((List<?>) perms).contains("portal.paciente"), m.getKey() + " " + e.getKey());
                assertFalse(groups instanceof List<?> && ((List<?>) groups).contains("paciente"), m.getKey() + " " + e.getKey());
            }
        }
    }
}
