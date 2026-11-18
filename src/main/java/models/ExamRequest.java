package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

public class ExamRequest {

    public record Result(boolean ok, long id, String error) {
    }

    private static final String SCHEDULING = "(SELECT a.data_hora FROM exames_agendados a WHERE a.id_solicitacao = e.id_exame "
            + "AND a.status <> 'cancelado' ORDER BY a.data_hora DESC LIMIT 1) AS agendado_para, "
            + "(SELECT a.status FROM exames_agendados a WHERE a.id_solicitacao = e.id_exame "
            + "AND a.status <> 'cancelado' ORDER BY a.data_hora DESC LIMIT 1) AS status_agendamento ";

    public Result request(int appointmentId, int doctorId, Map<String, Object> data, int userId) {
        int typeId = Sanitize.intVal(data.get("id_tipo_exame"), 0);
        String name;
        String code;
        Integer type = null;
        if (typeId > 0) {
            Map<String, Object> t = Sql.one("SELECT id_tipo_exame, nome, codigo FROM tipos_exame WHERE id_tipo_exame = ? AND ativo = 1", typeId);
            if (t == null) {
                return new Result(false, 0, "Exame não encontrado no catálogo.");
            }
            type = typeId;
            name = String.valueOf(t.get("nome"));
            code = t.get("codigo") == null ? null : String.valueOf(t.get("codigo"));
        } else {
            name = text(data.get("nome_exame"), 200);
            code = text(data.get("codigo"), 20);
            if (name == null || name.length() < 3) {
                return new Result(false, 0, "Escolha o exame no catálogo ou digite o nome de um exame fora dele.");
            }
        }
        Map<String, Object> c = Sql.one("SELECT id_paciente FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", appointmentId, doctorId);
        if (c == null) {
            return new Result(false, 0, "Só o médico do atendimento solicita exames, durante ou logo após a consulta.");
        }
        if (type != null && Sql.one("SELECT 1 FROM exames WHERE id_consulta = ? AND id_tipo_exame = ? AND status = 'solicitado'",
                appointmentId, type) != null) {
            return new Result(false, 0, "Este exame já foi solicitado nesta consulta.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO exames (id_consulta, id_medico, id_tipo_exame, nome_exame, codigo, justificativa, observacoes) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", appointmentId, doctorId, type, name, code,
                    text(data.get("justificativa"), 500), text(data.get("observacoes"), 500));
            AuditLog.record(conn, userId, "exame.solicitado", "exames", id, ((Number) c.get("id_paciente")).intValue(), null, null);
            return new Result(true, id, null);
        } catch (SQLException e) {
            throw new RuntimeException("Could not request the exam", e);
        }
    }

    public Result cancel(int examId, int doctorId, String reason, int userId) {
        String m = text(reason, 255);
        if (m == null || m.length() < 3) {
            return new Result(false, 0, "Informe o motivo do cancelamento.");
        }
        Map<String, Object> e = Sql.one("SELECT c.id_paciente FROM exames e JOIN consultas c ON c.id_consulta = e.id_consulta "
                + "WHERE e.id_exame = ? AND e.id_medico = ? AND e.status = 'solicitado'", examId, doctorId);
        if (e == null) {
            return new Result(false, 0, "Solicitação não encontrada, de outro médico ou já cancelada.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            Sql.execute(conn, "UPDATE exames SET status = 'cancelado', cancelado_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_exame = ? AND status = 'solicitado'", m, examId);
            AuditLog.record(conn, userId, "exame.cancelado", "exames", examId, ((Number) e.get("id_paciente")).intValue(), null, null);
            return new Result(true, examId, null);
        } catch (SQLException ex) {
            throw new RuntimeException("Could not cancel the exam", ex);
        }
    }

    public List<Map<String, Object>> ofAppointment(int appointmentId) {
        return Sql.list("SELECT e.id_exame, e.id_tipo_exame, e.nome_exame, e.codigo, e.justificativa, e.observacoes, e.status, "
                + "e.solicitado_em, e.motivo_cancelamento, " + SCHEDULING
                + "FROM exames e WHERE e.id_consulta = ? ORDER BY e.solicitado_em DESC, e.id_exame DESC", appointmentId);
    }

    public List<Map<String, Object>> ofPatient(int patientId) {
        return Sql.list("SELECT e.id_exame, e.id_tipo_exame, e.nome_exame, e.codigo, e.justificativa, e.status, e.solicitado_em, "
                + "pr.nome AS medico, " + SCHEDULING + "FROM exames e "
                + "JOIN consultas c ON c.id_consulta = e.id_consulta JOIN profissionais pr ON pr.id_profissional = e.id_medico "
                + "WHERE c.id_paciente = ? ORDER BY e.solicitado_em DESC", patientId);
    }

    public List<Map<String, Object>> catalog() {
        return Sql.list("SELECT t.id_tipo_exame, t.codigo, t.nome, t.categoria, t.preparo, t.exige_pedido, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 ORDER BY t.nome");
    }

    private static String text(Object v, int max) {
        String s = v == null ? "" : Sanitize.string(String.valueOf(v)).trim();
        return s.isEmpty() ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
