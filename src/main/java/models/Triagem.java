package models;

import database.DBConnection;
import database.Sql;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Triagem
 *
 * Triagem de enfermagem (RF-TRI-01..07, RN-TRI-01..05):
 * - a fila e a ordem de chegada; ao iniciar, a consulta passa a "em_triagem" e sai da fila
 *   dos outros enfermeiros (UPDATE condicional: so um consegue);
 * - sinais vitais conferidos aqui com mensagens por campo (o banco repete as faixas em CHECK);
 * - a prioridade e escolhida pelo enfermeiro: o sistema nao calcula nem sugere;
 * - triagem gravada nao muda (trigger no banco): correcao = nova triagem com retifica_id e motivo.
 */
public class Triagem {

    /** Campo, rotulo, minimo, maximo (RN-TRI-04). */
    private static final Object[][] FAIXAS = {
            {"pa_sistolica", "PA sistólica", 50, 300},
            {"pa_diastolica", "PA diastólica", 20, 200},
            {"frequencia_cardiaca", "Frequência cardíaca", 20, 250},
            {"temperatura", "Temperatura", 30, 45},
            {"saturacao_o2", "Saturação de O₂", 0, 100},
            {"peso_kg", "Peso", 0.3, 400},
            {"altura_cm", "Altura", 20, 260},
    };

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

    public List<Map<String, Object>> classificacoes() {
        return Sql.lista("SELECT id_classificacao, codigo, nome, cor_hex, ordem, tempo_alvo_min "
                + "FROM classificacoes_prioridade WHERE ativo = 1 ORDER BY ordem");
    }

    /** Fila: aguardando e em triagem, por ordem de chegada (RF-TRI-01). */
    public List<Map<String, Object>> fila() {
        return Sql.lista("SELECT c.id_consulta, c.status, c.chegada_em, c.data_hora, c.id_paciente, pa.nome AS paciente, "
                + "pa.data_nascimento, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.status IN ('aguardando_triagem', 'em_triagem') ORDER BY c.chegada_em");
    }

    /** Triagens registradas hoje pelo enfermeiro (para retificar ou consultar). */
    public List<Map<String, Object>> recentesDoEnfermeiro(int idEnfermeiro) {
        return Sql.lista("SELECT t.id_triagem, t.id_consulta, t.registrado_em, pa.nome AS paciente, cp.nome AS prioridade, cp.cor_hex, "
                + "c.status, (SELECT COUNT(*) FROM triagens r WHERE r.retifica_id = t.id_triagem) AS retificada "
                + "FROM triagens t JOIN consultas c ON c.id_consulta = t.id_consulta "
                + "JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "WHERE t.id_enfermeiro = ? AND t.registrado_em >= CURRENT_DATE ORDER BY t.registrado_em DESC LIMIT 20", idEnfermeiro);
    }

    /** Consulta com o paciente, para o cabecalho da tela de triagem. */
    public Map<String, Object> consulta(int idConsulta) {
        return Sql.um("SELECT c.id_consulta, c.status, c.chegada_em, c.id_paciente, pa.nome AS paciente, pa.data_nascimento, pa.sexo, "
                + "pr.nome AS medico, e.nome AS especialidade FROM consultas c "
                + "JOIN pacientes pa ON pa.id_paciente = c.id_paciente JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade WHERE c.id_consulta = ?", idConsulta);
    }

    /** A triagem valida da consulta (a ultima, que nao foi retificada); null se ainda nao ha. */
    public Map<String, Object> atual(int idConsulta) {
        return Sql.um("SELECT t.*, cp.nome AS prioridade, cp.cor_hex, cp.codigo, pr.nome AS enfermeiro, "
                + "(SELECT COUNT(*) FROM triagens x WHERE x.id_consulta = t.id_consulta) AS versoes "
                + "FROM triagens t JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "JOIN profissionais pr ON pr.id_profissional = t.id_enfermeiro "
                + "WHERE t.id_consulta = ? AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) "
                + "ORDER BY t.id_triagem DESC LIMIT 1", idConsulta);
    }

    /** RF-TRI-02: so um enfermeiro inicia; os outros veem "em triagem". */
    public boolean iniciar(int idConsulta, int idUsuario) {
        int n = Sql.executar("UPDATE consultas SET status = 'em_triagem', atualizado_por = ? "
                + "WHERE id_consulta = ? AND status = 'aguardando_triagem'", idUsuario, idConsulta);
        return n == 1;
    }

    /** Devolve a consulta para a fila (ex.: enfermeiro abriu por engano). */
    public boolean devolver(int idConsulta, int idUsuario) {
        return Sql.executar("UPDATE consultas SET status = 'aguardando_triagem', atualizado_por = ? "
                + "WHERE id_consulta = ? AND status = 'em_triagem'", idUsuario, idConsulta) == 1;
    }

    /** Registra a triagem e encaminha ao medico (consulta -> aguardando_atendimento). */
    public Resultado registrar(int idConsulta, int idEnfermeiro, Map<String, Object> dados, int idUsuario) {
        Map<String, Object> limpos = new LinkedHashMap<>();
        Map<String, String> erros = validar(dados, limpos);
        if (!erros.isEmpty()) {
            return new Resultado(0, erros);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = Sql.um(conn, "SELECT id_paciente, status FROM consultas WHERE id_consulta = ? FOR UPDATE", idConsulta);
                if (c == null || !List.of("em_triagem", "aguardando_triagem").contains(String.valueOf(c.get("status")))) {
                    conn.rollback();
                    return Resultado.erro("geral", "Esta consulta não está aguardando triagem.");
                }
                if (Sql.um(conn, "SELECT 1 FROM triagens WHERE id_consulta = ?", idConsulta) != null) {
                    conn.rollback();
                    return Resultado.erro("geral", "A consulta já foi triada. Use a retificação para corrigir.");
                }
                long id = inserir(conn, idConsulta, idEnfermeiro, limpos, null, null);
                Sql.executar(conn, "UPDATE consultas SET status = 'aguardando_atendimento', atualizado_por = ? WHERE id_consulta = ?",
                        idUsuario, idConsulta);
                Auditoria.registrar(conn, idUsuario, "triagem.registrada", "triagens", id,
                        ((Number) c.get("id_paciente")).intValue(), null, null);
                conn.commit();
                return new Resultado(id, erros);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar triagem", e);
        }
    }

    /** RN-TRI-03: nova triagem ligada a anterior, com motivo; a anterior fica guardada. */
    public Resultado retificar(int idTriagemAnterior, int idEnfermeiro, Map<String, Object> dados, String motivo, int idUsuario) {
        String m = motivo == null ? "" : Sanitize.string(motivo).trim();
        if (m.length() < 3) {
            return Resultado.erro("motivo_retificacao", "Informe o motivo da retificação.");
        }
        Map<String, Object> limpos = new LinkedHashMap<>();
        Map<String, String> erros = validar(dados, limpos);
        if (!erros.isEmpty()) {
            return new Resultado(0, erros);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> ant = Sql.um(conn, "SELECT t.id_consulta, c.id_paciente, c.status FROM triagens t "
                        + "JOIN consultas c ON c.id_consulta = t.id_consulta WHERE t.id_triagem = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) FOR UPDATE", idTriagemAnterior);
                if (ant == null) {
                    conn.rollback();
                    return Resultado.erro("geral", "Triagem não encontrada ou já retificada.");
                }
                if (!List.of("aguardando_atendimento", "em_atendimento").contains(String.valueOf(ant.get("status")))) {
                    conn.rollback();
                    return Resultado.erro("geral", "Só é possível retificar enquanto o atendimento não termina.");
                }
                int idConsulta = ((Number) ant.get("id_consulta")).intValue();
                long id = inserir(conn, idConsulta, idEnfermeiro, limpos, idTriagemAnterior, m.length() > 255 ? m.substring(0, 255) : m);
                Auditoria.registrar(conn, idUsuario, "triagem.retificada", "triagens", id,
                        ((Number) ant.get("id_paciente")).intValue(), "retifica " + idTriagemAnterior, null);
                conn.commit();
                return new Resultado(id, erros);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (Sql.ehDuplicidade(e)) {
                return Resultado.erro("geral", "Esta triagem acabou de ser retificada por outra pessoa.");
            }
            throw new RuntimeException("Falha ao retificar triagem", e);
        }
    }

    /** Triagens do paciente, para o historico (enfermagem e medico com vinculo). */
    public List<Map<String, Object>> doPaciente(int idPaciente) {
        return Sql.lista("SELECT t.*, cp.nome AS prioridade, cp.cor_hex, pr.nome AS enfermeiro, c.data_hora AS consulta_em "
                + "FROM triagens t JOIN consultas c ON c.id_consulta = t.id_consulta "
                + "JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "JOIN profissionais pr ON pr.id_profissional = t.id_enfermeiro "
                + "WHERE c.id_paciente = ? ORDER BY t.registrado_em DESC", idPaciente);
    }

    // ------------------------------------------------------------------ auxiliares

    private static long inserir(Connection conn, int idConsulta, int idEnfermeiro, Map<String, Object> d,
                                Integer retifica, String motivo) throws SQLException {
        return Sql.inserir(conn, "INSERT INTO triagens (id_consulta, id_enfermeiro, pa_sistolica, pa_diastolica, frequencia_cardiaca, "
                        + "temperatura, saturacao_o2, peso_kg, altura_cm, queixa_principal, sintomas, observacoes, id_classificacao, "
                        + "retifica_id, motivo_retificacao) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                idConsulta, idEnfermeiro, d.get("pa_sistolica"), d.get("pa_diastolica"), d.get("frequencia_cardiaca"),
                d.get("temperatura"), d.get("saturacao_o2"), d.get("peso_kg"), d.get("altura_cm"), d.get("queixa_principal"),
                d.get("sintomas"), d.get("observacoes"), d.get("id_classificacao"), retifica, motivo);
    }

    Map<String, String> validar(Map<String, Object> dados, Map<String, Object> limpos) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (Object[] f : FAIXAS) {
            String campo = (String) f[0];
            String bruto = dados.get(campo) == null ? "" : String.valueOf(dados.get(campo)).trim().replace(',', '.');
            if (bruto.isEmpty()) {
                limpos.put(campo, null);
                continue;
            }
            try {
                BigDecimal v = new BigDecimal(bruto);
                double min = ((Number) f[2]).doubleValue();
                double max = ((Number) f[3]).doubleValue();
                if (v.doubleValue() < min || v.doubleValue() > max) {
                    erros.put(campo, f[1] + " fora da faixa aceita (" + fmt(min) + " a " + fmt(max) + ").");
                } else {
                    boolean inteiro = !campo.equals("temperatura") && !campo.equals("peso_kg");
                    limpos.put(campo, inteiro ? (Object) v.intValue() : v);
                }
            } catch (NumberFormatException e) {
                erros.put(campo, "Use apenas números.");
            }
        }
        Object sis = limpos.get("pa_sistolica");
        Object dia = limpos.get("pa_diastolica");
        if ((sis == null) != (dia == null) && !erros.containsKey("pa_sistolica") && !erros.containsKey("pa_diastolica")) {
            erros.put(sis == null ? "pa_sistolica" : "pa_diastolica", "Informe a pressão completa (sistólica e diastólica).");
        } else if (sis != null && dia != null && (Integer) dia >= (Integer) sis) {
            erros.put("pa_diastolica", "A diastólica deve ser menor que a sistólica.");
        }
        String queixa = Sanitize.string(dados.get("queixa_principal"));
        if (queixa.length() < 3) {
            erros.put("queixa_principal", "Descreva a queixa principal.");
        }
        limpos.put("queixa_principal", queixa.length() > 500 ? queixa.substring(0, 500) : queixa);
        limpos.put("sintomas", vazioNulo(Sanitize.string(dados.get("sintomas"))));
        limpos.put("observacoes", vazioNulo(Sanitize.string(dados.get("observacoes"))));
        int classe = Sanitize.intVal(dados.get("id_classificacao"), 0);
        if (Sql.numero("SELECT COUNT(*) FROM classificacoes_prioridade WHERE id_classificacao = ? AND ativo = 1", classe) == 0) {
            erros.put("id_classificacao", "Escolha a classificação de prioridade.");
        }
        limpos.put("id_classificacao", classe);
        return erros;
    }

    private static Object vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
