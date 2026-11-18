package database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

class HospitalModelIntegrationTest {

    private static final int DOCTOR = 3;
    private static final int NURSE = 2;
    private static final int SPECIALTY = 1;
    private static final int PATIENT_A = 1;
    private static final int PATIENT_B = 2;

    private Connection conn;

    @BeforeAll
    static void requireDatabase() {
        assumeTrue(TestDatabase.available(), "Database unavailable: integration tests skipped");
    }

    @BeforeEach
    void openTransaction() throws SQLException {
        conn = DBConnection.openConnection();
        conn.setAutoCommit(false);
    }

    @AfterEach
    void undo() throws SQLException {
        conn.rollback();
        conn.close();
    }

    private long appointment(int patient, String dateTime, String type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, tipo) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, patient);
            ps.setInt(2, DOCTOR);
            ps.setInt(3, SPECIALTY);
            ps.setString(4, dateTime);
            ps.setString(5, type);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private int execute(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }

    private long triage(long appointmentId, Object saturation) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, saturacao_o2, queixa_principal, id_classificacao) "
                        + "VALUES (?, ?, ?, 'Dor de cabeca (teste)', 4)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, appointmentId);
            ps.setInt(2, NURSE);
            ps.setObject(3, saturation);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void sameDoctorCannotHaveTwoActiveAppointmentsAtSameTime() throws SQLException {
        appointment(PATIENT_A, "2099-01-05 08:00:00", "agendada");
        assertThrows(SQLException.class, () -> appointment(PATIENT_B, "2099-01-05 08:00:00", "agendada"));
    }

    @Test
    void cancelFreesSlot() throws SQLException {
        long id = appointment(PATIENT_A, "2099-01-05 08:30:00", "agendada");
        execute("UPDATE consultas SET status = 'cancelada', motivo_cancelamento = 'teste', cancelada_em = NOW() "
                + "WHERE id_consulta = ?", id);
        assertDoesNotThrow(() -> appointment(PATIENT_B, "2099-01-05 08:30:00", "agendada"));
    }

    @Test
    void samePatientCannotHaveTwoAppointmentsAtSameTimeEvenWithOtherDoctor() throws SQLException {
        appointment(PATIENT_A, "2099-01-05 09:00:00", "agendada");

        long otherDoctor;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO profissionais (tipo, nome) VALUES ('medico', 'Outro Medico (teste)')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                otherDoctor = rs.getLong(1);
            }
        }
        execute("INSERT INTO medicos (id_profissional, crm, crm_uf) VALUES (?, '999999', 'RJ')", otherDoctor);

        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora) VALUES (?, ?, ?, '2099-01-05 09:00:00')",
                PATIENT_A, otherDoctor, SPECIALTY));
        assertDoesNotThrow(() -> execute(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora) VALUES (?, ?, ?, '2099-01-05 09:00:00')",
                PATIENT_B, otherDoctor, SPECIALTY));
    }

    @Test
    void walkInDoesNotTakeScheduleSlot() throws SQLException {
        appointment(PATIENT_A, "2099-01-05 10:00:00", "agendada");
        assertDoesNotThrow(() -> appointment(PATIENT_B, "2099-01-05 10:00:00", "encaixe"));
    }

    @Test
    void cancellationRequiresReason() throws SQLException {
        long id = appointment(PATIENT_A, "2099-01-05 10:30:00", "agendada");
        assertThrows(SQLException.class,
                () -> execute("UPDATE consultas SET status = 'cancelada' WHERE id_consulta = ?", id));
    }

    @Test
    void appointmentRequiresExistingDoctor() {
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora) VALUES (1, 999999, 1, '2099-01-05 11:00:00')"));
    }

    @Test
    void duplicateAndInvalidCpfAreRejected() {
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO pacientes (nome, cpf, data_nascimento) VALUES ('Duplicado', '12345678909', '2000-01-01')"));
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO pacientes (nome, cpf, data_nascimento) VALUES ('Formato', '123.456.789-09', '2000-01-01')"));
    }

    @Test
    void patientWithAppointmentCannotBeDeleted() throws SQLException {
        appointment(PATIENT_A, "2099-01-05 11:30:00", "agendada");
        assertThrows(SQLException.class, () -> execute("DELETE FROM pacientes WHERE id_paciente = ?", PATIENT_A));
    }

    @Test
    void outOfRangeVitalSignsAreRejected() throws SQLException {
        long id = appointment(PATIENT_A, "2099-01-05 13:00:00", "agendada");
        assertThrows(SQLException.class, () -> triage(id, 150));
        assertDoesNotThrow(() -> triage(id, 97));
    }

    @Test
    void triageCannotBeChangedOrDeleted() throws SQLException {
        long id = triage(appointment(PATIENT_A, "2099-01-05 13:30:00", "agendada"), 98);
        SQLException upd = assertThrows(SQLException.class,
                () -> execute("UPDATE triagens SET observacoes = 'x' WHERE id_triagem = ?", id));
        assertTrue(upd.getMessage().contains("retificacao"), upd.getMessage());
        assertThrows(SQLException.class, () -> execute("DELETE FROM triagens WHERE id_triagem = ?", id));
    }

    @Test
    void amendmentRequiresReason() throws SQLException {
        long appointmentId = appointment(PATIENT_A, "2099-01-05 14:00:00", "agendada");
        long original = triage(appointmentId, 98);
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, queixa_principal, id_classificacao, retifica_id) "
                        + "VALUES (?, ?, 'corrigida', 3, ?)", appointmentId, NURSE, original));
        assertEquals(1, execute(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, queixa_principal, id_classificacao, retifica_id, motivo_retificacao) "
                        + "VALUES (?, ?, 'corrigida', 3, ?, 'PA digitada errada')", appointmentId, NURSE, original));
    }

    @Test
    void draftRecordEditableAndFinishedImmutable() throws SQLException {
        long appointmentId = appointment(PATIENT_A, "2099-01-05 14:30:00", "agendada");
        execute("INSERT INTO prontuarios (id_consulta, id_medico, conduta) VALUES (?, ?, 'rascunho')", appointmentId, DOCTOR);

        assertEquals(1, execute("UPDATE prontuarios SET conduta = 'rascunho revisado' WHERE id_consulta = ?", appointmentId));
        assertEquals(1, execute("UPDATE prontuarios SET status = 'finalizado', finalizado_em = NOW() WHERE id_consulta = ?",
                appointmentId));

        SQLException e = assertThrows(SQLException.class,
                () -> execute("UPDATE prontuarios SET conduta = 'alterada' WHERE id_consulta = ?", appointmentId));
        assertTrue(e.getMessage().contains("adendo"), e.getMessage());
        assertThrows(SQLException.class, () -> execute("DELETE FROM prontuarios WHERE id_consulta = ?", appointmentId));
    }

    @Test
    void finishedMedicalRecordRequiresCompletionDate() throws SQLException {
        long appointmentId = appointment(PATIENT_A, "2099-01-05 15:00:00", "agendada");
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO prontuarios (id_consulta, id_medico, status) VALUES (?, ?, 'finalizado')", appointmentId, DOCTOR));
    }

    @Test
    void oneMedicalRecordPerAppointment() throws SQLException {
        long appointmentId = appointment(PATIENT_A, "2099-01-05 15:30:00", "agendada");
        execute("INSERT INTO prontuarios (id_consulta, id_medico) VALUES (?, ?)", appointmentId, DOCTOR);
        assertThrows(SQLException.class,
                () -> execute("INSERT INTO prontuarios (id_consulta, id_medico) VALUES (?, ?)", appointmentId, DOCTOR));
    }

    @Test
    void medicalRecordVersionMustBeValidJson() throws SQLException {
        long appointmentId = appointment(PATIENT_A, "2099-01-05 16:00:00", "agendada");
        execute("INSERT INTO prontuarios (id_consulta, id_medico) VALUES (?, ?)", appointmentId, DOCTOR);
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO prontuario_versoes (id_prontuario, versao, conteudo) "
                        + "SELECT id_prontuario, 1, 'nao e json' FROM prontuarios WHERE id_consulta = ?", appointmentId));
    }

    @Test
    void issuedPrescriptionCanOnlyBeCancelled() throws SQLException {
        long appointmentId = appointment(PATIENT_A, "2099-01-05 16:30:00", "agendada");
        execute("INSERT INTO receitas (id_consulta, id_medico, orientacoes_gerais) VALUES (?, ?, 'repouso')",
                appointmentId, DOCTOR);

        assertThrows(SQLException.class, () -> execute(
                "UPDATE receitas SET orientacoes_gerais = 'alterada' WHERE id_consulta = ?", appointmentId));
        assertEquals(1, execute(
                "UPDATE receitas SET status = 'cancelada', cancelada_em = NOW(), motivo_cancelamento = 'erro de dose' "
                        + "WHERE id_consulta = ?", appointmentId));
        assertThrows(SQLException.class, () -> execute(
                "UPDATE receitas SET status = 'emitida' WHERE id_consulta = ?", appointmentId));
    }

    @Test
    void auditLogIsAppendOnly() throws SQLException {
        execute("INSERT INTO auditoria (id_usuario, acao, entidade) VALUES (1, 'teste.integridade', 'teste')");
        assertThrows(SQLException.class,
                () -> execute("UPDATE auditoria SET acao = 'x' WHERE acao = 'teste.integridade'"));
        assertThrows(SQLException.class,
                () -> execute("DELETE FROM auditoria WHERE acao = 'teste.integridade'"));
    }

    @Test
    void hospitalRoleSeedIsComplete() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM grupos WHERE nome IN ('recepcionista','enfermeiro','medico')),"
                             + " (SELECT COUNT(*) FROM classificacoes_prioridade),"
                             + " (SELECT COUNT(*) FROM grupos_permissoes gp JOIN permissoes p ON p.id_permissao = gp.id_permissao"
                             + "    WHERE gp.id_grupo = 1 AND p.codigo LIKE 'prontuario.%')")) {
            rs.next();
            assertEquals(3, rs.getInt(1));
            assertEquals(5, rs.getInt(2));
            assertEquals(0, rs.getInt(3), "the administrator gets no clinical permission (decision P1)");
        }
    }
}
