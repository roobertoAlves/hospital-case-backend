package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

/** RN-ACE-03/04: professional-patient link, tested in a transaction rolled back at the end. */
class ClinicalAccessIntegrationTest {

    private static final int DOCTOR = 3;
    private static final int NURSE = 2;
    /** Patients created inside the transaction (no history at all); they vanish on rollback. */
    private int PATIENT;
    private int OTHER_PATIENT;

    private Connection conn;

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(TestDatabase.available(), "Database unavailable");
    }

    @BeforeEach
    void open() throws SQLException {
        conn = DBConnection.openConnection();
        conn.setAutoCommit(false);
        PATIENT = newPatient("97314826528");
        OTHER_PATIENT = newPatient("97314826609");
    }

    private int newPatient(String cpf) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO pacientes (nome, cpf, data_nascimento) VALUES ('Paciente de Teste Vinculo', ?, '1980-01-01')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cpf);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @AfterEach
    void undo() throws SQLException {
        conn.rollback();
        conn.close();
    }

    private long appointment(int patient, String status, String dateTime) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, status) VALUES (?, ?, 1, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patient);
            ps.setInt(2, DOCTOR);
            ps.setString(3, dateTime);
            ps.setString(4, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void doctorOnlyAccessesReferredInProgressOrSeenPatient() throws SQLException {
        assertFalse(ClinicalAccess.doctorCanAccessPatient(conn, DOCTOR, PATIENT), "no appointment, no access");

        appointment(PATIENT, "agendada", "2099-02-01 08:00:00");
        assertFalse(ClinicalAccess.doctorCanAccessPatient(conn, DOCTOR, PATIENT), "a scheduled appointment does not grant access yet");

        appointment(PATIENT, "aguardando_atendimento", "2099-02-02 08:00:00");
        assertTrue(ClinicalAccess.doctorCanAccessPatient(conn, DOCTOR, PATIENT));
        assertFalse(ClinicalAccess.doctorCanAccessPatient(conn, DOCTOR, OTHER_PATIENT), "changing the ID grants no access");
    }

    @Test
    void nurseAccessesQueuedOrTriagedPatient() throws SQLException {
        assertFalse(ClinicalAccess.nurseCanAccessPatient(conn, NURSE, PATIENT));

        long appointmentId = appointment(PATIENT, "aguardando_triagem", "2099-02-03 08:00:00");
        assertTrue(ClinicalAccess.nurseCanAccessPatient(conn, NURSE, PATIENT), "patient in the queue");

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, queixa_principal, id_classificacao) VALUES (?, ?, 'teste', 4)")) {
            ps.setLong(1, appointmentId);
            ps.setInt(2, NURSE);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE consultas SET status = 'realizada' WHERE id_consulta = ?")) {
            ps.setLong(1, appointmentId);
            ps.executeUpdate();
        }
        assertTrue(ClinicalAccess.nurseCanAccessPatient(conn, NURSE, PATIENT), "triado por ele, mesmo fora da fila");
        assertFalse(ClinicalAccess.nurseCanAccessPatient(conn, NURSE, OTHER_PATIENT));
    }

    @Test
    void seededProfessionalWithLicense() throws SQLException {
        Map<String, Object> doctor = new Professional().byUser(conn, 32);
        assertEquals("medico", doctor.get("tipo"));
        assertEquals("CRM 000001/SP", doctor.get("license"));
        assertEquals("COREN 000001/SP", new Professional().byUser(conn, 31).get("license"));
        assertNull(new Professional().byUser(conn, 1), "admin is not a professional");
    }
}
