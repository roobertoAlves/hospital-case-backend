package models;

import com.google.gson.Gson;
import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Atendimento (prontuario)
 *
 * Atendimento medico (RF-PRT-01..07, RN-PRT-01..04):
 * - iniciar: so o medico da consulta, so com a consulta "aguardando_atendimento"; cria o
 *   registro do prontuario como rascunho (queixa da triagem pre-preenchida);
 * - salvar rascunho: so o autor; a versao anterior vai inteira para prontuario_versoes (JSON);
 * - finalizar: registro imutavel (trigger no banco) e consulta "realizada";
 * - complementos depois de finalizar: evolucao ou adendo, so inclusao;
 * - alergias pertencem ao paciente (valem para todos os atendimentos).
 * Toda leitura e escrita de dado clinico e auditada com o id do paciente.
 */
public class Atendimento {

    private static final Gson GSON = new Gson();

    public static final List<String> CAMPOS = List.of(
            "queixa_principal", "historia_doenca_atual", "historico_relevante",
            "observacoes", "hipoteses_diagnosticas", "conduta");

    public record Resultado(boolean ok, long id, String erro) {
        static Resultado falha(String erro) {
            return new Resultado(false, 0, erro);
        }
    }

    /** Fila e atendimentos em curso do medico (prioridade, depois chegada). */
    public List<Map<String, Object>> filaDoMedico(int idMedico) {
        return Sql.lista("SELECT c.id_consulta, c.status, c.chegada_em, c.data_hora, c.id_paciente, pa.nome AS paciente, "
                + "pa.data_nascimento, e.nome AS especialidade, cp.nome AS prioridade, cp.cor_hex, cp.ordem, t.queixa_principal "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "LEFT JOIN triagens t ON t.id_consulta = c.id_consulta "
                + " AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) "
                + "LEFT JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "WHERE c.id_medico = ? AND c.status IN ('aguardando_atendimento', 'em_atendimento') "
                + "ORDER BY c.status = 'em_atendimento' DESC, COALESCE(cp.ordem, 99), c.chegada_em", idMedico);
    }

    /** Consulta do medico com paciente e prontuario; null se a consulta nao for dele. */
    public Map<String, Object> consultaDoMedico(int idConsulta, int idMedico) {
        return Sql.um("SELECT c.id_consulta, c.status, c.data_hora, c.chegada_em, c.inicio_atendimento_em, c.id_paciente, "
                + "c.id_medico, pa.nome AS paciente, pa.data_nascimento, pa.sexo, pa.cpf, e.nome AS especialidade, "
                + "p.id_prontuario, p.status AS status_prontuario, p.versao, p.finalizado_em, p.atualizado_em AS prontuario_atualizado_em, "
                + "p.queixa_principal, p.historia_doenca_atual, p.historico_relevante, p.observacoes, p.hipoteses_diagnosticas, p.conduta "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "LEFT JOIN prontuarios p ON p.id_consulta = c.id_consulta "
                + "WHERE c.id_consulta = ? AND c.id_medico = ?", idConsulta, idMedico);
    }

    public Resultado iniciar(int idConsulta, int idMedico, int idUsuario) {
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = Sql.um(conn, "SELECT id_paciente, status FROM consultas WHERE id_consulta = ? AND id_medico = ? FOR UPDATE",
                        idConsulta, idMedico);
                if (c == null) {
                    conn.rollback();
                    return Resultado.falha("Consulta não encontrada na sua agenda.");
                }
                if (!"aguardando_atendimento".equals(c.get("status"))) {
                    conn.rollback();
                    return Resultado.falha("O paciente ainda não foi triado ou o atendimento já começou.");
                }
                Map<String, Object> t = Sql.um(conn, "SELECT queixa_principal FROM triagens t WHERE id_consulta = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) ORDER BY id_triagem DESC LIMIT 1", idConsulta);
                Sql.executar(conn, "UPDATE consultas SET status = 'em_atendimento', inicio_atendimento_em = NOW(), atualizado_por = ? "
                        + "WHERE id_consulta = ?", idUsuario, idConsulta);
                long id = Sql.inserir(conn, "INSERT INTO prontuarios (id_consulta, id_medico, queixa_principal) VALUES (?, ?, ?)",
                        idConsulta, idMedico, t != null ? t.get("queixa_principal") : null);
                Auditoria.registrar(conn, idUsuario, "atendimento.iniciado", "consultas", idConsulta,
                        ((Number) c.get("id_paciente")).intValue(), null, null);
                conn.commit();
                return new Resultado(true, id, null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao iniciar atendimento", e);
        }
    }

    /** RN-PRT-02: so o autor altera o rascunho; a versao anterior e guardada. */
    public Resultado salvarRascunho(int idConsulta, int idMedico, Map<String, Object> dados, int idUsuario) {
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> p = Sql.um(conn, "SELECT p.*, c.id_paciente FROM prontuarios p JOIN consultas c ON c.id_consulta = p.id_consulta "
                        + "WHERE p.id_consulta = ? AND p.id_medico = ? FOR UPDATE", idConsulta, idMedico);
                if (p == null) {
                    conn.rollback();
                    return Resultado.falha("Prontuário não encontrado para este atendimento.");
                }
                if (!"rascunho".equals(p.get("status"))) {
                    conn.rollback();
                    return Resultado.falha("O prontuário já foi finalizado. Registre uma evolução ou adendo.");
                }
                Map<String, Object> anterior = new LinkedHashMap<>();
                Map<String, Object> novo = new LinkedHashMap<>();
                boolean mudou = false;
                for (String campo : CAMPOS) {
                    anterior.put(campo, p.get(campo));
                    if (!dados.containsKey(campo)) {
                        // campo nao enviado: mantem o valor atual (nao apaga por omissao)
                        novo.put(campo, p.get(campo));
                        continue;
                    }
                    String v = Sanitize.string(dados.get(campo));
                    Object valor = v.isBlank() ? null : (v.length() > 20000 ? v.substring(0, 20000) : v);
                    novo.put(campo, valor);
                    if (!java.util.Objects.equals(valor, p.get(campo))) {
                        mudou = true;
                    }
                }
                int versao = ((Number) p.get("versao")).intValue();
                long idProntuario = ((Number) p.get("id_prontuario")).longValue();
                if (mudou) {
                    Sql.executar(conn, "INSERT INTO prontuario_versoes (id_prontuario, versao, conteudo, alterado_por) VALUES (?, ?, ?, ?)",
                            idProntuario, versao, GSON.toJson(anterior), idUsuario);
                    Sql.executar(conn, "UPDATE prontuarios SET queixa_principal = ?, historia_doenca_atual = ?, historico_relevante = ?, "
                                    + "observacoes = ?, hipoteses_diagnosticas = ?, conduta = ?, versao = ? WHERE id_prontuario = ?",
                            novo.get("queixa_principal"), novo.get("historia_doenca_atual"), novo.get("historico_relevante"),
                            novo.get("observacoes"), novo.get("hipoteses_diagnosticas"), novo.get("conduta"), versao + 1, idProntuario);
                    Auditoria.registrar(conn, idUsuario, "prontuario.alterado", "prontuarios", idProntuario,
                            ((Number) p.get("id_paciente")).intValue(), "versao " + (versao + 1), null);
                }
                conn.commit();
                return new Resultado(true, idProntuario, null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao salvar prontuário", e);
        }
    }

    /** RN-PRT-03/04: finaliza o registro (imutavel) e a consulta (realizada). */
    public Resultado finalizar(int idConsulta, int idMedico, Map<String, Object> dados, int idUsuario) {
        Resultado salvo = salvarRascunho(idConsulta, idMedico, dados, idUsuario);
        if (!salvo.ok()) {
            return salvo;
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> p = Sql.um(conn, "SELECT p.id_prontuario, p.conduta, c.id_paciente FROM prontuarios p "
                        + "JOIN consultas c ON c.id_consulta = p.id_consulta WHERE p.id_consulta = ? AND p.id_medico = ? "
                        + "AND p.status = 'rascunho' FOR UPDATE", idConsulta, idMedico);
                if (p == null) {
                    conn.rollback();
                    return Resultado.falha("Prontuário não encontrado ou já finalizado.");
                }
                if (p.get("conduta") == null) {
                    conn.rollback();
                    return Resultado.falha("Registre a conduta antes de finalizar o atendimento.");
                }
                Sql.executar(conn, "UPDATE prontuarios SET status = 'finalizado', finalizado_em = NOW() WHERE id_prontuario = ?",
                        p.get("id_prontuario"));
                Sql.executar(conn, "UPDATE consultas SET status = 'realizada', fim_atendimento_em = NOW(), atualizado_por = ? "
                        + "WHERE id_consulta = ? AND status = 'em_atendimento'", idUsuario, idConsulta);
                Auditoria.registrar(conn, idUsuario, "atendimento.finalizado", "consultas", idConsulta,
                        ((Number) p.get("id_paciente")).intValue(), null, null);
                conn.commit();
                return new Resultado(true, ((Number) p.get("id_prontuario")).longValue(), null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao finalizar atendimento", e);
        }
    }

    /** Evolucao (durante ou apos) ou adendo (so apos finalizar). Apenas inclusao. */
    public Resultado registrarEvolucao(int idConsulta, int idMedico, String tipo, String texto, int idUsuario) {
        String t = Sanitize.string(texto).trim();
        if (t.length() < 3) {
            return Resultado.falha("Escreva o texto da evolução.");
        }
        if (!"evolucao".equals(tipo) && !"adendo".equals(tipo)) {
            return Resultado.falha("Tipo inválido.");
        }
        Map<String, Object> p = Sql.um("SELECT p.id_prontuario, p.status, c.id_paciente FROM prontuarios p "
                + "JOIN consultas c ON c.id_consulta = p.id_consulta WHERE p.id_consulta = ? AND p.id_medico = ?", idConsulta, idMedico);
        if (p == null) {
            return Resultado.falha("Prontuário não encontrado.");
        }
        if ("adendo".equals(tipo) && !"finalizado".equals(p.get("status"))) {
            return Resultado.falha("Adendo só depois de finalizar; antes disso, edite o rascunho.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.inserir(conn, "INSERT INTO evolucoes_clinicas (id_prontuario, id_autor, tipo, texto) VALUES (?, ?, ?, ?)",
                    p.get("id_prontuario"), idMedico, tipo, t.length() > 20000 ? t.substring(0, 20000) : t);
            Auditoria.registrar(conn, idUsuario, "prontuario." + tipo, "evolucoes_clinicas", id,
                    ((Number) p.get("id_paciente")).intValue(), null, null);
            return new Resultado(true, id, null);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar evolução", e);
        }
    }

    public List<Map<String, Object>> evolucoes(int idConsulta) {
        return Sql.lista("SELECT e.id_evolucao, e.tipo, e.texto, e.registrado_em, pr.nome AS autor FROM evolucoes_clinicas e "
                + "JOIN prontuarios p ON p.id_prontuario = e.id_prontuario JOIN profissionais pr ON pr.id_profissional = e.id_autor "
                + "WHERE p.id_consulta = ? ORDER BY e.registrado_em", idConsulta);
    }

    public int quantidadeDeVersoes(int idConsulta) {
        return (int) Sql.numero("SELECT COUNT(*) FROM prontuario_versoes v JOIN prontuarios p ON p.id_prontuario = v.id_prontuario "
                + "WHERE p.id_consulta = ?", idConsulta);
    }

    // ------------------------------------------------------------------ alergias

    public List<Map<String, Object>> alergias(int idPaciente) {
        return Sql.lista("SELECT id_alergia, substancia, reacao, gravidade, registrado_em FROM paciente_alergias "
                + "WHERE id_paciente = ? AND ativo = 1 ORDER BY gravidade = 'grave' DESC, substancia", idPaciente);
    }

    public Resultado registrarAlergia(int idPaciente, String substancia, String reacao, String gravidade, int idUsuario) {
        String s = Sanitize.string(substancia).trim();
        if (s.length() < 2) {
            return Resultado.falha("Informe a substância.");
        }
        String g = List.of("leve", "moderada", "grave").contains(gravidade) ? gravidade : null;
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.inserir(conn, "INSERT INTO paciente_alergias (id_paciente, substancia, reacao, gravidade, registrado_por) "
                    + "VALUES (?, ?, ?, ?, ?)", idPaciente, s.length() > 150 ? s.substring(0, 150) : s,
                    vazio(Sanitize.string(reacao)), g, idUsuario);
            Auditoria.registrar(conn, idUsuario, "alergia.registrada", "paciente_alergias", id, idPaciente, null, null);
            return new Resultado(true, id, null);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar alergia", e);
        }
    }

    // ------------------------------------------------------------------ historico clinico

    /** Atendimentos anteriores do paciente (prontuarios finalizados), mais recentes primeiro. */
    public List<Map<String, Object>> prontuariosDoPaciente(int idPaciente) {
        return Sql.lista("SELECT p.id_prontuario, p.id_consulta, p.status, p.queixa_principal, p.hipoteses_diagnosticas, p.conduta, "
                + "p.finalizado_em, c.data_hora, pr.nome AS medico, e.nome AS especialidade "
                + "FROM prontuarios p JOIN consultas c ON c.id_consulta = p.id_consulta "
                + "JOIN profissionais pr ON pr.id_profissional = p.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.id_paciente = ? AND p.status = 'finalizado' ORDER BY c.data_hora DESC", idPaciente);
    }

    private static Object vazio(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
