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
 * Trava a configuracao das rotas clinicas no routes.json: exigem sessao, grupo clinico com
 * strictGroup (o administrador nao entra - decisao P1) e uma permissao; escrita so por POST
 * (o Router valida o token CSRF em todo POST).
 */
class RotasClinicasTest {

    private static final List<String> PREFIXOS = List.of("/triagem", "/api/triagem", "/api/triagens", "/atendimento",
            "/api/atendimento", "/receitas", "/api/receitas", "/api/solicitacoes-exame");

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> rotas() throws Exception {
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
    }

    private static boolean clinica(String path) {
        return path.endsWith("/historico") || PREFIXOS.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotasClinicasExigemGrupoClinicoEstritoEPermissao() throws Exception {
        int conferidas = 0;
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : rotas().entrySet()) {
            if (!clinica(e.getKey())) {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Map<String, Object> cfg = m.getValue();
                String rota = m.getKey().toUpperCase() + " " + e.getKey();
                assertEquals(Boolean.TRUE, ((Map<String, Object>) cfg.get("session")).get("required"), rota);
                List<String> grupos = (List<String>) cfg.get("groups");
                assertFalse(grupos == null || grupos.isEmpty(), rota + " sem grupos");
                assertFalse(grupos.contains("admin"), rota + " liberada ao admin");
                assertEquals(Boolean.TRUE, cfg.get("strictGroup"), rota + " sem strictGroup");
                assertFalse(((List<String>) cfg.get("permissions")).isEmpty(), rota + " sem permissao");
                if (e.getKey().startsWith("/api/") && !Boolean.TRUE.equals(cfg.get("jsonView"))) {
                    assertEquals("post", m.getKey(), rota + ": API clinica so altera por POST (GET so para leitura jsonView)");
                }
                if ("get".equals(m.getKey()) && e.getKey().startsWith("/api/")) {
                    assertEquals(Boolean.TRUE, cfg.get("jsonView"), rota + ": GET em /api/ clinica so como leitura de tela");
                }
                conferidas++;
            }
        }
        assertTrue(conferidas >= 19, "rotas clinicas conferidas: " + conferidas);
    }
}
