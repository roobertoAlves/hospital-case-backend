package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

public class Referral {

    private static final List<String> PRIORITIES = List.of("eletivo", "prioritario", "urgente");

    public record Result(long id, Map<String, String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public Result issue(int appointmentId, int doctorId, Map<String, Object> data, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> c = Sql.one("SELECT id_paciente FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", appointmentId, doctorId);
        if (c == null) {
            errors.put("general", "Só o médico do atendimento encaminha o paciente, durante ou logo após a consulta.");
            return new Result(0, errors);
        }
        int specialty = Sanitize.intVal(data.get("id_especialidade"), 0);
        if (Sql.one("SELECT 1 FROM especialidades WHERE id_especialidade = ? AND ativo = 1 AND emergencia = 0", specialty) == null) {
            errors.put("id_especialidade", "Escolha a especialidade de destino.");
        }
        String priority = Sanitize.string(data.get("prioridade")).trim();
        if (priority.isEmpty()) {
            priority = "eletivo";
        }
        if (!PRIORITIES.contains(priority)) {
            errors.put("prioridade", "Prioridade inválida.");
        }
        String reason = Sanitize.string(data.get("motivo")).trim();
        if (reason.length() < 5) {
            errors.put("motivo", "Explique o motivo do encaminhamento.");
        }
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO encaminhamentos (id_consulta, id_medico, id_especialidade, prioridade, motivo) "
                    + "VALUES (?, ?, ?, ?, ?)", appointmentId, doctorId, specialty, priority, reason.length() > 500 ? reason.substring(0, 500) : reason);
            AuditLog.record(conn, userId, "encaminhamento.emitido", "encaminhamentos", id, ((Number) c.get("id_paciente")).intValue(), null, null);
            return new Result(id, errors);
        } catch (SQLException e) {
            throw new RuntimeException("Could not issue the referral", e);
        }
    }

    public Result cancel(int referralId, int doctorId, String reason, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String m = Sanitize.string(reason).trim();
        if (m.length() < 3) {
            errors.put("motivo", "Informe o motivo do cancelamento.");
            return new Result(0, errors);
        }
        Map<String, Object> r = Sql.one("SELECT c.id_paciente FROM encaminhamentos x JOIN consultas c ON c.id_consulta = x.id_consulta "
                + "WHERE x.id_encaminhamento = ? AND x.id_medico = ? AND x.status = 'emitido'", referralId, doctorId);
        if (r == null) {
            errors.put("general", "Encaminhamento não encontrado, de outro médico, já agendado ou cancelado.");
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            Sql.execute(conn, "UPDATE encaminhamentos SET status = 'cancelado', cancelado_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_encaminhamento = ? AND status = 'emitido'", m.length() > 255 ? m.substring(0, 255) : m, referralId);
            AuditLog.record(conn, userId, "encaminhamento.cancelado", "encaminhamentos", referralId, ((Number) r.get("id_paciente")).intValue(), null, null);
            return new Result(referralId, errors);
        } catch (SQLException e) {
            throw new RuntimeException("Could not cancel the referral", e);
        }
    }

    private static final String COLUMNS = "x.id_encaminhamento, x.id_consulta, x.id_especialidade, e.nome AS especialidade, x.prioridade, "
            + "x.motivo, x.status, x.emitido_em, x.cancelado_em, x.motivo_cancelamento, x.id_consulta_agendada, "
            + "(SELECT ca.data_hora FROM consultas ca WHERE ca.id_consulta = x.id_consulta_agendada) AS agendada_para ";

    public List<Map<String, Object>> ofAppointment(int appointmentId) {
        return Sql.list("SELECT " + COLUMNS + "FROM encaminhamentos x JOIN especialidades e ON e.id_especialidade = x.id_especialidade "
                + "WHERE x.id_consulta = ? ORDER BY x.emitido_em DESC", appointmentId);
    }

    public List<Map<String, Object>> ofPatient(int patientId) {
        return Sql.list("SELECT " + COLUMNS + ", pr.nome AS medico FROM encaminhamentos x "
                + "JOIN especialidades e ON e.id_especialidade = x.id_especialidade "
                + "JOIN consultas c ON c.id_consulta = x.id_consulta JOIN profissionais pr ON pr.id_profissional = x.id_medico "
                + "WHERE c.id_paciente = ? ORDER BY x.emitido_em DESC", patientId);
    }

    public List<Map<String, Object>> openOfPatient(int patientId) {
        return Sql.list("SELECT " + COLUMNS + ", pr.nome AS medico FROM encaminhamentos x "
                + "JOIN especialidades e ON e.id_especialidade = x.id_especialidade "
                + "JOIN consultas c ON c.id_consulta = x.id_consulta JOIN profissionais pr ON pr.id_profissional = x.id_medico "
                + "WHERE c.id_paciente = ? AND x.status = 'emitido' ORDER BY FIELD(x.prioridade, 'urgente', 'prioritario', 'eletivo'), x.emitido_em",
                patientId);
    }

    static void markScheduled(Connection conn, int patientId, int specialtyId, long appointmentId) throws SQLException {
        Map<String, Object> open = Sql.one(conn, "SELECT x.id_encaminhamento FROM encaminhamentos x JOIN consultas c ON c.id_consulta = x.id_consulta "
                + "WHERE c.id_paciente = ? AND x.id_especialidade = ? AND x.status = 'emitido' ORDER BY x.emitido_em LIMIT 1", patientId, specialtyId);
        if (open != null) {
            Sql.execute(conn, "UPDATE encaminhamentos SET status = 'agendado', id_consulta_agendada = ? WHERE id_encaminhamento = ?",
                    appointmentId, open.get("id_encaminhamento"));
        }
    }
}
