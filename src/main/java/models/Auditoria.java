package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ZTF_J2EE - Model Auditoria
 *
 * Registro somente-inclusao de operacoes relevantes (RF-AUD-01, LGPD): logins, alteracoes
 * de senha e de permissoes e, nas proximas fases, cada leitura/alteracao de dado clinico.
 *
 * Regras:
 * - "detalhes" nunca deve conter conteudo clinico, senhas ou tokens;
 * - falha ao auditar e registrada no log do servidor e NAO interrompe a operacao principal;
 * - a tabela tem triggers que impedem UPDATE e DELETE.
 */
public final class Auditoria {

    private static final Logger LOG = Logger.getLogger(Auditoria.class.getName());
    private static final int MAX_DETALHES = 1000;

    private Auditoria() {
    }

    public static void registrar(Integer idUsuario, String acao, String entidade, Object idEntidade,
                                 Integer idPaciente, String detalhes, String ip) {
        try (Connection conn = DBConnection.openConnection()) {
            registrar(conn, idUsuario, acao, entidade, idEntidade, idPaciente, detalhes, ip);
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao registrar auditoria: " + acao, e);
        }
    }

    /**
     * Variante na conexao informada: permite auditar dentro da mesma transacao da operacao
     * (a auditoria so fica gravada se a operacao for confirmada) e testar com rollback.
     */
    public static void registrar(Connection conn, Integer idUsuario, String acao, String entidade, Object idEntidade,
                                 Integer idPaciente, String detalhes, String ip) {
        String sql = "INSERT INTO auditoria (id_usuario, acao, entidade, id_entidade, id_paciente, detalhes, ip) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setInt(ps, 1, idUsuario);
            ps.setString(2, acao);
            ps.setString(3, entidade);
            if (idEntidade instanceof Number) {
                ps.setLong(4, ((Number) idEntidade).longValue());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            setInt(ps, 5, idPaciente);
            ps.setString(6, truncar(detalhes, MAX_DETALHES));
            ps.setString(7, truncar(ip, 45));
            ps.executeUpdate();
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao registrar auditoria: " + acao, e);
        }
    }

    private static void setInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null || value <= 0) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static String truncar(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
