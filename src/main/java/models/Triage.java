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
public class Triage {

    /** Campo, rotulo, minimo, maximo (RN-TRI-04). */
    private static final Object[][] RANGES = {
            {"pa_sistolica", "PA sistólica", 50, 300},
            {"pa_diastolica", "PA diastólica", 20, 200},
            {"frequencia_cardiaca", "Frequência cardíaca", 20, 250},
            {"temperatura", "Temperatura", 30, 45},
            {"saturacao_o2", "Saturação de O₂", 0, 100},
            {"peso_kg", "Peso", 0.3, 400},
            {"altura_cm", "Altura", 20, 260},
    };

    public record Result(long id, Map<String, String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }

        static Result error(String field, String msg) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put(field, msg);
            return new Result(0, e);
        }
    }

    public List<Map<String, Object>> classifications() {
        return Sql.list("SELECT id_classificacao, codigo, nome, cor_hex, ordem, tempo_alvo_min "
                + "FROM classificacoes_prioridade WHERE ativo = 1 ORDER BY ordem");
    }

    /** Fila: aguardando e em triagem, por ordem de chegada (RF-TRI-01). */
    public List<Map<String, Object>> queue() {
        return Sql.list("SELECT c.id_consulta, c.status, c.chegada_em, c.data_hora, c.id_paciente, pa.nome AS paciente, "
                + "pa.data_nascimento, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.status IN ('aguardando_triagem', 'em_triagem') ORDER BY c.chegada_em");
    }

    /** Triagens registradas hoje pelo enfermeiro (para retificar ou consultar). */
    public List<Map<String, Object>> nurseRecent(int nurseId) {
        return Sql.list("SELECT t.id_triagem, t.id_consulta, t.registrado_em, pa.nome AS paciente, cp.nome AS prioridade, cp.cor_hex, "
                + "c.status, (SELECT COUNT(*) FROM triagens r WHERE r.retifica_id = t.id_triagem) AS retificada "
                + "FROM triagens t JOIN consultas c ON c.id_consulta = t.id_consulta "
                + "JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "WHERE t.id_enfermeiro = ? AND t.registrado_em >= CURRENT_DATE ORDER BY t.registrado_em DESC LIMIT 20", nurseId);
    }

    /** Consulta com o paciente, para o cabecalho da tela de triagem. */
    public Map<String, Object> appointment(int appointmentId) {
        return Sql.one("SELECT c.id_consulta, c.status, c.chegada_em, c.id_paciente, pa.nome AS paciente, pa.data_nascimento, pa.sexo, "
                + "pr.nome AS medico, e.nome AS especialidade FROM consultas c "
                + "JOIN pacientes pa ON pa.id_paciente = c.id_paciente JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade WHERE c.id_consulta = ?", appointmentId);
    }

    /** A triagem valida da consulta (a ultima, que nao foi retificada); null se ainda nao ha. */
    public Map<String, Object> current(int appointmentId) {
        return Sql.one("SELECT t.*, cp.nome AS prioridade, cp.cor_hex, cp.codigo, pr.nome AS enfermeiro, "
                + "(SELECT COUNT(*) FROM triagens x WHERE x.id_consulta = t.id_consulta) AS versoes "
                + "FROM triagens t JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "JOIN profissionais pr ON pr.id_profissional = t.id_enfermeiro "
                + "WHERE t.id_consulta = ? AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) "
                + "ORDER BY t.id_triagem DESC LIMIT 1", appointmentId);
    }

    /** RF-TRI-02: so um enfermeiro inicia; os outros veem "em triagem". */
    public boolean start(int appointmentId, int userId) {
        int n = Sql.execute("UPDATE consultas SET status = 'em_triagem', atualizado_por = ? "
                + "WHERE id_consulta = ? AND status = 'aguardando_triagem'", userId, appointmentId);
        return n == 1;
    }

    /** Devolve a consulta para a fila (ex.: enfermeiro abriu por engano). */
    public boolean sendBack(int appointmentId, int userId) {
        return Sql.execute("UPDATE consultas SET status = 'aguardando_triagem', atualizado_por = ? "
                + "WHERE id_consulta = ? AND status = 'em_triagem'", userId, appointmentId) == 1;
    }

    /** Registra a triagem e encaminha ao medico (consulta -> aguardando_atendimento). */
    public Result record(int appointmentId, int nurseId, Map<String, Object> data, int userId) {
        Map<String, Object> clean = new LinkedHashMap<>();
        Map<String, String> errors = validate(data, clean);
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = Sql.one(conn, "SELECT id_paciente, status FROM consultas WHERE id_consulta = ? FOR UPDATE", appointmentId);
                if (c == null || !List.of("em_triagem", "aguardando_triagem").contains(String.valueOf(c.get("status")))) {
                    conn.rollback();
                    return Result.error("geral", "Esta consulta não está aguardando triagem.");
                }
                if (Sql.one(conn, "SELECT 1 FROM triagens WHERE id_consulta = ?", appointmentId) != null) {
                    conn.rollback();
                    return Result.error("geral", "A consulta já foi triada. Use a retificação para corrigir.");
                }
                long id = insert(conn, appointmentId, nurseId, clean, null, null);
                Sql.execute(conn, "UPDATE consultas SET status = 'aguardando_atendimento', atualizado_por = ? WHERE id_consulta = ?",
                        userId, appointmentId);
                AuditLog.record(conn, userId, "triagem.registrada", "triagens", id,
                        ((Number) c.get("id_paciente")).intValue(), null, null);
                conn.commit();
                return new Result(id, errors);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar triagem", e);
        }
    }

    /** RN-TRI-03: nova triagem ligada a anterior, com motivo; a anterior fica guardada. */
    public Result amend(int previousTriageId, int nurseId, Map<String, Object> data, String reason, int userId) {
        String m = reason == null ? "" : Sanitize.string(reason).trim();
        if (m.length() < 3) {
            return Result.error("motivo_retificacao", "Informe o motivo da retificação.");
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        Map<String, String> errors = validate(data, clean);
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> ant = Sql.one(conn, "SELECT t.id_consulta, c.id_paciente, c.status FROM triagens t "
                        + "JOIN consultas c ON c.id_consulta = t.id_consulta WHERE t.id_triagem = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) FOR UPDATE", previousTriageId);
                if (ant == null) {
                    conn.rollback();
                    return Result.error("geral", "Triagem não encontrada ou já retificada.");
                }
                if (!List.of("aguardando_atendimento", "em_atendimento").contains(String.valueOf(ant.get("status")))) {
                    conn.rollback();
                    return Result.error("geral", "Só é possível retificar enquanto o atendimento não termina.");
                }
                int appointmentId = ((Number) ant.get("id_consulta")).intValue();
                long id = insert(conn, appointmentId, nurseId, clean, previousTriageId, m.length() > 255 ? m.substring(0, 255) : m);
                AuditLog.record(conn, userId, "triagem.retificada", "triagens", id,
                        ((Number) ant.get("id_paciente")).intValue(), "retifica " + previousTriageId, null);
                conn.commit();
                return new Result(id, errors);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (Sql.isDuplicate(e)) {
                return Result.error("geral", "Esta triagem acabou de ser retificada por outra pessoa.");
            }
            throw new RuntimeException("Falha ao retificar triagem", e);
        }
    }

    /** Triagens do paciente, para o historico (enfermagem e medico com vinculo). */
    public List<Map<String, Object>> ofPatient(int patientId) {
        return Sql.list("SELECT t.*, cp.nome AS prioridade, cp.cor_hex, pr.nome AS enfermeiro, c.data_hora AS consulta_em "
                + "FROM triagens t JOIN consultas c ON c.id_consulta = t.id_consulta "
                + "JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "JOIN profissionais pr ON pr.id_profissional = t.id_enfermeiro "
                + "WHERE c.id_paciente = ? ORDER BY t.registrado_em DESC", patientId);
    }

    // ------------------------------------------------------------------ auxiliares

    private static long insert(Connection conn, int appointmentId, int nurseId, Map<String, Object> d,
                                Integer amends, String reason) throws SQLException {
        return Sql.insert(conn, "INSERT INTO triagens (id_consulta, id_enfermeiro, pa_sistolica, pa_diastolica, frequencia_cardiaca, "
                        + "temperatura, saturacao_o2, peso_kg, altura_cm, queixa_principal, sintomas, observacoes, id_classificacao, "
                        + "retifica_id, motivo_retificacao) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                appointmentId, nurseId, d.get("pa_sistolica"), d.get("pa_diastolica"), d.get("frequencia_cardiaca"),
                d.get("temperatura"), d.get("saturacao_o2"), d.get("peso_kg"), d.get("altura_cm"), d.get("queixa_principal"),
                d.get("sintomas"), d.get("observacoes"), d.get("id_classificacao"), amends, reason);
    }

    Map<String, String> validate(Map<String, Object> data, Map<String, Object> clean) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (Object[] f : RANGES) {
            String field = (String) f[0];
            String raw = data.get(field) == null ? "" : String.valueOf(data.get(field)).trim().replace(',', '.');
            if (raw.isEmpty()) {
                clean.put(field, null);
                continue;
            }
            try {
                BigDecimal v = new BigDecimal(raw);
                double min = ((Number) f[2]).doubleValue();
                double max = ((Number) f[3]).doubleValue();
                if (v.doubleValue() < min || v.doubleValue() > max) {
                    errors.put(field, f[1] + " fora da faixa aceita (" + fmt(min) + " a " + fmt(max) + ").");
                } else {
                    boolean integer = !field.equals("temperatura") && !field.equals("peso_kg");
                    clean.put(field, integer ? (Object) v.intValue() : v);
                }
            } catch (NumberFormatException e) {
                errors.put(field, "Use apenas números.");
            }
        }
        Object sis = clean.get("pa_sistolica");
        Object day = clean.get("pa_diastolica");
        if ((sis == null) != (day == null) && !errors.containsKey("pa_sistolica") && !errors.containsKey("pa_diastolica")) {
            errors.put(sis == null ? "pa_sistolica" : "pa_diastolica", "Informe a pressão completa (sistólica e diastólica).");
        } else if (sis != null && day != null && (Integer) day >= (Integer) sis) {
            errors.put("pa_diastolica", "A diastólica deve ser menor que a sistólica.");
        }
        String complaint = Sanitize.string(data.get("queixa_principal"));
        if (complaint.length() < 3) {
            errors.put("queixa_principal", "Descreva a queixa principal.");
        }
        clean.put("queixa_principal", complaint.length() > 500 ? complaint.substring(0, 500) : complaint);
        clean.put("sintomas", nullEmpty(Sanitize.string(data.get("sintomas"))));
        clean.put("observacoes", nullEmpty(Sanitize.string(data.get("observacoes"))));
        int classValue = Sanitize.intVal(data.get("id_classificacao"), 0);
        if (Sql.number("SELECT COUNT(*) FROM classificacoes_prioridade WHERE id_classificacao = ? AND ativo = 1", classValue) == 0) {
            errors.put("id_classificacao", "Escolha a classificação de prioridade.");
        }
        clean.put("id_classificacao", classValue);
        return errors;
    }

    private static Object nullEmpty(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
