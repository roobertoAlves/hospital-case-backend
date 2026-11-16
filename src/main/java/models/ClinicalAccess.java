package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ZTF_J2EE - ClinicalAccess model
 *
 * Access rule for clinical data based on the professional-patient link (phase 2):
 *
 * RN-ACE-03 (decision P2) - DOCTOR: reads a patient's clinical history when they have at least
 *   one appointment with the patient that was referred (aguardando_atendimento), is in progress
 *   or was completed.
 *
 * RN-ACE-04 (decision P3) - NURSE: reads triages and allergies of patients in the triage queue
 *   (waiting or being triaged) or that they triaged themselves. Never reads the medical record.
 *
 * Clinical controllers must call these methods AFTER loading the record by the received ID, so
 * changing the ID in the URL grants no access (RN-ACE-06). The Connection overloads allow using
 * the same transaction (and testing with rollback).
 */
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
                        + "AND status IN ('aguardando_atendimento', 'em_atendimento', 'realizada') LIMIT 1",
                doctorId, patientId);
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
