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
 * Portal do paciente: so o cadastro e o catalogo sao publicos; o resto exige o grupo
 * paciente com strictGroup e a permissao portal.paciente. E o grupo paciente nunca alcanca
 * rotas da equipe (as unicas compartilhadas sao as de horarios livres, sem dado pessoal).
 */
class RotasPortalTest {

    private static final Set<String> PUBLICAS = Set.of("/api/portal/cadastro", "/api/publico/catalogo");
    private static final Set<String> COMPARTILHADAS = Set.of("/api/agenda/medicos", "/api/agenda/horarios", "/api/exames/horarios", "/api/vacinas/horarios");

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> rotas() throws Exception {
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void rotasDoPortalExigemGrupoPacienteEstrito() throws Exception {
        int conferidas = 0;
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : rotas().entrySet()) {
            boolean portal = e.getKey().startsWith("/api/portal/") || e.getKey().startsWith("/api/publico/");
            if (!portal) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Map<String, Object> cfg = m.getValue();
                String nome = m.getKey() + " " + e.getKey();
                if (PUBLICAS.contains(e.getKey())) {
                    assertFalse(Boolean.TRUE.equals(((Map<String, Object>) cfg.get("session")).get("required")), nome);
                    continue;
                }
                assertEquals(List.of("paciente"), cfg.get("groups"), nome);
                assertEquals(Boolean.TRUE, cfg.get("strictGroup"), nome + ": admin nao entra no portal de um paciente");
                assertEquals(List.of("portal.paciente"), cfg.get("permissions"), nome);
                conferidas++;
            }
        }
        assertTrue(conferidas >= 18, "rotas do portal conferidas: " + conferidas);
    }

    @SuppressWarnings("unchecked")
    @Test
    void pacienteNaoAlcancaRotasDaEquipe() throws Exception {
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : rotas().entrySet()) {
            if (e.getKey().startsWith("/api/portal/") || COMPARTILHADAS.contains(e.getKey())) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Object perms = m.getValue().get("permissions");
                Object grupos = m.getValue().get("groups");
                assertFalse(perms instanceof List<?> && ((List<?>) perms).contains("portal.paciente"), m.getKey() + " " + e.getKey());
                assertFalse(grupos instanceof List<?> && ((List<?>) grupos).contains("paciente"), m.getKey() + " " + e.getKey());
            }
        }
    }
}
