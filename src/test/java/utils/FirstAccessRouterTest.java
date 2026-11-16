package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** RN-AUT-05: with a temporary password, only routes with "allowFirstAccess" are allowed. */
class FirstAccessRouterTest {

    private final Router router = new Router();
    private final Map<String, Object> temporaryUser = Map.of(
            "id_usuario", 99, "grupos", List.of("medico"), "permissoes", List.of(), "primeiro_acesso", 1);

    private Map<String, Object> route(String uri, String method) {
        return router.matchRoute(uri).methods.get(method);
    }

    @Test
    void regularProtectedRouteIsBlocked() {
        Map<String, Object> r = router.checkAccessControl(route("/api/dashboard", "get"), temporaryUser);
        assertEquals(Boolean.FALSE, r.get("allowed"));
        assertEquals(Router.ERR_PASSWORD_CHANGE_REQUIRED, r.get("errorCode"));
    }

    @Test
    void passwordChangeAndLogoutRemainAllowed() {
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(route("/api/auth/change-password", "post"), temporaryUser).get("allowed")));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(route("/api/auth/session", "get"), temporaryUser).get("allowed")));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(route("/api/auth/logout", "post"), temporaryUser).get("allowed")));
    }

    @Test
    void unlockRequiresSpecificPermission() {
        Map<String, Object> route = route("/api/admin/users/unlock", "post");
        Map<String, Object> nonAdmin = Map.of("id_usuario", 5, "grupos", List.of("recepcionista"), "permissoes", List.of("usuarios.unlock"));
        assertEquals(403, router.checkAccessControl(route, nonAdmin).get("statusCode"));
    }
}
