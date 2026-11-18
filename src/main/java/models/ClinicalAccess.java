package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ClinicalAccess {

    private ClinicalAccess() {
    }

    public static boolean doctorCanAccessPatient(int doctorId, int patientId) {
        try (Connection conn = DBConnection.openConnection()) {
            return doctorCanAccessPatient(conn, doctorId, patientId);
        } catch (SQLException e) {
            throw new RuntimeException("Could not check the doctor's clinical link", e);
        }
    }

    public static boolean doctorCanAccessPatient(Connection conn, int doctorId, int patientId) throws SQLException {
        return exists(conn,
                "SELECT 1 FROM consultas WHERE id_medico = ? AND id_paciente = ? "
                        + "AND status IN ('aguardando_atendimento', 'em_atendimento', 'realizada') "
                        + "UNION ALL SELECT 1 FROM consultas c WHERE c.id_paciente = ? AND c.tipo = 'emergencia' "
                        + "AND c.status = 'aguardando_atendimento' AND EXISTS (SELECT 1 FROM medico_especialidades me "
                        + "JOIN especialidades e ON e.id_especialidade = me.id_especialidade "
                        + "WHERE me.id_profissional = ? AND e.emergencia = 1) LIMIT 1",
                doctorId, patientId, patientId, doctorId);
    }

    public static boolean nurseCanAccessPatient(int nurseId, int patientId) {
        try (Connection conn = DBConnection.openConnection()) {
            return nurseCanAccessPatient(conn, nurseId, patientId);
        } catch (SQLException e) {
            throw new RuntimeException("Could not check the nurse's clinical link", e);
        }
    }

    public static boolean nurseCanAccessPatient(Connection conn, int nurseId, int patientId) throws SQLException {
        return exists(conn,
                "SELECT 1 FROM consultas WHERE id_paciente = ? AND status IN ('aguardando_triagem', 'em_triagem') "
                        + "UNION ALL "
                        + "SELECT 1 FROM triagens t JOIN consultas c ON c.id_consulta = t.id_consulta "
                        + "WHERE c.id_paciente = ? AND t.id_enfermeiro = ? LIMIT 1",
                patientId, patientId, nurseId);
    }

    private static boolean exists(Connection conn, String sql, int... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
