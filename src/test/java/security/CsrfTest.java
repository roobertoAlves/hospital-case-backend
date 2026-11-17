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

class CsrfTest {

    private static HttpSession sessionWithToken(String token) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(Csrf.SESSION_ATTR, token);
        return FakeHttp.session(attrs);
    }

    @Test
    void readMethodsDoNotRequireToken() {
        assertFalse(Csrf.requiresToken("GET"));
        assertFalse(Csrf.requiresToken("head"));
        assertTrue(Csrf.requiresToken("POST"));
        assertTrue(Csrf.requiresToken("delete"));
    }

    @Test
    void acceptsCorrectTokenInHeaderOrParameter() {
        HttpSession s = sessionWithToken("abc123");
        assertTrue(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, "abc123"), null, s)));
        assertTrue(Csrf.isValid(FakeHttp.request("POST", "/app/x", null, Map.of(Csrf.PARAM, "abc123"), s)));
    }

    @Test
    void rejectsMissingWrongOrSessionlessToken() {
        HttpSession s = sessionWithToken("abc123");
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", null, null, s)));
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, "abc124"), null, s)));
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, "abc123"), null, null)));
        assertFalse(Csrf.isValid(FakeHttp.request("POST", "/app/x", Map.of(Csrf.HEADER, ""), null, sessionWithToken(""))));
    }

    @Test
    void rotationGeneratesNewToken() {
        HttpSession s = sessionWithToken("antigo");
        String rotated = Csrf.rotate(s);
        assertNotEquals("antigo", rotated);
        assertEquals(64, rotated.length());
        assertEquals(rotated, s.getAttribute(Csrf.SESSION_ATTR));
    }

    @Test
    void routerRejectsPostWithoutTokenBeforeRunningAction() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable (the Router checks provisioning)");
        HttpSession s = sessionWithToken("token-valido");
        FakeHttp.Response res = FakeHttp.response();
        HttpServletRequest withoutToken = FakeHttp.request("POST", "/app/api/auth/forgot-password",
                Map.of("X-Requested-With", "XMLHttpRequest"), Map.of("email", "x@hospital.local"), s);

        new Router().dispatch(withoutToken, res.proxy);

        assertEquals(403, res.status);
        assertTrue(res.body.toString().contains("ERR_CSRF"), res.body.toString());
    }
}
