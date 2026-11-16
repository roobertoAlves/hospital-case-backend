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
public class Encounter {

    private static final Gson GSON = new Gson();

    public static final List<String> FIELDS = List.of(
            "queixa_principal", "historia_doenca_atual", "historico_relevante",
            "observacoes", "hipoteses_diagnosticas", "conduta");

    public record Result(boolean ok, long id, String error) {
        static Result failure(String error) {
            return new Result(false, 0, error);
        }
    }

    /** Fila e atendimentos em curso do medico (prioridade, depois chegada). */
    public List<Map<String, Object>> doctorQueue(int doctorId) {
        return Sql.list("SELECT c.id_consulta, c.status, c.chegada_em, c.data_hora, c.id_paciente, pa.nome AS paciente, "
                + "pa.data_nascimento, e.nome AS especialidade, cp.nome AS prioridade, cp.cor_hex, cp.ordem, t.queixa_principal "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "LEFT JOIN triagens t ON t.id_consulta = c.id_consulta "
                + " AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) "
                + "LEFT JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "WHERE c.id_medico = ? AND c.status IN ('aguardando_atendimento', 'em_atendimento') "
                + "ORDER BY c.status = 'em_atendimento' DESC, COALESCE(cp.ordem, 99), c.chegada_em", doctorId);
    }

    /** Consulta do medico com paciente e prontuario; null se a consulta nao for dele. */
    public Map<String, Object> doctorAppointment(int appointmentId, int doctorId) {
        return Sql.one("SELECT c.id_consulta, c.status, c.data_hora, c.chegada_em, c.inicio_atendimento_em, c.id_paciente, "
                + "c.id_medico, pa.nome AS paciente, pa.data_nascimento, pa.sexo, pa.cpf, e.nome AS especialidade, "
                + "p.id_prontuario, p.status AS status_prontuario, p.versao, p.finalizado_em, p.atualizado_em AS prontuario_atualizado_em, "
                + "p.queixa_principal, p.historia_doenca_atual, p.historico_relevante, p.observacoes, p.hipoteses_diagnosticas, p.conduta "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "LEFT JOIN prontuarios p ON p.id_consulta = c.id_consulta "
                + "WHERE c.id_consulta = ? AND c.id_medico = ?", appointmentId, doctorId);
    }

    public Result start(int appointmentId, int doctorId, int userId) {
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = Sql.one(conn, "SELECT id_paciente, status FROM consultas WHERE id_consulta = ? AND id_medico = ? FOR UPDATE",
                        appointmentId, doctorId);
                if (c == null) {
                    conn.rollback();
                    return Result.failure("Consulta não encontrada na sua agenda.");
                }
                if (!"aguardando_atendimento".equals(c.get("status"))) {
                    conn.rollback();
                    return Result.failure("O paciente ainda não foi triado ou o atendimento já começou.");
                }
                Map<String, Object> t = Sql.one(conn, "SELECT queixa_principal FROM triagens t WHERE id_consulta = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) ORDER BY id_triagem DESC LIMIT 1", appointmentId);
                Sql.execute(conn, "UPDATE consultas SET status = 'em_atendimento', inicio_atendimento_em = NOW(), atualizado_por = ? "
                        + "WHERE id_consulta = ?", userId, appointmentId);
                long id = Sql.insert(conn, "INSERT INTO prontuarios (id_consulta, id_medico, queixa_principal) VALUES (?, ?, ?)",
                        appointmentId, doctorId, t != null ? t.get("queixa_principal") : null);
                AuditLog.record(conn, userId, "atendimento.iniciado", "consultas", appointmentId,
                        ((Number) c.get("id_paciente")).intValue(), null, null);
                conn.commit();
                return new Result(true, id, null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao iniciar atendimento", e);
        }
    }

    /** RN-PRT-02: so o autor altera o rascunho; a versao anterior e guardada. */
    public Result saveDraft(int appointmentId, int doctorId, Map<String, Object> data, int userId) {
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> p = Sql.one(conn, "SELECT p.*, c.id_paciente FROM prontuarios p JOIN consultas c ON c.id_consulta = p.id_consulta "
                        + "WHERE p.id_consulta = ? AND p.id_medico = ? FOR UPDATE", appointmentId, doctorId);
                if (p == null) {
                    conn.rollback();
                    return Result.failure("Prontuário não encontrado para este atendimento.");
                }
                if (!"rascunho".equals(p.get("status"))) {
                    conn.rollback();
                    return Result.failure("O prontuário já foi finalizado. Registre uma evolução ou adendo.");
                }
                Map<String, Object> previous = new LinkedHashMap<>();
                Map<String, Object> newForm = new LinkedHashMap<>();
                boolean changed = false;
                for (String field : FIELDS) {
                    previous.put(field, p.get(field));
                    if (!data.containsKey(field)) {
                        // campo nao enviado: mantem o valor atual (nao apaga por omissao)
                        newForm.put(field, p.get(field));
                        continue;
                    }
                    String v = Sanitize.string(data.get(field));
                    Object value = v.isBlank() ? null : (v.length() > 20000 ? v.substring(0, 20000) : v);
                    newForm.put(field, value);
                    if (!java.util.Objects.equals(value, p.get(field))) {
                        changed = true;
                    }
                }
                int version = ((Number) p.get("versao")).intValue();
                long medicalRecordId = ((Number) p.get("id_prontuario")).longValue();
                if (changed) {
                    Sql.execute(conn, "INSERT INTO prontuario_versoes (id_prontuario, versao, conteudo, alterado_por) VALUES (?, ?, ?, ?)",
                            medicalRecordId, version, GSON.toJson(previous), userId);
                    Sql.execute(conn, "UPDATE prontuarios SET queixa_principal = ?, historia_doenca_atual = ?, historico_relevante = ?, "
                                    + "observacoes = ?, hipoteses_diagnosticas = ?, conduta = ?, versao = ? WHERE id_prontuario = ?",
                            newForm.get("queixa_principal"), newForm.get("historia_doenca_atual"), newForm.get("historico_relevante"),
                            newForm.get("observacoes"), newForm.get("hipoteses_diagnosticas"), newForm.get("conduta"), version + 1, medicalRecordId);
                    AuditLog.record(conn, userId, "prontuario.alterado", "prontuarios", medicalRecordId,
                            ((Number) p.get("id_paciente")).intValue(), "versao " + (version + 1), null);
                }
                conn.commit();
                return new Result(true, medicalRecordId, null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao salvar prontuário", e);
        }
    }

    /** RN-PRT-03/04: finaliza o registro (imutavel) e a consulta (realizada). */
    public Result finish(int appointmentId, int doctorId, Map<String, Object> data, int userId) {
        Result saved = saveDraft(appointmentId, doctorId, data, userId);
        if (!saved.ok()) {
            return saved;
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> p = Sql.one(conn, "SELECT p.id_prontuario, p.conduta, c.id_paciente FROM prontuarios p "
                        + "JOIN consultas c ON c.id_consulta = p.id_consulta WHERE p.id_consulta = ? AND p.id_medico = ? "
                        + "AND p.status = 'rascunho' FOR UPDATE", appointmentId, doctorId);
                if (p == null) {
                    conn.rollback();
                    return Result.failure("Prontuário não encontrado ou já finalizado.");
                }
                if (p.get("conduta") == null) {
                    conn.rollback();
                    return Result.failure("Registre a conduta antes de finalizar o atendimento.");
                }
                Sql.execute(conn, "UPDATE prontuarios SET status = 'finalizado', finalizado_em = NOW() WHERE id_prontuario = ?",
                        p.get("id_prontuario"));
                Sql.execute(conn, "UPDATE consultas SET status = 'realizada', fim_atendimento_em = NOW(), atualizado_por = ? "
                        + "WHERE id_consulta = ? AND status = 'em_atendimento'", userId, appointmentId);
                AuditLog.record(conn, userId, "atendimento.finalizado", "consultas", appointmentId,
                        ((Number) p.get("id_paciente")).intValue(), null, null);
                conn.commit();
                return new Result(true, ((Number) p.get("id_prontuario")).longValue(), null);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao finalizar atendimento", e);
        }
    }

    /** Evolucao (durante ou apos) ou adendo (so apos finalizar). Apenas inclusao. */
    public Result recordProgressNote(int appointmentId, int doctorId, String type, String text, int userId) {
        String t = Sanitize.string(text).trim();
        if (t.length() < 3) {
            return Result.failure("Escreva o texto da evolução.");
        }
        if (!"evolucao".equals(type) && !"adendo".equals(type)) {
            return Result.failure("Tipo inválido.");
        }
        Map<String, Object> p = Sql.one("SELECT p.id_prontuario, p.status, c.id_paciente FROM prontuarios p "
                + "JOIN consultas c ON c.id_consulta = p.id_consulta WHERE p.id_consulta = ? AND p.id_medico = ?", appointmentId, doctorId);
        if (p == null) {
            return Result.failure("Prontuário não encontrado.");
        }
        if ("adendo".equals(type) && !"finalizado".equals(p.get("status"))) {
            return Result.failure("Adendo só depois de finalizar; antes disso, edite o rascunho.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO evolucoes_clinicas (id_prontuario, id_autor, tipo, texto) VALUES (?, ?, ?, ?)",
                    p.get("id_prontuario"), doctorId, type, t.length() > 20000 ? t.substring(0, 20000) : t);
            AuditLog.record(conn, userId, "prontuario." + type, "evolucoes_clinicas", id,
                    ((Number) p.get("id_paciente")).intValue(), null, null);
            return new Result(true, id, null);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar evolução", e);
        }
    }

    public List<Map<String, Object>> progressNotes(int appointmentId) {
        return Sql.list("SELECT e.id_evolucao, e.tipo, e.texto, e.registrado_em, pr.nome AS autor FROM evolucoes_clinicas e "
                + "JOIN prontuarios p ON p.id_prontuario = e.id_prontuario JOIN profissionais pr ON pr.id_profissional = e.id_autor "
                + "WHERE p.id_consulta = ? ORDER BY e.registrado_em", appointmentId);
    }

    public int versionCount(int appointmentId) {
        return (int) Sql.number("SELECT COUNT(*) FROM prontuario_versoes v JOIN prontuarios p ON p.id_prontuario = v.id_prontuario "
                + "WHERE p.id_consulta = ?", appointmentId);
    }

    // ------------------------------------------------------------------ alergias

    public List<Map<String, Object>> allergies(int patientId) {
        return Sql.list("SELECT id_alergia, substancia, reacao, gravidade, registrado_em FROM paciente_alergias "
                + "WHERE id_paciente = ? AND ativo = 1 ORDER BY gravidade = 'grave' DESC, substancia", patientId);
    }

    public Result recordAllergy(int patientId, String substance, String reaction, String severity, int userId) {
        String s = Sanitize.string(substance).trim();
        if (s.length() < 2) {
            return Result.failure("Informe a substância.");
        }
        String g = List.of("leve", "moderada", "grave").contains(severity) ? severity : null;
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO paciente_alergias (id_paciente, substancia, reacao, gravidade, registrado_por) "
                    + "VALUES (?, ?, ?, ?, ?)", patientId, s.length() > 150 ? s.substring(0, 150) : s,
                    empty(Sanitize.string(reaction)), g, userId);
            AuditLog.record(conn, userId, "alergia.registrada", "paciente_alergias", id, patientId, null, null);
            return new Result(true, id, null);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar alergia", e);
        }
    }

    // ------------------------------------------------------------------ historico clinico

    /** Atendimentos anteriores do paciente (prontuarios finalizados), mais recentes primeiro. */
    public List<Map<String, Object>> patientMedicalRecords(int patientId) {
        return Sql.list("SELECT p.id_prontuario, p.id_consulta, p.status, p.queixa_principal, p.hipoteses_diagnosticas, p.conduta, "
                + "p.finalizado_em, c.data_hora, pr.nome AS medico, e.nome AS especialidade "
                + "FROM prontuarios p JOIN consultas c ON c.id_consulta = p.id_consulta "
                + "JOIN profissionais pr ON pr.id_profissional = p.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.id_paciente = ? AND p.status = 'finalizado' ORDER BY c.data_hora DESC", patientId);
    }

    private static Object empty(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
