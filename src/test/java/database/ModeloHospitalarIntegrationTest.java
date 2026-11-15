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

/**
 * Valida as regras de integridade do modelo da Fase 3 diretamente no banco:
 * UNIQUE, CHECK, FK e triggers de imutabilidade. Cada teste roda numa transacao
 * desfeita no final (rollback), sem deixar dados no banco de desenvolvimento.
 */
class ModeloHospitalarIntegrationTest {

    private static final int MEDICO = 3;          // seed: Marina Medica (ficticia)
    private static final int ENFERMEIRO = 2;      // seed: Eduardo Enfermeiro (ficticio)
    private static final int ESPECIALIDADE = 1;   // seed: Clinica Geral
    private static final int PACIENTE_A = 1;
    private static final int PACIENTE_B = 2;

    private Connection conn;

    @BeforeAll
    static void exigirBanco() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel: testes de integracao ignorados");
    }

    @BeforeEach
    void abrirTransacao() throws SQLException {
        conn = DBConnection.openConnection();
        conn.setAutoCommit(false);
    }

    @AfterEach
    void desfazer() throws SQLException {
        conn.rollback();
        conn.close();
    }

    private long consulta(int paciente, String dataHora, String tipo) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, tipo) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, paciente);
            ps.setInt(2, MEDICO);
            ps.setInt(3, ESPECIALIDADE);
            ps.setString(4, dataHora);
            ps.setString(5, tipo);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private int executar(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }

    private long triagem(long idConsulta, Object saturacao) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, saturacao_o2, queixa_principal, id_classificacao) "
                        + "VALUES (?, ?, ?, 'Dor de cabeca (teste)', 4)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, idConsulta);
            ps.setInt(2, ENFERMEIRO);
            ps.setObject(3, saturacao);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---------------------------------------------------------------- agenda

    @Test
    void mesmoMedicoNaoTemDuasConsultasAtivasNoMesmoHorario() throws SQLException {
        consulta(PACIENTE_A, "2099-01-05 08:00:00", "agendada");
        assertThrows(SQLException.class, () -> consulta(PACIENTE_B, "2099-01-05 08:00:00", "agendada"));
    }

    @Test
    void cancelarLiberaOHorario() throws SQLException {
        long id = consulta(PACIENTE_A, "2099-01-05 08:30:00", "agendada");
        executar("UPDATE consultas SET status = 'cancelada', motivo_cancelamento = 'teste', cancelada_em = NOW() "
                + "WHERE id_consulta = ?", id);
        assertDoesNotThrow(() -> consulta(PACIENTE_B, "2099-01-05 08:30:00", "agendada"));
    }

    @Test
    void mesmoPacienteNaoTemDuasConsultasNoMesmoHorarioMesmoComOutroMedico() throws SQLException {
        consulta(PACIENTE_A, "2099-01-05 09:00:00", "agendada");

        long outroMedico;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO profissionais (tipo, nome) VALUES ('medico', 'Outro Medico (teste)')",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                outroMedico = rs.getLong(1);
            }
        }
        executar("INSERT INTO medicos (id_profissional, crm, crm_uf) VALUES (?, '999999', 'RJ')", outroMedico);

        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora) VALUES (?, ?, ?, '2099-01-05 09:00:00')",
                PACIENTE_A, outroMedico, ESPECIALIDADE));
        assertDoesNotThrow(() -> executar(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora) VALUES (?, ?, ?, '2099-01-05 09:00:00')",
                PACIENTE_B, outroMedico, ESPECIALIDADE));
    }

    @Test
    void encaixeNaoOcupaOHorarioDaAgenda() throws SQLException {
        consulta(PACIENTE_A, "2099-01-05 10:00:00", "agendada");
        assertDoesNotThrow(() -> consulta(PACIENTE_B, "2099-01-05 10:00:00", "encaixe"));
    }

    @Test
    void cancelamentoExigeMotivo() throws SQLException {
        long id = consulta(PACIENTE_A, "2099-01-05 10:30:00", "agendada");
        assertThrows(SQLException.class,
                () -> executar("UPDATE consultas SET status = 'cancelada' WHERE id_consulta = ?", id));
    }

    @Test
    void consultaExigeMedicoExistente() {
        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora) VALUES (1, 999999, 1, '2099-01-05 11:00:00')"));
    }

    // ---------------------------------------------------------------- pacientes

    @Test
    void cpfDuplicadoEInvalidoSaoRecusados() {
        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO pacientes (nome, cpf, data_nascimento) VALUES ('Duplicado', '12345678909', '2000-01-01')"));
        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO pacientes (nome, cpf, data_nascimento) VALUES ('Formato', '123.456.789-09', '2000-01-01')"));
    }

    @Test
    void pacienteComConsultaNaoPodeSerApagado() throws SQLException {
        consulta(PACIENTE_A, "2099-01-05 11:30:00", "agendada");
        assertThrows(SQLException.class, () -> executar("DELETE FROM pacientes WHERE id_paciente = ?", PACIENTE_A));
    }

    // ---------------------------------------------------------------- triagem

    @Test
    void sinaisVitaisForaDaFaixaSaoRecusados() throws SQLException {
        long id = consulta(PACIENTE_A, "2099-01-05 13:00:00", "agendada");
        assertThrows(SQLException.class, () -> triagem(id, 150));
        assertDoesNotThrow(() -> triagem(id, 97));
    }

    @Test
    void triagemNaoPodeSerAlteradaNemExcluida() throws SQLException {
        long id = triagem(consulta(PACIENTE_A, "2099-01-05 13:30:00", "agendada"), 98);
        SQLException upd = assertThrows(SQLException.class,
                () -> executar("UPDATE triagens SET observacoes = 'x' WHERE id_triagem = ?", id));
        assertTrue(upd.getMessage().contains("retificacao"), upd.getMessage());
        assertThrows(SQLException.class, () -> executar("DELETE FROM triagens WHERE id_triagem = ?", id));
    }

    @Test
    void retificacaoExigeMotivo() throws SQLException {
        long idConsulta = consulta(PACIENTE_A, "2099-01-05 14:00:00", "agendada");
        long original = triagem(idConsulta, 98);
        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, queixa_principal, id_classificacao, retifica_id) "
                        + "VALUES (?, ?, 'corrigida', 3, ?)", idConsulta, ENFERMEIRO, original));
        assertEquals(1, executar(
                "INSERT INTO triagens (id_consulta, id_enfermeiro, queixa_principal, id_classificacao, retifica_id, motivo_retificacao) "
                        + "VALUES (?, ?, 'corrigida', 3, ?, 'PA digitada errada')", idConsulta, ENFERMEIRO, original));
    }

    // ---------------------------------------------------------------- prontuario

    @Test
    void prontuarioRascunhoEditavelEFinalizadoImutavel() throws SQLException {
        long idConsulta = consulta(PACIENTE_A, "2099-01-05 14:30:00", "agendada");
        executar("INSERT INTO prontuarios (id_consulta, id_medico, conduta) VALUES (?, ?, 'rascunho')", idConsulta, MEDICO);

        assertEquals(1, executar("UPDATE prontuarios SET conduta = 'rascunho revisado' WHERE id_consulta = ?", idConsulta));
        assertEquals(1, executar("UPDATE prontuarios SET status = 'finalizado', finalizado_em = NOW() WHERE id_consulta = ?",
                idConsulta));

        SQLException e = assertThrows(SQLException.class,
                () -> executar("UPDATE prontuarios SET conduta = 'alterada' WHERE id_consulta = ?", idConsulta));
        assertTrue(e.getMessage().contains("adendo"), e.getMessage());
        assertThrows(SQLException.class, () -> executar("DELETE FROM prontuarios WHERE id_consulta = ?", idConsulta));
    }

    @Test
    void prontuarioFinalizadoExigeDataDeFinalizacao() throws SQLException {
        long idConsulta = consulta(PACIENTE_A, "2099-01-05 15:00:00", "agendada");
        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO prontuarios (id_consulta, id_medico, status) VALUES (?, ?, 'finalizado')", idConsulta, MEDICO));
    }

    @Test
    void umProntuarioPorConsulta() throws SQLException {
        long idConsulta = consulta(PACIENTE_A, "2099-01-05 15:30:00", "agendada");
        executar("INSERT INTO prontuarios (id_consulta, id_medico) VALUES (?, ?)", idConsulta, MEDICO);
        assertThrows(SQLException.class,
                () -> executar("INSERT INTO prontuarios (id_consulta, id_medico) VALUES (?, ?)", idConsulta, MEDICO));
    }

    @Test
    void versaoDoProntuarioPrecisaSerJsonValido() throws SQLException {
        long idConsulta = consulta(PACIENTE_A, "2099-01-05 16:00:00", "agendada");
        executar("INSERT INTO prontuarios (id_consulta, id_medico) VALUES (?, ?)", idConsulta, MEDICO);
        assertThrows(SQLException.class, () -> executar(
                "INSERT INTO prontuario_versoes (id_prontuario, versao, conteudo) "
                        + "SELECT id_prontuario, 1, 'nao e json' FROM prontuarios WHERE id_consulta = ?", idConsulta));
    }

    // ---------------------------------------------------------------- receitas

    @Test
    void receitaEmitidaSoPodeSerCancelada() throws SQLException {
        long idConsulta = consulta(PACIENTE_A, "2099-01-05 16:30:00", "agendada");
        executar("INSERT INTO receitas (id_consulta, id_medico, orientacoes_gerais) VALUES (?, ?, 'repouso')",
                idConsulta, MEDICO);

        assertThrows(SQLException.class, () -> executar(
                "UPDATE receitas SET orientacoes_gerais = 'alterada' WHERE id_consulta = ?", idConsulta));
        assertEquals(1, executar(
                "UPDATE receitas SET status = 'cancelada', cancelada_em = NOW(), motivo_cancelamento = 'erro de dose' "
                        + "WHERE id_consulta = ?", idConsulta));
        assertThrows(SQLException.class, () -> executar(
                "UPDATE receitas SET status = 'emitida' WHERE id_consulta = ?", idConsulta));
    }

    // ---------------------------------------------------------------- auditoria

    @Test
    void auditoriaEhSomenteInclusao() throws SQLException {
        executar("INSERT INTO auditoria (id_usuario, acao, entidade) VALUES (1, 'teste.integridade', 'teste')");
        assertThrows(SQLException.class,
                () -> executar("UPDATE auditoria SET acao = 'x' WHERE acao = 'teste.integridade'"));
        assertThrows(SQLException.class,
                () -> executar("DELETE FROM auditoria WHERE acao = 'teste.integridade'"));
    }

    // ---------------------------------------------------------------- seed

    @Test
    void seedDosPerfisHospitalaresEstaCompleto() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM grupos WHERE nome IN ('recepcionista','enfermeiro','medico')),"
                             + " (SELECT COUNT(*) FROM classificacoes_prioridade),"
                             + " (SELECT COUNT(*) FROM grupos_permissoes gp JOIN permissoes p ON p.id_permissao = gp.id_permissao"
                             + "    WHERE gp.id_grupo = 1 AND p.codigo LIKE 'prontuario.%')")) {
            rs.next();
            assertEquals(3, rs.getInt(1));
            assertEquals(5, rs.getInt(2));
            assertEquals(0, rs.getInt(3), "administrador nao recebe permissao clinica (decisao P1)");
        }
    }
}
