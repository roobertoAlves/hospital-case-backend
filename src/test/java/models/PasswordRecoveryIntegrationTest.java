package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.DBQuery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestDatabase;
import utils.PasswordSecurity;

/** MySQL/MariaDB integration: the full password recovery flow with a token. */
class PasswordRecoveryIntegrationTest {

    private final PasswordRecovery recovery = new PasswordRecovery();
    private Map<String, Object> user;

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(TestDatabase.available(), "Database unavailable: integration tests skipped");
    }

    @BeforeEach
    void createUser() {
        user = TestDatabase.createFakeUser("recuperacao");
    }

    @AfterEach
    void removeUser() {
        if (user != null) {
            TestDatabase.removeUser(user.get("id_usuario"));
        }
    }

    private String email() {
        return String.valueOf(user.get("email"));
    }

    @Test
    void unknownEmailCreatesNoRequest() throws Exception {
        assertNull(recovery.request("ninguem." + System.nanoTime() + "@hospital.local", "127.0.0.1"));
    }

    @Test
    void requestDoesNotChangeCurrentPassword() throws Exception {
        assertNotNull(recovery.request(email(), "127.0.0.1"));
        assertNotNull(new User().authenticate(email(), String.valueOf(user.get("password"))),
                "the old password keeps working until the token is used");
    }

    @Test
    void databaseStoresOnlyTokenHash() throws Exception {
        PasswordRecovery.ResetRequest order = recovery.request(email(), "127.0.0.1");
        List<Map<String, Object>> rows = new DBQuery("recuperacoes_senha", "token_hash")
                .addCondition("AND", "id_usuario", "=", user.get("id_usuario"))
                .select();
        assertEquals(1, rows.size());
        String stored = String.valueOf(rows.get(0).get("token_hash"));
        assertFalse(stored.equals(order.token()), "the clear-text token must not be stored");
        assertEquals(PasswordSecurity.sha256Hex(order.token()), stored);
    }

    @Test
    void tokenResetsPasswordOnlyOnce() throws Exception {
        String token = recovery.request(email(), "127.0.0.1").token();
        assertTrue(recovery.validToken(token));

        assertTrue(recovery.reset(token, "NovaSenha2026") > 0);
        assertNotNull(new User().authenticate(email(), "NovaSenha2026"));
        assertNull(new User().authenticate(email(), String.valueOf(user.get("password"))));

        assertFalse(recovery.validToken(token), "a consumed token is no longer valid");
        assertEquals(0, recovery.reset(token, "OutraSenha2026"), "a token cannot be reused");
    }

    @Test
    void newRequestInvalidatesPrevious() throws Exception {
        String first = recovery.request(email(), "127.0.0.1").token();
        String second = recovery.request(email(), "127.0.0.1").token();
        assertFalse(recovery.validToken(first));
        assertTrue(recovery.validToken(second));
    }

    @Test
    void tokenExpiredAndRejected() throws Exception {
        String token = recovery.request(email(), "127.0.0.1").token();
        try (var conn = database.DBConnection.openConnection();
             var ps = conn.prepareStatement(
                     "UPDATE recuperacoes_senha SET expira_em = NOW() - INTERVAL 1 MINUTE WHERE id_usuario = ?")) {
            ps.setObject(1, user.get("id_usuario"));
            ps.executeUpdate();
        }
        assertFalse(recovery.validToken(token));
        assertEquals(0, recovery.reset(token, "NovaSenha2026"));
    }

    @Test
    void inactiveUserGetsNoToken() throws Exception {
        new User().toggleStatus(((Number) user.get("id_usuario")).intValue(), 0);
        assertNull(recovery.request(email(), "127.0.0.1"));
    }

    @Test
    void malformedTokensAreRejectedWithoutQueryingDatabase() throws Exception {
        assertFalse(recovery.validToken("' OR '1'='1"));
        assertEquals(0, recovery.reset("' OR '1'='1", "NovaSenha2026"));
        assertFalse(recovery.validToken(null));
    }
}
