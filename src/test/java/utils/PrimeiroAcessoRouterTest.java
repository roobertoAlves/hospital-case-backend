package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RN-AUT-05: com senha temporaria, so rotas com "allowFirstAccess" ficam liberadas. */
class PrimeiroAcessoRouterTest {

    private final Router router = new Router();
    private final Map<String, Object> usuarioTemporario = Map.of(
            "id_usuario", 99, "grupos", List.of("medico"), "permissoes", List.of(), "primeiro_acesso", 1);

    private Map<String, Object> rota(String uri, String metodo) {
        return router.matchRoute(uri).methods.get(metodo);
    }

    @Test
    void rotaProtegidaComumEhBloqueada() {
        Map<String, Object> r = router.checkAccessControl(rota("/dashboard", "get"), usuarioTemporario);
        assertEquals(Boolean.FALSE, r.get("allowed"));
        assertEquals(Router.ERR_PASSWORD_CHANGE_REQUIRED, r.get("errorCode"));
    }

    @Test
    void trocaDeSenhaELogoutContinuamLiberados() {
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(rota("/trocar-senha", "get"), usuarioTemporario).get("allowed")));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(rota("/trocar-senha", "post"), usuarioTemporario).get("allowed")));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(rota("/logout", "get"), usuarioTemporario).get("allowed")));
    }

    @Test
    void desbloqueioExigePermissaoEspecifica() {
        Map<String, Object> rota = rota("/api/admin/usuario/desbloquear", "post");
        Map<String, Object> naoAdmin = Map.of("id_usuario", 5, "grupos", List.of("recepcionista"), "permissoes", List.of("usuarios.unlock"));
        assertEquals(403, router.checkAccessControl(rota, naoAdmin).get("statusCode"));
    }
}
