package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import utils.Sanitize;

public class Emergency {

    public static final int REDIRECT_UP_TO_ORDER = 2;

    public static Integer specialtyId() {
        Map<String, Object> e = Sql.one("SELECT id_especialidade FROM especialidades WHERE emergencia = 1 AND ativo = 1 "
                + "ORDER BY id_especialidade LIMIT 1");
        return e == null ? null : ((Number) e.get("id_especialidade")).intValue();
    }

    public static boolean isEmergencyDoctor(int doctorId) {
        return doctorId > 0 && Sql.one("SELECT 1 FROM medico_especialidades me JOIN especialidades e ON e.id_especialidade = me.id_especialidade "
                + "JOIN profissionais p ON p.id_profissional = me.id_profissional "
                + "WHERE me.id_profissional = ? AND e.emergencia = 1 AND e.ativo = 1 AND p.ativo = 1", doctorId) != null;
    }

    static Map<String, Object> pickDoctor(Connection conn) throws SQLException {
        return Sql.one(conn, "SELECT p.id_profissional, m.duracao_consulta_min, me.id_especialidade FROM profissionais p "
                + "JOIN medicos m ON m.id_profissional = p.id_profissional "
                + "JOIN medico_especialidades me ON me.id_profissional = p.id_profissional "
                + "JOIN especialidades e ON e.id_especialidade = me.id_especialidade AND e.emergencia = 1 AND e.ativo = 1 "
                + "WHERE p.ativo = 1 ORDER BY (SELECT COUNT(*) FROM consultas c WHERE c.id_medico = p.id_profissional "
                + "AND c.status IN ('aguardando_triagem', 'em_triagem', 'aguardando_atendimento', 'em_atendimento')), p.id_profissional LIMIT 1");
    }

    public Appointments.Result open(int patientId, String reason, int userId) {
        String r = Sanitize.string(reason).trim();
        if (r.length() < 3) {
            return Appointments.Result.failure("Descreva o que o paciente está sentindo.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                if (Sql.one(conn, "SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", patientId) == null) {
                    conn.rollback();
                    return Appointments.Result.failure("Paciente inativo ou inexistente.");
                }
                if (Sql.one(conn, "SELECT 1 FROM consultas WHERE id_paciente = ? AND status IN "
                        + "('aguardando_triagem', 'em_triagem', 'aguardando_atendimento', 'em_atendimento')", patientId) != null) {
                    conn.rollback();
                    return Appointments.Result.failure("O paciente já está na fila de atendimento.");
                }
                Map<String, Object> doctor = pickDoctor(conn);
                if (doctor == null) {
                    conn.rollback();
                    return Appointments.Result.failure("Nenhum médico de emergência ativo. Cadastre um plantonista na especialidade Emergência.");
                }
                long id = Sql.insert(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, duracao_min, tipo, "
                                + "motivo_emergencia, status, chegada_em, criado_por, atualizado_por) "
                                + "VALUES (?, ?, ?, ?, ?, 'emergencia', ?, 'aguardando_triagem', NOW(), ?, ?)",
                        patientId, doctor.get("id_profissional"), doctor.get("id_especialidade"), Appointments.now(),
                        doctor.get("duracao_consulta_min"), cut(r, 255), userId, userId);
                AuditLog.record(conn, userId, "emergencia.aberta", "consultas", id, patientId, null, null);
                conn.commit();
                return new Appointments.Result(true, id, null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the emergency visit", e);
        }
    }

    static boolean redirect(Connection conn, int appointmentId, String reason, int userId) throws SQLException {
        Map<String, Object> c = Sql.one(conn, "SELECT id_paciente, id_medico, tipo FROM consultas WHERE id_consulta = ? FOR UPDATE", appointmentId);
        if (c == null || "emergencia".equals(c.get("tipo"))) {
            return false;
        }
        Map<String, Object> doctor = pickDoctor(conn);
        if (doctor == null) {
            return false;
        }
        Sql.execute(conn, "UPDATE consultas SET medico_original = id_medico, id_medico = ?, id_especialidade = ?, tipo = 'emergencia', "
                        + "motivo_emergencia = ?, atualizado_por = ? WHERE id_consulta = ?",
                doctor.get("id_profissional"), doctor.get("id_especialidade"), cut(reason, 255), userId, appointmentId);
        AuditLog.record(conn, userId, "consulta.encaminhada_emergencia", "consultas", appointmentId,
                ((Number) c.get("id_paciente")).intValue(), "medico_original=" + c.get("id_medico"), null);
        return true;
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
