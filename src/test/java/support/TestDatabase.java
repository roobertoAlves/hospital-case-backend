package support;

import database.DBQuery;
import database.DatabaseProvisioner;
import java.util.Map;
import models.User;
import utils.Config;

/**
 * Integration test support: checks whether the configured MySQL/MariaDB (config.json +
 * config.local.json) is reachable and creates/removes disposable fictitious users. Tests that
 * need the database use Assumptions and are skipped when it does not answer.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static boolean available() {
        try {
            Config.reset();
            DatabaseProvisioner.resetState();
            Map<String, Object> status = DatabaseProvisioner.checkAndProvision();
            return Boolean.TRUE.equals(status.get("ok"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Creates an active fictitious user and returns {id_usuario, email, senha}. */
    public static Map<String, Object> createFakeUser(String prefix) {
        String email = prefix + "." + System.nanoTime() + "@teste.hospital.local";
        Map<String, Object> res = new User().registerWithAutomaticPassword("Paciente Ficticio de Teste", email);
        if (!Boolean.TRUE.equals(res.get("status"))) {
            throw new IllegalStateException("Could not create the test user: " + res.get("message"));
        }
        return Map.of(
                "id_usuario", res.get("id_usuario"),
                "email", email,
                "password", res.get("initialPassword"));
    }

    /**
     * Provisions a temporary database from scratch (script.sql + migrations) and switches to it.
     * Returns its name; call discardTemporaryDatabase(name) in @AfterAll.
     */
    public static String useTemporaryDatabase(String prefix) {
        String name = prefix + "_" + Long.toString(System.nanoTime(), 36);
        database.DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        Config.set("database.connections.mysql.dbname", name);
        Map<String, Object> status = DatabaseProvisioner.checkAndProvision(null, true);
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            throw new IllegalStateException("Could not provision the temporary database: " + status);
        }
        return name;
    }

    public static void discardTemporaryDatabase(String name) throws java.sql.SQLException {
        database.DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        try (java.sql.Connection c = database.DBConnection.openConnection();
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + name + "`");
        }
    }

    public static void removeUser(Object userId) {
        if (userId != null) {
            new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", userId).delete();
        }
    }
}
