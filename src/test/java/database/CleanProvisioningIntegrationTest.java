package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import support.TestDatabase;
import utils.Config;

/**
 * Fresh install: provisions the whole script.sql on a temporary database (unique name, created
 * and dropped by the test itself) and checks tables and triggers.
 */
class CleanProvisioningIntegrationTest {

    private final String temporaryDatabase = "hospital_teste_" + Long.toString(System.nanoTime(), 36);

    @AfterEach
    void removeTemporaryDatabase() throws Exception {
        try (Connection conn = DBConnection.openConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + temporaryDatabase + "`");
        } finally {
            DBConnection.closeAll();
            Config.reset();
            DatabaseProvisioner.resetState();
        }
    }

    @Test
    void scriptCreatesAllTablesAndTriggersOnEmptyDatabase() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        DBConnection.closeAll();
        Config.set("database.connections.mysql.dbname", temporaryDatabase);

        Map<String, Object> status = DatabaseProvisioner.checkAndProvision(null, true);

        assertTrue(Boolean.TRUE.equals(status.get("ok")), String.valueOf(status));
        assertEquals(List.of(), status.get("missingTables"));
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ?),"
                             + " (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'exames_agendados'),"
                             + " (SELECT COUNT(*) FROM `" + temporaryDatabase + "`.schema_migracoes)")) {
            ps.setString(1, temporaryDatabase);
            ps.setString(2, temporaryDatabase);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(12, rs.getInt(1), "triggers do script.sql (10) e da migracao 004 (2)");
                assertEquals(1, rs.getInt(2), "as migracoes tambem rodam numa instalacao do zero");
                assertEquals(Migrations.listFiles().size(), rs.getInt(3), "todas as migracoes registradas");
            }
        }
    }
}
