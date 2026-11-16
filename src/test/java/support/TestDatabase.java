package support;

import database.DBQuery;
import database.DatabaseProvisioner;
import java.util.Map;
import models.User;
import utils.Config;

/**
 * Apoio aos testes de integracao: verifica se o MySQL/MariaDB configurado (config.json +
 * config.local.json) esta acessivel e cria/remove usuarios ficticios descartaveis.
 * Os testes que dependem do banco usam Assumptions e sao ignorados quando ele nao responde.
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

    /** Cria um usuario ficticio ativo e devolve {id_usuario, email, senha}. */
    public static Map<String, Object> createFakeUser(String prefix) {
        String email = prefix + "." + System.nanoTime() + "@teste.hospital.local";
        Map<String, Object> res = new User().registerWithAutomaticPassword("Paciente Ficticio de Teste", email);
        if (!Boolean.TRUE.equals(res.get("status"))) {
            throw new IllegalStateException("Nao foi possivel criar usuario de teste: " + res.get("message"));
        }
        return Map.of(
                "id_usuario", res.get("id_usuario"),
                "email", email,
                "senha", res.get("initialPassword"));
    }

    /**
     * Provisiona um banco temporario do zero (script.sql + migracoes) e passa a usa-lo.
     * Devolve o nome; chame descartarBancoTemporario(nome) no @AfterAll.
     */
    public static String useTemporaryDatabase(String prefix) {
        String name = prefix + "_" + Long.toString(System.nanoTime(), 36);
        database.DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        Config.set("database.connections.mysql.dbname", name);
        Map<String, Object> status = DatabaseProvisioner.checkAndProvision(null, true);
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            throw new IllegalStateException("Falha ao provisionar banco temporario: " + status);
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
