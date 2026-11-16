package security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.DBQuery;
import java.util.List;
import java.util.Map;
import models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import support.TestDatabase;

/**
 * SQL injection: values received from the user must be bound parameters (PreparedStatement),
 * never concatenated into the SQL.
 */
class SqlInjectionTest {

    private static final String[] PAYLOADS = {
            "' OR '1'='1",
            "admin@hospital.local' -- ",
            "x'; DROP TABLE usuarios; -- ",
            "\" OR \"\"=\"",
            "1 OR 1=1"
    };

    @Test
    void dbQueryKeepsValueOutOfSql() {
        for (String payload : PAYLOADS) {
            DBQuery q = new DBQuery("usuarios", "id_usuario").addCondition("AND", "email", "=", payload);
            String sql = q.getSelectSql();
            assertEquals("SELECT id_usuario FROM usuarios WHERE email = ?", sql);
            assertFalse(sql.contains(payload));
            assertEquals(List.of(payload), q.getBindings());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1", "admin@hospital.local' -- ", "admin@hospital.local' OR 1=1 -- "})
    void loginWithPayloadDoesNotAuthenticate(String payload) {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        assertNull(new User().authenticate(payload, "qualquer"));
        assertNull(new User().authenticate("admin@hospital.local", payload));
    }

    @Test
    void dropPayloadDoesNotDeleteTable() {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        new DBQuery("usuarios", "id_usuario")
                .addCondition("AND", "email", "=", "x'; DROP TABLE usuarios; -- ")
                .select();
        List<Map<String, Object>> admin = new DBQuery("usuarios", "id_usuario")
                .addCondition("AND", "email", "=", "admin@hospital.local")
                .select();
        assertEquals(1, admin.size(), "the usuarios table must still exist, intact");
    }
}
