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
class InfraSegurancaTest {

    @TempDir
    Path tmp;

    @AfterEach
    void limpar() {
        Config.reset();
        Router.clearRouteCache();
    }

    @Test
    void filtroAplicaCabecalhosDeSegurancaENaoCacheiaPaginas() {
        FakeHttp.Response res = FakeHttp.response();
        SecurityHeadersFilter.apply(FakeHttp.request("GET", "/app/dashboard", null, null, null), res.proxy);

        assertEquals("nosniff", res.headers.get("X-Content-Type-Options"));
        assertEquals("DENY", res.headers.get("X-Frame-Options"));
        assertTrue(res.headers.get("Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertTrue(res.headers.get("Content-Security-Policy").contains("object-src 'none'"));
        assertEquals("no-store", res.headers.get("Cache-Control"));
        assertNull(res.headers.get("Strict-Transport-Security"), "HSTS so em HTTPS");
    }

    @Test
    void arquivosEstaticosPodemSerCacheados() {
        FakeHttp.Response res = FakeHttp.response();
        SecurityHeadersFilter.apply(FakeHttp.request("GET", "/app/assets/css/style.css", null, null, null), res.proxy);
        assertNull(res.headers.get("Cache-Control"));
        assertEquals("nosniff", res.headers.get("X-Content-Type-Options"));
    }

    @Test
    void corsDevolveUmaUnicaOrigemPermitida() {
        assertEquals("https://a.test", Router.resolveAllowedOrigin(List.of("https://a.test", "https://b.test"), "https://a.test"));
        assertNull(Router.resolveAllowedOrigin(List.of("https://a.test"), "https://evil.test"));
        assertEquals("*", Router.resolveAllowedOrigin(List.of("*"), "https://qualquer.test"));
        assertNull(Router.resolveAllowedOrigin(List.of(), "https://a.test"));
    }

    @Test
    void cacheDeRotasNaoEContaminadoPorAddRoute() {
        Router primeiro = new Router();
        primeiro.addRoute("/rota-so-deste-teste", "get", Map.of("path", "x.jsp"));
        assertNotNull(primeiro.matchRoute("/rota-so-deste-teste"));

        Router segundo = new Router();
        assertNull(segundo.matchRoute("/rota-so-deste-teste"));
        assertNotNull(segundo.matchRoute("/login"));
    }

    @Test
    void rotasDeConsultaDeUsuariosExigemAdmin() {
        Map<String, Object> rota = new Router().matchRoute("/api/consulta-direta").methods.get("get");
        Map<String, Object> anonimo = new Router().checkAccessControl(rota, null);
        assertFalse(Boolean.TRUE.equals(anonimo.get("allowed")));
        assertEquals(401, anonimo.get("statusCode"));
    }

    @Test
    void storageAceitaCaminhoAbsolutoForaDoWebapp() throws Exception {
        java.nio.file.Files.writeString(tmp.resolve("config.json"),
                "{\"paths\":{\"storage\":\"" + tmp.resolve("dados").toString().replace("\\", "\\\\") + "\"}}");
        Config.setConfigPath(tmp.resolve("config.json").toString());

        File logs = Config.getStorageDir("logs");
        assertEquals(tmp.resolve("dados").resolve("logs").toFile().getAbsolutePath(), logs.getAbsolutePath());
        assertTrue(logs.isDirectory());
    }

    @Test
    void urlJdbcUsaFusoDaAplicacaoESoLigaMultiplosComandosNoProvisionamento() {
        String app = DBConnection.mysqlUrl("localhost", 3306, "hospital", null, false);
        assertTrue(app.contains("connectionTimeZone=-03%3A00"), app);
        assertTrue(app.contains("forceConnectionTimeZoneToSession=true"));
        assertFalse(app.contains("allowMultiQueries"));
        assertFalse(app.contains("serverTimezone=UTC"));
        assertTrue(DBConnection.mysqlUrl("localhost", 3306, "hospital", "-03:00", true).contains("allowMultiQueries=true"));
    }
}
