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

/** RN-ACE-03/04: vinculo profissional-paciente, testado numa transacao desfeita no final. */
class VinculoClinicoIntegrationTest {

    private static final int MEDICO = 3;
    private static final int ENFERMEIRO = 2;
    private static final int PACIENTE = 1;
    private static final int OUTRO_PACIENTE = 2;

    private Connection conn;

    @BeforeAll
    static void exigirBanco() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
    }

    @BeforeEach
    void abrir() throws SQLException {
        conn = DBConnection.openConnection();
        conn.setAutoCommit(false);
    }

    @AfterEach
    void desfazer() throws SQLException {
        conn.rollback();
        conn.close();
    }

    private long consulta(int paciente, String status, String dataHora) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, status) VALUES (?, ?, 1, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, paciente);
            ps.setInt(2, MEDICO);
            ps.setString(3, dataHora);
            ps.setString(4, status);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void medicoSoAcessaPacienteEncaminhadoEmAtendimentoOuJaAtendido() throws SQLException {
        assertFalse(VinculoClinico.medicoPodeAcessarPaciente(conn, MEDICO, PACIENTE), "sem consulta, sem acesso");

        consulta(PACIENTE, "agendada", "2099-02-01 08:00:00");
        assertFalse(VinculoClinico.medicoPodeAcessarPaciente(conn, MEDICO, PACIENTE), "agendada ainda nao libera");

        consulta(PACIENTE, "aguardando_atendimento", "2099-02-02 08:00:00");
        assertTrue(VinculoClinico.medicoPodeAcessarPaciente(conn, MEDICO, PACIENTE));
        assertFalse(VinculoClinico.medicoPodeAcessarPaciente(conn, MEDICO, OUTRO_PACIENTE), "trocar o ID nao da acesso");
    }

    @Test
    void enfermeiroAcessaPacienteNaFilaOuQueEleTriou() throws SQLException {
        assertFalse(VinculoClinico.enfermeiroPodeAcessarPaciente(conn, ENFERMEIRO, PACIENTE));

        long idConsulta = consulta(PACIENTE, "aguardando_triagem", "2099-02-03 08:00:00");
        assertTrue(VinculoClinico.enfermeiroPodeAcessarPaciente(conn, ENFERMEIRO, PACIENTE), "paciente na fila");

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, queixa_principal, id_classificacao) VALUES (?, ?, 'teste', 4)")) {
            ps.setLong(1, idConsulta);
            ps.setInt(2, ENFERMEIRO);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("UPDATE consultas SET status = 'realizada' WHERE id_consulta = ?")) {
            ps.setLong(1, idConsulta);
            ps.executeUpdate();
        }
        assertTrue(VinculoClinico.enfermeiroPodeAcessarPaciente(conn, ENFERMEIRO, PACIENTE), "triado por ele, mesmo fora da fila");
        assertFalse(VinculoClinico.enfermeiroPodeAcessarPaciente(conn, ENFERMEIRO, OUTRO_PACIENTE));
    }

    @Test
    void profissionalDoSeedComRegistro() throws SQLException {
        Map<String, Object> medico = new Profissional().porUsuario(conn, 32);
        assertEquals("medico", medico.get("tipo"));
        assertEquals("CRM 000001/SP", medico.get("registro"));
        assertEquals("COREN 000001/SP", new Profissional().porUsuario(conn, 31).get("registro"));
        assertNull(new Profissional().porUsuario(conn, 1), "admin nao e profissional");
    }
}
