package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.DBConnection;
import database.DBQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.FakeHttp;
import support.TestDatabase;

/** Fase 4: bloqueio por tentativas, troca de senha, recarga da sessao e auditoria. */
class HardenedLoginIntegrationTest {

    private Map<String, Object> user;
    private int id;
    private String email;
    private String password;

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(TestDatabase.available(), "Banco indisponivel");
    }

    @BeforeEach
    void create() {
        user = TestDatabase.createFakeUser("login");
        id = ((Number) user.get("id_usuario")).intValue();
        email = String.valueOf(user.get("email"));
        password = String.valueOf(user.get("senha"));
    }

    @AfterEach
    void remove() {
        TestDatabase.removeUser(id);
    }

    private User.LoginStatus attempt(String s) {
        return new User().authenticateDetailed(email, s).status();
    }

    @Test
    void fiveFailuresLockEvenWithCorrectPassword() {
        for (int i = 1; i <= LoginSecurity.maxFailures(); i++) {
            assertEquals(User.LoginStatus.INVALID_CREDENTIALS, attempt("errada" + i));
            boolean locked = LoginSecurity.recordFailure(id);
            assertEquals(i == LoginSecurity.maxFailures(), locked, "bloqueia exatamente na falha " + LoginSecurity.maxFailures());
        }
        assertEquals(User.LoginStatus.BLOCKED, attempt(password));

        LoginSecurity.unlock(id);
        assertEquals(User.LoginStatus.SUCCESS, attempt(password));
    }

    @Test
    void successfulLoginResetsFailures() {
        LoginSecurity.recordFailure(id);
        LoginSecurity.recordFailure(id);
        LoginSecurity.recordSuccess(id);
        for (int i = 1; i < LoginSecurity.maxFailures(); i++) {
            assertFalse(LoginSecurity.recordFailure(id), "contador deve ter recomecado do zero");
        }
    }

    @Test
    void unknownEmailAndInactiveUserGetSameResult() {
        User.LoginResult nonexistent = new User().authenticateDetailed("ninguem." + System.nanoTime() + "@hospital.local", "x");
        assertEquals(User.LoginStatus.INVALID_CREDENTIALS, nonexistent.status());
        assertNull(nonexistent.userId());

        new User().toggleStatus(id, 0);
        assertEquals(User.LoginStatus.INVALID_CREDENTIALS, attempt(password));
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndPolicyAndEndsFirstAccess() throws Exception {
        assertEquals("Senha atual incorreta.", new User().changePassword(id, "errada", "NovaSenha2026"));
        assertNotNull(new User().changePassword(id, password, "fraca"));
        // A senha temporaria (CodeGenerator, 10 caracteres com letras e digitos) cumpre a
        // politica; reutiliza-la deve ser recusado por ser igual a atual.
        assertEquals("A nova senha deve ser diferente da atual.", new User().changePassword(id, password, password));

        String token = new PasswordRecovery().request(email, "127.0.0.1").token();
        assertNull(new User().changePassword(id, password, "NovaSenha2026"));

        Map<String, Object> row = new DBQuery("usuarios", "primeiro_acesso").addCondition("AND", "id_usuario", "=", id).first();
        assertEquals(0, ((Number) row.get("primeiro_acesso")).intValue());
        assertEquals(User.LoginStatus.SUCCESS, attempt("NovaSenha2026"));
        assertFalse(new PasswordRecovery().validToken(token), "troca de senha invalida pedidos de recuperacao pendentes");
    }

    @Test
    void sessionWithOldVersionIsReloadedAndInactiveUserLosesSession() throws Exception {
        Map<String, Object> data = new User().loadSession(id);
        assertEquals(User.versionAcl(), data.get("acl_versao"));
        assertEquals(List.of("usuario"), data.get("grupos"));

        // Admin adiciona o grupo "medico" e a ACL muda de versao
        new User().syncUserGroups(id, List.of(2, 8));
        User.invalidateAcl();

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("user", data);
        HttpSession session = FakeHttp.session(attrs);
        controllers.SessionFilter.updateSession(FakeHttp.request("GET", "/app/dashboard", null, null, session));
        @SuppressWarnings("unchecked")
        Map<String, Object> reloaded = (Map<String, Object>) attrs.get("user");
        assertTrue(((List<?>) reloaded.get("grupos")).contains("medico"), "grupos recarregados sem novo login");

        new User().toggleStatus(id, 0);
        User.invalidateAcl();
        controllers.SessionFilter.updateSession(FakeHttp.request("GET", "/app/dashboard", null, null, session));
        assertEquals(Boolean.TRUE, attrs.get("__invalidada__"), "usuario inativado perde a sessao na hora");
    }

    @Test
    void auditLogRecordsWithoutInterruptingOnError() throws Exception {
        // Transacao desfeita no final: a auditoria e somente-inclusao (sem DELETE), entao o
        // teste nao pode deixar linhas no banco de desenvolvimento.
        String action = "teste.auditoria." + System.nanoTime();
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                AuditLog.record(conn, id, action, "usuarios", id, null, "detalhe de teste", "127.0.0.1");
                try (PreparedStatement ps = conn.prepareStatement("SELECT id_usuario, ip FROM auditoria WHERE acao = ?")) {
                    ps.setString(1, action);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(id, rs.getInt(1));
                        assertEquals("127.0.0.1", rs.getString(2));
                    }
                }
                // acao nula viola NOT NULL: deve apenas registrar no log, sem lancar excecao
                AuditLog.record(conn, id, null, null, null, null, null, null);
            } finally {
                conn.rollback();
            }
        }
    }
}
