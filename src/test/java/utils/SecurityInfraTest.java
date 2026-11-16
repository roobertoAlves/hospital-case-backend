package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import database.DBConnection;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.FakeHttp;

/** Infraestrutura da Fase 4: cabecalhos, CORS, cache de rotas, storage e URL JDBC. */
class SecurityInfraTest {

    @TempDir
    Path tmp;

    @AfterEach
    void clear() {
        Config.reset();
        Router.clearRouteCache();
    }

    @Test
    void filterAppliesSecurityHeadersAndDisablesCaching() {
        FakeHttp.Response res = FakeHttp.response();
        SecurityHeadersFilter.apply(FakeHttp.request("GET", "/app/api/dashboard", null, null, null), res.proxy);

        assertEquals("nosniff", res.headers.get("X-Content-Type-Options"));
        assertEquals("DENY", res.headers.get("X-Frame-Options"));
        assertTrue(res.headers.get("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertTrue(res.headers.get("Content-Security-Policy").contains("default-src 'none'"));
        assertEquals("no-store", res.headers.get("Cache-Control"));
        assertNull(res.headers.get("Strict-Transport-Security"), "HSTS so em HTTPS");
    }

    @Test
    void corsReturnsSingleAllowedOrigin() {
        assertEquals("https://a.test", Router.resolveAllowedOrigin(List.of("https://a.test", "https://b.test"), "https://a.test"));
        assertNull(Router.resolveAllowedOrigin(List.of("https://a.test"), "https://evil.test"));
        assertEquals("*", Router.resolveAllowedOrigin(List.of("*"), "https://qualquer.test"));
        assertNull(Router.resolveAllowedOrigin(List.of(), "https://a.test"));
    }

    @Test
    void routeCacheIsNotPollutedByAddRoute() {
        Router first = new Router();
        first.addRoute("/route-only-in-this-test", "get", Map.of("controller", "AuthController", "action", "session"));
        assertNotNull(first.matchRoute("/route-only-in-this-test"));

        Router second = new Router();
        assertNull(second.matchRoute("/route-only-in-this-test"));
        assertNotNull(second.matchRoute("/api/auth/login"));
    }

    @Test
    void userQueryRoutesRequireAdmin() {
        Map<String, Object> route = new Router().matchRoute("/api/examples/declarative-query").methods.get("get");
        Map<String, Object> anonymous = new Router().checkAccessControl(route, null);
        assertFalse(Boolean.TRUE.equals(anonymous.get("allowed")));
        assertEquals(401, anonymous.get("statusCode"));
    }

    @Test
    void storageAcceptsAbsolutePathOutsideWebapp() throws Exception {
        java.nio.file.Files.writeString(tmp.resolve("config.json"),
                "{\"paths\":{\"storage\":\"" + tmp.resolve("dados").toString().replace("\\", "\\\\") + "\"}}");
        Config.setConfigPath(tmp.resolve("config.json").toString());

        File logs = Config.getStorageDir("logs");
        assertEquals(tmp.resolve("dados").resolve("logs").toFile().getAbsolutePath(), logs.getAbsolutePath());
        assertTrue(logs.isDirectory());
    }

    @Test
    void jdbcUrlUsesAppTimezoneAndMultiQueriesOnlyWhenProvisioning() {
        String app = DBConnection.mysqlUrl("localhost", 3306, "hospital", null, false);
        assertTrue(app.contains("connectionTimeZone=-03%3A00"), app);
        assertTrue(app.contains("forceConnectionTimeZoneToSession=true"));
        assertFalse(app.contains("allowMultiQueries"));
        assertFalse(app.contains("serverTimezone=UTC"));
        assertTrue(DBConnection.mysqlUrl("localhost", 3306, "hospital", "-03:00", true).contains("allowMultiQueries=true"));
    }
}
