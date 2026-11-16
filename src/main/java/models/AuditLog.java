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
public final class AuditLog {

    private static final Logger LOG = Logger.getLogger(AuditLog.class.getName());
    private static final int MAX_DETAILS = 1000;

    private AuditLog() {
    }

    public static void record(Integer userId, String action, String entity, Object entityId,
                                 Integer patientId, String details, String ip) {
        try (Connection conn = DBConnection.openConnection()) {
            record(conn, userId, action, entity, entityId, patientId, details, ip);
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao registrar auditoria: " + action, e);
        }
    }

    /**
     * Variante na conexao informada: permite auditar dentro da mesma transacao da operacao
     * (a auditoria so fica gravada se a operacao for confirmada) e testar com rollback.
     */
    public static void record(Connection conn, Integer userId, String action, String entity, Object entityId,
                                 Integer patientId, String details, String ip) {
        String sql = "INSERT INTO auditoria (id_usuario, acao, entidade, id_entidade, id_paciente, detalhes, ip) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setInt(ps, 1, userId);
            ps.setString(2, action);
            ps.setString(3, entity);
            if (entityId instanceof Number) {
                ps.setLong(4, ((Number) entityId).longValue());
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            setInt(ps, 5, patientId);
            ps.setString(6, truncate(details, MAX_DETAILS));
            ps.setString(7, truncate(ip, 45));
            ps.executeUpdate();
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao registrar auditoria: " + action, e);
        }
    }

    private static void setInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null || value <= 0) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
