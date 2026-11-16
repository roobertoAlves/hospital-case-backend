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
 * Trava a configuracao das rotas de cadastro e governanca: sessao obrigatoria, a permissao
 * certa de cada modulo e escrita so por POST (com CSRF conferido pelo Router).
 */
class RotasAdministrativasTest {

    private static final Map<String, String> ESPERADAS = Map.ofEntries(
            Map.entry("get /profissionais", "profissionais.manage"),
            Map.entry("get /profissionais/novo", "profissionais.manage"),
            Map.entry("get /profissionais/{id}", "profissionais.manage"),
            Map.entry("post /api/profissionais", "profissionais.manage"),
            Map.entry("post /api/profissionais/{id}", "profissionais.manage"),
            Map.entry("post /api/profissionais/{id}/situacao", "profissionais.manage"),
            Map.entry("post /api/profissionais/{id}/disponibilidade", "agenda.disponibilidade.manage"),
            Map.entry("get /especialidades", "especialidades.manage"),
            Map.entry("post /api/especialidades", "especialidades.manage"),
            Map.entry("post /api/especialidades/{id}", "especialidades.manage"),
            Map.entry("post /api/especialidades/{id}/situacao", "especialidades.manage"),
            Map.entry("get /auditoria", "auditoria.view"),
            Map.entry("get /agenda/{id}/remarcar", "consultas.remarcar"),
            Map.entry("post /api/consultas/{id}/remarcar", "consultas.remarcar"));

    @SuppressWarnings("unchecked")
    @Test
    void cadaRotaExigeSessaoEAPermissaoDoModulo() throws Exception {
        Map<String, Map<String, Map<String, Object>>> rotas;
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            rotas = new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
        for (Map.Entry<String, String> e : ESPERADAS.entrySet()) {
            String[] mp = e.getKey().split(" ");
            Map<String, Map<String, Object>> porMetodo = rotas.get(mp[1]);
            assertNotNull(porMetodo, "rota ausente: " + e.getKey());
            for (Map.Entry<String, Map<String, Object>> outro : porMetodo.entrySet()) {
                if (!outro.getKey().equals(mp[0])) {
                    assertEquals("get", outro.getKey(), e.getKey() + ": escrita nunca por GET");
                    assertEquals(Boolean.TRUE, outro.getValue().get("jsonView"), e.getKey() + ": GET extra so como leitura de tela");
                }
            }
            Map<String, Object> cfg = porMetodo.get(mp[0]);
            assertNotNull(cfg, "metodo errado: " + e.getKey());
            assertEquals(Boolean.TRUE, ((Map<String, Object>) cfg.get("session")).get("required"), e.getKey());
            assertEquals(List.of(e.getValue()), cfg.get("permissions"), e.getKey());
        }
    }

    /** Permissoes usadas nas rotas existem na carga do banco (senao ninguem alem do admin entra). */
    @Test
    void permissoesDasRotasExistemNoScript() throws Exception {
        String sql = Files.readString(Path.of("src/main/webapp/WEB-INF/etc/script.sql"), StandardCharsets.UTF_8);
        for (String p : new java.util.HashSet<>(ESPERADAS.values())) {
            assertTrue(sql.contains("'" + p + "'"), "permissao sem carga: " + p);
        }
    }
}
