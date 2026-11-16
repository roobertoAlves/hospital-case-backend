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

/** Migracoes numeradas aplicadas uma unica vez, em ordem, num banco temporario. */
class MigracoesIntegrationTest {

    @TempDir
    Path pasta;

    private final String banco = "hospital_mig_" + Long.toString(System.nanoTime(), 36);

    @BeforeEach
    void configurar() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        DBConnection.closeAll();
        Config.set("database.connections.mysql.dbname", banco);
        Config.set("paths.migrations", pasta.toString());
    }

    @AfterEach
    void remover() throws Exception {
        // Volta para a configuracao padrao antes de conectar: o banco temporario pode nem existir.
        DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        try (Connection c = DBConnection.openConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + banco + "`");
        }
    }

    private Map<String, Object> provisionar() {
        return DatabaseProvisioner.checkAndProvision(null, true);
    }

    private int contar(String sql) throws Exception {
        try (Connection c = DBConnection.openConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void aplicaEmOrdemUmaUnicaVezEParaNaPrimeiraFalha() throws Exception {
        Files.writeString(pasta.resolve("000_base.sql"), "-- so comentario\n");
        Files.writeString(pasta.resolve("002_coluna.sql"), "ALTER TABLE teste_migracao ADD COLUMN obs VARCHAR(50) NULL;");
        Files.writeString(pasta.resolve("001_tabela.sql"),
                "CREATE TABLE teste_migracao (id INT PRIMARY KEY);\nINSERT INTO teste_migracao (id) VALUES (1);");
        Files.writeString(pasta.resolve("leia-me.txt"), "ignorado");

        Map<String, Object> primeira = provisionar();
        assertTrue(Boolean.TRUE.equals(primeira.get("ok")), String.valueOf(primeira));
        assertEquals(3, contar("SELECT COUNT(*) FROM schema_migracoes"));
        assertEquals(1, contar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '"
                + banco + "' AND TABLE_NAME = 'teste_migracao' AND COLUMN_NAME = 'obs'"));

        // Segunda execucao: nada e reaplicado (o INSERT da 001 duplicaria a PK)
        Map<String, Object> segunda = provisionar();
        assertTrue(Boolean.TRUE.equals(segunda.get("ok")), String.valueOf(segunda));
        assertFalse(String.valueOf(segunda.get("action")).contains("applied_migrations"));

        // Migracao com erro: aplicacao para, nada e registrado para ela
        Files.writeString(pasta.resolve("003_quebrada.sql"), "ALTER TABLE tabela_que_nao_existe ADD COLUMN x INT;");
        Map<String, Object> terceira = provisionar();
        assertEquals(Boolean.FALSE, terceira.get("ok"));
        assertEquals("ERR_DATABASE_MIGRATION_FAILED", terceira.get("error"));
        assertEquals(3, contar("SELECT COUNT(*) FROM schema_migracoes"));
    }
}
