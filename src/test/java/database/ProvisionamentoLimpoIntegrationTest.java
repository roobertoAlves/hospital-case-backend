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
 * Instalacao do zero: provisiona o script.sql completo num banco temporario
 * (nome unico, criado e removido pelo proprio teste) e confere tabelas e triggers.
 */
class ProvisionamentoLimpoIntegrationTest {

    private final String bancoTemporario = "hospital_teste_" + Long.toString(System.nanoTime(), 36);

    @AfterEach
    void removerBancoTemporario() throws Exception {
        try (Connection conn = DBConnection.openConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + bancoTemporario + "`");
        } finally {
            DBConnection.closeAll();
            Config.reset();
            DatabaseProvisioner.resetState();
        }
    }

    @Test
    void scriptCriaTodasAsTabelasETriggersEmBancoVazio() throws Exception {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        DBConnection.closeAll();
        Config.set("database.connections.mysql.dbname", bancoTemporario);

        Map<String, Object> status = DatabaseProvisioner.checkAndProvision(null, true);

        assertTrue(Boolean.TRUE.equals(status.get("ok")), String.valueOf(status));
        assertEquals(List.of(), status.get("missingTables"));
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = ?),"
                             + " (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'exames_agendados'),"
                             + " (SELECT COUNT(*) FROM `" + bancoTemporario + "`.schema_migracoes)")) {
            ps.setString(1, bancoTemporario);
            ps.setString(2, bancoTemporario);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(10, rs.getInt(1), "triggers do script.sql");
                assertEquals(1, rs.getInt(2), "as migracoes tambem rodam numa instalacao do zero");
                assertEquals(Migracoes.listarArquivos().size(), rs.getInt(3), "todas as migracoes registradas");
            }
        }
    }
}
