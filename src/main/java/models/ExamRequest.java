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

    public Result request(int appointmentId, int doctorId, Map<String, Object> data, int userId) {
        String name = text(data.get("nome_exame"), 200);
        if (name == null || name.length() < 3) {
            return new Result(false, 0, "Informe o exame.");
        }
        Map<String, Object> c = Sql.one("SELECT id_paciente FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", appointmentId, doctorId);
        if (c == null) {
            return new Result(false, 0, "Só o médico do atendimento solicita exames, durante ou logo após a consulta.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO exames (id_consulta, id_medico, nome_exame, codigo, justificativa, observacoes) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", appointmentId, doctorId, name, text(data.get("codigo"), 20),
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
        return Sql.list("SELECT id_exame, nome_exame, codigo, justificativa, observacoes, status, solicitado_em, motivo_cancelamento "
                + "FROM exames WHERE id_consulta = ? ORDER BY solicitado_em DESC, id_exame DESC", appointmentId);
    }

    public List<Map<String, Object>> ofPatient(int patientId) {
        return Sql.list("SELECT e.id_exame, e.nome_exame, e.status, e.solicitado_em, pr.nome AS medico FROM exames e "
                + "JOIN consultas c ON c.id_consulta = e.id_consulta JOIN profissionais pr ON pr.id_profissional = e.id_medico "
                + "WHERE c.id_paciente = ? ORDER BY e.solicitado_em DESC", patientId);
    }

    private static String text(Object v, int max) {
        String s = v == null ? "" : Sanitize.string(String.valueOf(v)).trim();
        return s.isEmpty() ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
