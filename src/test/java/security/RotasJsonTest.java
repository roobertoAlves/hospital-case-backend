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
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * Rotas de leitura para o frontend separado ("jsonView": true) reaproveitam a acao de uma tela
 * JSP. Este teste trava que cada uma tem as MESMAS regras de acesso da tela correspondente
 * (sessao, grupos, strictGroup e permissoes): a API nunca fica mais aberta que a tela.
 */
class RotasJsonTest {

    @SuppressWarnings("unchecked")
    @Test
    void leituraJsonTemAsMesmasRegrasDaTela() throws Exception {
        Map<String, Map<String, Map<String, Object>>> rotas;
        try (Reader r = Files.newBufferedReader(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8)) {
            rotas = new Gson().fromJson(r, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() { }.getType());
        }
        List<String> conferidas = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> e : rotas.entrySet()) {
            for (Map.Entry<String, Map<String, Object>> m : e.getValue().entrySet()) {
                Map<String, Object> api = m.getValue();
                if (!Boolean.TRUE.equals(api.get("jsonView"))) {
                    continue;
                }
                String nome = m.getKey().toUpperCase() + " " + e.getKey();
                assertEquals("get", m.getKey(), nome + ": jsonView e so leitura");
                assertTrue(e.getKey().startsWith("/api/"), nome + ": jsonView fica sob /api/");
                assertEquals("json", api.get("responseType"), nome);
                Map<String, Object> tela = telaDaMesmaAcao(rotas, api);
                assertTrue(tela != null, nome + ": sem tela HTML com a mesma acao");
                assertEquals(tela.get("session"), api.get("session"), nome + ": sessao");
                assertEquals(tela.get("groups"), api.get("groups"), nome + ": grupos");
                assertEquals(tela.get("strictGroup"), api.get("strictGroup"), nome + ": strictGroup");
                assertEquals(tela.get("permissions"), api.get("permissions"), nome + ": permissoes");
                conferidas.add(nome);
            }
        }
        assertTrue(conferidas.size() >= 19, "rotas jsonView conferidas: " + conferidas);
    }

    private static Map<String, Object> telaDaMesmaAcao(Map<String, Map<String, Map<String, Object>>> rotas,
                                                       Map<String, Object> api) {
        for (Map<String, Map<String, Object>> metodos : rotas.values()) {
            Map<String, Object> get = metodos.get("get");
            if (get != null && "html".equals(get.get("responseType"))
                    && Objects.equals(get.get("controller"), api.get("controller"))
                    && Objects.equals(get.get("action"), api.get("action"))) {
                return get;
            }
        }
        return null;
    }
}
