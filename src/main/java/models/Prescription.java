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
public class Prescription {

    public static final int MAX_ITEMS = 20;

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

    public Result issue(int appointmentId, int doctorId, String instructions, List<?> rawItems, int userId) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, String> errors = validateItems(rawItems, items);
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = doctorAppointment(conn, appointmentId, doctorId);
                if (c == null) {
                    conn.rollback();
                    return Result.error("geral", "Só o médico do atendimento emite receita, durante ou logo após a consulta.");
                }
                String orient = Sanitize.string(instructions).trim();
                long id = Sql.insert(conn, "INSERT INTO receitas (id_consulta, id_medico, orientacoes_gerais) VALUES (?, ?, ?)",
                        appointmentId, doctorId, orient.isEmpty() ? null : cuts(orient, 5000));
                int order = 1;
                for (Map<String, Object> i : items) {
                    Sql.execute(conn, "INSERT INTO receita_itens (id_receita, ordem, medicamento, dosagem, via, frequencia, duracao, orientacoes) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, order++, i.get("medicamento"), i.get("dosagem"), i.get("via"),
                            i.get("frequencia"), i.get("duracao"), i.get("orientacoes"));
                }
                AuditLog.record(conn, userId, "receita.emitida", "receitas", id,
                        ((Number) c.get("id_paciente")).intValue(), items.size() + " itens", null);
                conn.commit();
                return new Result(id, errors);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao emitir receita", e);
        }
    }

    public Result cancel(int prescriptionId, int doctorId, String reason, int userId) {
        String m = Sanitize.string(reason).trim();
        if (m.length() < 3) {
            return Result.error("motivo", "Informe o motivo do cancelamento.");
        }
        Map<String, Object> r = Sql.one("SELECT r.id_receita, c.id_paciente FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta "
                + "WHERE r.id_receita = ? AND r.id_medico = ? AND r.status = 'emitida'", prescriptionId, doctorId);
        if (r == null) {
            return Result.error("geral", "Receita não encontrada, de outro médico ou já cancelada.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            int n = Sql.execute(conn, "UPDATE receitas SET status = 'cancelada', cancelada_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_receita = ? AND status = 'emitida'", cuts(m, 255), prescriptionId);
            if (n == 1) {
                AuditLog.record(conn, userId, "receita.cancelada", "receitas", prescriptionId,
                        ((Number) r.get("id_paciente")).intValue(), null, null);
            }
            return new Result(prescriptionId, new LinkedHashMap<>());
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao cancelar receita", e);
        }
    }

    /** Receitas da consulta com seus itens (mais recentes primeiro). */
    public List<Map<String, Object>> ofAppointment(int appointmentId) {
        List<Map<String, Object>> prescriptions = Sql.list("SELECT id_receita, status, orientacoes_gerais, emitida_em, cancelada_em, "
                + "motivo_cancelamento FROM receitas WHERE id_consulta = ? ORDER BY emitida_em DESC, id_receita DESC", appointmentId);
        for (Map<String, Object> r : prescriptions) {
            r.put("itens", items(((Number) r.get("id_receita")).intValue()));
        }
        return prescriptions;
    }

    public List<Map<String, Object>> ofPatient(int patientId) {
        List<Map<String, Object>> prescriptions = Sql.list("SELECT r.id_receita, r.status, r.emitida_em, pr.nome AS medico FROM receitas r "
                + "JOIN consultas c ON c.id_consulta = r.id_consulta JOIN profissionais pr ON pr.id_profissional = r.id_medico "
                + "WHERE c.id_paciente = ? ORDER BY r.emitida_em DESC", patientId);
        for (Map<String, Object> r : prescriptions) {
            r.put("itens", items(((Number) r.get("id_receita")).intValue()));
        }
        return prescriptions;
    }

    /** Dados para imprimir; null se a receita nao for do medico (sem vazar se existe). */
    public Map<String, Object> forPrint(int prescriptionId, int doctorId) {
        Map<String, Object> r = Sql.one("SELECT r.id_receita, r.id_consulta, r.status, r.orientacoes_gerais, r.emitida_em, r.motivo_cancelamento, "
                + "pr.nome AS medico, m.crm, m.crm_uf, pa.id_paciente, pa.nome AS paciente, pa.data_nascimento, pa.cpf, "
                + "pa.logradouro, pa.numero, pa.cidade, pa.uf "
                + "FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = r.id_medico JOIN medicos m ON m.id_profissional = r.id_medico "
                + "WHERE r.id_receita = ? AND r.id_medico = ?", prescriptionId, doctorId);
        if (r != null) {
            r.put("itens", items(prescriptionId));
        }
        return r;
    }

    /** Receita para o portal: so a do proprio paciente; null se for de outra pessoa (sem vazar se existe). */
    public Map<String, Object> forPatientPrint(int prescriptionId, int patientId) {
        Map<String, Object> r = Sql.one("SELECT r.id_receita, r.id_consulta, r.status, r.orientacoes_gerais, r.emitida_em, r.motivo_cancelamento, "
                + "pr.nome AS medico, m.crm, m.crm_uf, pa.id_paciente, pa.nome AS paciente, pa.data_nascimento, pa.cpf, "
                + "pa.logradouro, pa.numero, pa.cidade, pa.uf "
                + "FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = r.id_medico JOIN medicos m ON m.id_profissional = r.id_medico "
                + "WHERE r.id_receita = ? AND c.id_paciente = ?", prescriptionId, patientId);
        if (r != null) {
            r.put("itens", items(prescriptionId));
        }
        return r;
    }

    private List<Map<String, Object>> items(int prescriptionId) {
        return Sql.list("SELECT ordem, medicamento, dosagem, via, frequencia, duracao, orientacoes FROM receita_itens "
                + "WHERE id_receita = ? ORDER BY ordem", prescriptionId);
    }

    // ------------------------------------------------------------------ validacao

    static Map<String, String> validateItems(List<?> raw, List<Map<String, Object>> output) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            errors.put("itens", "Inclua ao menos um medicamento.");
            return errors;
        }
        if (raw.size() > MAX_ITEMS) {
            errors.put("itens", "No máximo " + MAX_ITEMS + " medicamentos por receita.");
            return errors;
        }
        int n = 1;
        for (Object o : raw) {
            if (!(o instanceof Map)) {
                errors.put("itens", "Formato de item inválido.");
                return errors;
            }
            Map<?, ?> b = (Map<?, ?>) o;
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("medicamento", text(b.get("medicamento"), 200));
            i.put("dosagem", text(b.get("dosagem"), 100));
            i.put("via", text(b.get("via"), 50));
            i.put("frequencia", text(b.get("frequencia"), 100));
            i.put("duracao", text(b.get("duracao"), 100));
            i.put("orientacoes", text(b.get("orientacoes"), 500));
            for (String required : List.of("medicamento", "dosagem", "frequencia", "duracao")) {
                if (i.get(required) == null) {
                    errors.put("itens", "Item " + n + ": preencha medicamento, dosagem, frequência e duração.");
                    return errors;
                }
            }
            output.add(i);
            n++;
        }
        return errors;
    }

    private static Map<String, Object> doctorAppointment(Connection conn, int appointmentId, int doctorId) throws SQLException {
        return Sql.one(conn, "SELECT id_paciente FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", appointmentId, doctorId);
    }

    private static String text(Object v, int max) {
        String s = v == null ? "" : Sanitize.string(String.valueOf(v)).trim();
        return s.isEmpty() ? null : cuts(s, max);
    }

    static String cuts(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
