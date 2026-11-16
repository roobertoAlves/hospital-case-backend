package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Receita
 *
 * Receitas (RN-REC-01..03): o medico da consulta emite durante o atendimento (ou logo depois
 * de finalizar); a receita emitida nao e editada, so cancelada com motivo. Cabecalho e itens
 * entram na mesma transacao. A impressao traz nome, CRM e UF do medico.
 */
public class Receita {

    public static final int MAX_ITENS = 20;

    public record Resultado(long id, Map<String, String> erros) {
        public boolean ok() {
            return erros.isEmpty();
        }

        static Resultado erro(String campo, String msg) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put(campo, msg);
            return new Resultado(0, e);
        }
    }

    public Resultado emitir(int idConsulta, int idMedico, String orientacoes, List<?> itensBrutos, int idUsuario) {
        List<Map<String, Object>> itens = new ArrayList<>();
        Map<String, String> erros = validarItens(itensBrutos, itens);
        if (!erros.isEmpty()) {
            return new Resultado(0, erros);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = consultaDoMedico(conn, idConsulta, idMedico);
                if (c == null) {
                    conn.rollback();
                    return Resultado.erro("geral", "Só o médico do atendimento emite receita, durante ou logo após a consulta.");
                }
                String orient = Sanitize.string(orientacoes).trim();
                long id = Sql.inserir(conn, "INSERT INTO receitas (id_consulta, id_medico, orientacoes_gerais) VALUES (?, ?, ?)",
                        idConsulta, idMedico, orient.isEmpty() ? null : corta(orient, 5000));
                int ordem = 1;
                for (Map<String, Object> i : itens) {
                    Sql.executar(conn, "INSERT INTO receita_itens (id_receita, ordem, medicamento, dosagem, via, frequencia, duracao, orientacoes) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, ordem++, i.get("medicamento"), i.get("dosagem"), i.get("via"),
                            i.get("frequencia"), i.get("duracao"), i.get("orientacoes"));
                }
                Auditoria.registrar(conn, idUsuario, "receita.emitida", "receitas", id,
                        ((Number) c.get("id_paciente")).intValue(), itens.size() + " itens", null);
                conn.commit();
                return new Resultado(id, erros);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao emitir receita", e);
        }
    }

    public Resultado cancelar(int idReceita, int idMedico, String motivo, int idUsuario) {
        String m = Sanitize.string(motivo).trim();
        if (m.length() < 3) {
            return Resultado.erro("motivo", "Informe o motivo do cancelamento.");
        }
        Map<String, Object> r = Sql.um("SELECT r.id_receita, c.id_paciente FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta "
                + "WHERE r.id_receita = ? AND r.id_medico = ? AND r.status = 'emitida'", idReceita, idMedico);
        if (r == null) {
            return Resultado.erro("geral", "Receita não encontrada, de outro médico ou já cancelada.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            int n = Sql.executar(conn, "UPDATE receitas SET status = 'cancelada', cancelada_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_receita = ? AND status = 'emitida'", corta(m, 255), idReceita);
            if (n == 1) {
                Auditoria.registrar(conn, idUsuario, "receita.cancelada", "receitas", idReceita,
                        ((Number) r.get("id_paciente")).intValue(), null, null);
            }
            return new Resultado(idReceita, new LinkedHashMap<>());
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao cancelar receita", e);
        }
    }

    /** Receitas da consulta com seus itens (mais recentes primeiro). */
    public List<Map<String, Object>> daConsulta(int idConsulta) {
        List<Map<String, Object>> receitas = Sql.lista("SELECT id_receita, status, orientacoes_gerais, emitida_em, cancelada_em, "
                + "motivo_cancelamento FROM receitas WHERE id_consulta = ? ORDER BY emitida_em DESC, id_receita DESC", idConsulta);
        for (Map<String, Object> r : receitas) {
            r.put("itens", itens(((Number) r.get("id_receita")).intValue()));
        }
        return receitas;
    }

    public List<Map<String, Object>> doPaciente(int idPaciente) {
        List<Map<String, Object>> receitas = Sql.lista("SELECT r.id_receita, r.status, r.emitida_em, pr.nome AS medico FROM receitas r "
                + "JOIN consultas c ON c.id_consulta = r.id_consulta JOIN profissionais pr ON pr.id_profissional = r.id_medico "
                + "WHERE c.id_paciente = ? ORDER BY r.emitida_em DESC", idPaciente);
        for (Map<String, Object> r : receitas) {
            r.put("itens", itens(((Number) r.get("id_receita")).intValue()));
        }
        return receitas;
    }

    /** Dados para imprimir; null se a receita nao for do medico (sem vazar se existe). */
    public Map<String, Object> paraImpressao(int idReceita, int idMedico) {
        Map<String, Object> r = Sql.um("SELECT r.id_receita, r.id_consulta, r.status, r.orientacoes_gerais, r.emitida_em, r.motivo_cancelamento, "
                + "pr.nome AS medico, m.crm, m.crm_uf, pa.id_paciente, pa.nome AS paciente, pa.data_nascimento, pa.cpf, "
                + "pa.logradouro, pa.numero, pa.cidade, pa.uf "
                + "FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = r.id_medico JOIN medicos m ON m.id_profissional = r.id_medico "
                + "WHERE r.id_receita = ? AND r.id_medico = ?", idReceita, idMedico);
        if (r != null) {
            r.put("itens", itens(idReceita));
        }
        return r;
    }

    /** Receita para o portal: so a do proprio paciente; null se for de outra pessoa (sem vazar se existe). */
    public Map<String, Object> paraImpressaoDoPaciente(int idReceita, int idPaciente) {
        Map<String, Object> r = Sql.um("SELECT r.id_receita, r.id_consulta, r.status, r.orientacoes_gerais, r.emitida_em, r.motivo_cancelamento, "
                + "pr.nome AS medico, m.crm, m.crm_uf, pa.id_paciente, pa.nome AS paciente, pa.data_nascimento, pa.cpf, "
                + "pa.logradouro, pa.numero, pa.cidade, pa.uf "
                + "FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = r.id_medico JOIN medicos m ON m.id_profissional = r.id_medico "
                + "WHERE r.id_receita = ? AND c.id_paciente = ?", idReceita, idPaciente);
        if (r != null) {
            r.put("itens", itens(idReceita));
        }
        return r;
    }

    private List<Map<String, Object>> itens(int idReceita) {
        return Sql.lista("SELECT ordem, medicamento, dosagem, via, frequencia, duracao, orientacoes FROM receita_itens "
                + "WHERE id_receita = ? ORDER BY ordem", idReceita);
    }

    // ------------------------------------------------------------------ validacao

    static Map<String, String> validarItens(List<?> brutos, List<Map<String, Object>> saida) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (brutos == null || brutos.isEmpty()) {
            erros.put("itens", "Inclua ao menos um medicamento.");
            return erros;
        }
        if (brutos.size() > MAX_ITENS) {
            erros.put("itens", "No máximo " + MAX_ITENS + " medicamentos por receita.");
            return erros;
        }
        int n = 1;
        for (Object o : brutos) {
            if (!(o instanceof Map)) {
                erros.put("itens", "Formato de item inválido.");
                return erros;
            }
            Map<?, ?> b = (Map<?, ?>) o;
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("medicamento", texto(b.get("medicamento"), 200));
            i.put("dosagem", texto(b.get("dosagem"), 100));
            i.put("via", texto(b.get("via"), 50));
            i.put("frequencia", texto(b.get("frequencia"), 100));
            i.put("duracao", texto(b.get("duracao"), 100));
            i.put("orientacoes", texto(b.get("orientacoes"), 500));
            for (String obrig : List.of("medicamento", "dosagem", "frequencia", "duracao")) {
                if (i.get(obrig) == null) {
                    erros.put("itens", "Item " + n + ": preencha medicamento, dosagem, frequência e duração.");
                    return erros;
                }
            }
            saida.add(i);
            n++;
        }
        return erros;
    }

    private static Map<String, Object> consultaDoMedico(Connection conn, int idConsulta, int idMedico) throws SQLException {
        return Sql.um(conn, "SELECT id_paciente FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", idConsulta, idMedico);
    }

    private static String texto(Object v, int max) {
        String s = v == null ? "" : Sanitize.string(String.valueOf(v)).trim();
        return s.isEmpty() ? null : corta(s, max);
    }

    static String corta(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
