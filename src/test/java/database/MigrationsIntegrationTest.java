package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestDatabase;
import utils.Config;

/** Numbered migrations applied once, in order, on a temporary database. */
class MigrationsIntegrationTest {

    @TempDir
    Path folder;

    private final String testDb = "hospital_mig_" + Long.toString(System.nanoTime(), 36);

    @BeforeEach
    void configure() {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        DBConnection.closeAll();
        Config.set("database.connections.mysql.dbname", testDb);
        Config.set("paths.migrations", folder.toString());
    }

    @AfterEach
    void remove() throws Exception {
        // Back to the default configuration before connecting: the temporary database may not exist.
        DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        try (Connection c = DBConnection.openConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + testDb + "`");
        }
    }

    private Map<String, Object> provision() {
        return DatabaseProvisioner.checkAndProvision(null, true);
    }

    private int count(String sql) throws Exception {
        try (Connection c = DBConnection.openConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void appliesInOrderOnceAndStopsAtFirstFailure() throws Exception {
        Files.writeString(folder.resolve("000_base.sql"), "-- so comentario\n");
        Files.writeString(folder.resolve("002_coluna.sql"), "ALTER TABLE teste_migracao ADD COLUMN obs VARCHAR(50) NULL;");
        Files.writeString(folder.resolve("001_tabela.sql"),
                "CREATE TABLE teste_migracao (id INT PRIMARY KEY);\nINSERT INTO teste_migracao (id) VALUES (1);");
        Files.writeString(folder.resolve("leia-me.txt"), "ignorado");

        Map<String, Object> first = provision();
        assertTrue(Boolean.TRUE.equals(first.get("ok")), String.valueOf(first));
        assertEquals(3, count("SELECT COUNT(*) FROM schema_migracoes"));
        assertEquals(1, count("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                + testDb + "' AND TABLE_NAME = 'teste_migracao' AND COLUMN_NAME = 'obs'"));

        // Second run: nothing is re-applied (the 001 INSERT would duplicate the PK)
        Map<String, Object> monday = provision();
        assertTrue(Boolean.TRUE.equals(monday.get("ok")), String.valueOf(monday));
        assertFalse(String.valueOf(monday.get("action")).contains("applied_migrations"));

        // Failing migration: the run stops and nothing is recorded for it
        Files.writeString(folder.resolve("003_quebrada.sql"), "ALTER TABLE tabela_que_nao_existe ADD COLUMN x INT;");
        Map<String, Object> third = provision();
        assertEquals(Boolean.FALSE, third.get("ok"));
        assertEquals("ERR_DATABASE_MIGRATION_FAILED", third.get("error"));
        assertEquals(3, count("SELECT COUNT(*) FROM schema_migracoes"));
    }
}
