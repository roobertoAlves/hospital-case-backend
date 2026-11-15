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

    public static void removerUsuario(Object idUsuario) {
        if (idUsuario != null) {
            new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", idUsuario).delete();
        }
    }
}
