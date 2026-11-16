package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model SolicitacaoExame
 *
 * Pedido de exame feito pelo medico no atendimento (RF-EXA-01..03). O resultado do exame
 * esta fora do escopo; o agendamento fica com a recepcao (agenda de exames).
 */
public class SolicitacaoExame {

    public record Resultado(boolean ok, long id, String erro) {
    }

    public Resultado solicitar(int idConsulta, int idMedico, Map<String, Object> dados, int idUsuario) {
        String nome = texto(dados.get("nome_exame"), 200);
        if (nome == null || nome.length() < 3) {
            return new Resultado(false, 0, "Informe o exame.");
        }
        Map<String, Object> c = Sql.um("SELECT id_paciente FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", idConsulta, idMedico);
        if (c == null) {
            return new Resultado(false, 0, "Só o médico do atendimento solicita exames, durante ou logo após a consulta.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.inserir(conn, "INSERT INTO exames (id_consulta, id_medico, nome_exame, codigo, justificativa, observacoes) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", idConsulta, idMedico, nome, texto(dados.get("codigo"), 20),
                    texto(dados.get("justificativa"), 500), texto(dados.get("observacoes"), 500));
            Auditoria.registrar(conn, idUsuario, "exame.solicitado", "exames", id, ((Number) c.get("id_paciente")).intValue(), null, null);
            return new Resultado(true, id, null);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao solicitar exame", e);
        }
    }

    public Resultado cancelar(int idExame, int idMedico, String motivo, int idUsuario) {
        String m = texto(motivo, 255);
        if (m == null || m.length() < 3) {
            return new Resultado(false, 0, "Informe o motivo do cancelamento.");
        }
        Map<String, Object> e = Sql.um("SELECT c.id_paciente FROM exames e JOIN consultas c ON c.id_consulta = e.id_consulta "
                + "WHERE e.id_exame = ? AND e.id_medico = ? AND e.status = 'solicitado'", idExame, idMedico);
        if (e == null) {
            return new Resultado(false, 0, "Solicitação não encontrada, de outro médico ou já cancelada.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            Sql.executar(conn, "UPDATE exames SET status = 'cancelado', cancelado_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_exame = ? AND status = 'solicitado'", m, idExame);
            Auditoria.registrar(conn, idUsuario, "exame.cancelado", "exames", idExame, ((Number) e.get("id_paciente")).intValue(), null, null);
            return new Resultado(true, idExame, null);
        } catch (SQLException ex) {
            throw new RuntimeException("Falha ao cancelar exame", ex);
        }
    }

    public List<Map<String, Object>> daConsulta(int idConsulta) {
        return Sql.lista("SELECT id_exame, nome_exame, codigo, justificativa, observacoes, status, solicitado_em, motivo_cancelamento "
                + "FROM exames WHERE id_consulta = ? ORDER BY solicitado_em DESC, id_exame DESC", idConsulta);
    }

    public List<Map<String, Object>> doPaciente(int idPaciente) {
        return Sql.lista("SELECT e.id_exame, e.nome_exame, e.status, e.solicitado_em, pr.nome AS medico FROM exames e "
                + "JOIN consultas c ON c.id_consulta = e.id_consulta JOIN profissionais pr ON pr.id_profissional = e.id_medico "
                + "WHERE c.id_paciente = ? ORDER BY e.solicitado_em DESC", idPaciente);
    }

    private static String texto(Object v, int max) {
        String s = v == null ? "" : Sanitize.string(String.valueOf(v)).trim();
        return s.isEmpty() ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
