package support;

import database.DBQuery;
import database.DatabaseProvisioner;
import java.util.Map;
import models.Usuario;
import utils.Config;

/**
 * Apoio aos testes de integracao: verifica se o MySQL/MariaDB configurado (config.json +
 * config.local.json) esta acessivel e cria/remove usuarios ficticios descartaveis.
 * Os testes que dependem do banco usam Assumptions e sao ignorados quando ele nao responde.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static boolean disponivel() {
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
    public static Map<String, Object> criarUsuarioFicticio(String prefixo) {
        String email = prefixo + "." + System.nanoTime() + "@teste.hospital.local";
        Map<String, Object> res = new Usuario().cadastrarComSenhaAutomatica("Paciente Ficticio de Teste", email);
        if (!Boolean.TRUE.equals(res.get("status"))) {
            throw new IllegalStateException("Nao foi possivel criar usuario de teste: " + res.get("message"));
        }
        return Map.of(
                "id_usuario", res.get("id_usuario"),
                "email", email,
                "senha", res.get("senhaInicial"));
    }

    /**
     * Provisiona um banco temporario do zero (script.sql + migracoes) e passa a usa-lo.
     * Devolve o nome; chame descartarBancoTemporario(nome) no @AfterAll.
     */
    public static String usarBancoTemporario(String prefixo) {
        String nome = prefixo + "_" + Long.toString(System.nanoTime(), 36);
        database.DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        Config.set("database.connections.mysql.dbname", nome);
        Map<String, Object> status = DatabaseProvisioner.checkAndProvision(null, true);
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            throw new IllegalStateException("Falha ao provisionar banco temporario: " + status);
        }
        return nome;
    }

    public static void descartarBancoTemporario(String nome) throws java.sql.SQLException {
        database.DBConnection.closeAll();
        Config.reset();
        DatabaseProvisioner.resetState();
        try (java.sql.Connection c = database.DBConnection.openConnection();
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + nome + "`");
        }
    }

    public static void removerUsuario(Object idUsuario) {
        if (idUsuario != null) {
            new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", idUsuario).delete();
        }
    }
}
