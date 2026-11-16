package security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import support.FakeHttp;
import support.TestDatabase;
import utils.Csrf;
import utils.Router;

/** CSRF: token sincronizado na sessao, validado pelo Router em requisicoes que alteram estado. */
class CsrfTest {

    private static HttpSession sessaoComToken(String token) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(Csrf.SESSION_ATTR, token);
        return FakeHttp.session(attrs);
    }

    @Test
    void metodosDeLeituraNaoExigemToken() {
        assertFalse(Csrf.requiresToken("GET"));
        assertFalse(Csrf.requiresToken("head"));
        assertTrue(Csrf.requiresToken("POST"));
        assertTrue(Csrf.requiresToken("delete"));
    }

    @Test
    void aceitaTokenCorretoNoCabecalhoOuNoParametro() {
        HttpSession s = sessaoComToken("abc123");
        assertTrue(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, "abc123"), null, s)));
        assertTrue(Csrf.isValid(FakeHttp.request("POST", "/app/x", null, Map.of(Csrf.PARAM, "abc123"), s)));
    }

    @Test
    void recusaTokenAusenteErradoOuSemSessao() {
        HttpSession s = sessaoComToken("abc123");
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", null, null, s)));
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, "abc124"), null, s)));
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, "abc123"), null, null)));
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, ""), null, sessaoComToken(""))));
    }

    @Test
    void rotacaoGeraTokenNovo() {
        HttpSession s = sessaoComToken("antigo");
        String novo = Csrf.rotate(s);
        assertNotEquals("antigo", novo);
        assertEquals(64, novo.length());
        assertEquals(novo, s.getAttribute(Csrf.SESSION_ATTR));
    }

    @Test
    void metaTagPublicaOTokenDaSessao() {
        HttpSession s = sessaoComToken("tok\"<x>");
        String meta = Csrf.metaTag(FakeHttp.request("GET", "/app/x", null, null, s));
        assertEquals("<meta name=\"csrf-token\" content=\"tok&quot;&lt;x&gt;\">", meta, "valor escapado");
    }

    @Test
    void routerRecusaPostSemTokenAntesDeExecutarAAcao() throws Exception {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel (o Router confere o provisionamento)");
        HttpSession s = sessaoComToken("token-valido");
        FakeHttp.Response res = FakeHttp.response();
        HttpServletRequest semToken = FakeHttp.request("POST", "/app/recuperar-senha",
                Map.of("X-Requested-With", "XMLHttpRequest"), Map.of("email", "x@hospital.local"), s);

        new Router().dispatch(semToken, res.proxy);

        assertEquals(403, res.status);
        assertTrue(res.body.toString().contains("ERR_CSRF"), res.body.toString());
    }
}
