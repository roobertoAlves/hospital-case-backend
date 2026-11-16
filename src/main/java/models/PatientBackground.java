package models;

import database.Sql;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - PatientBackground model
 *
 * The patient's health background as in the CFM medical record model: pre-existing conditions,
 * surgeries, hospitalizations, continuous medication, lifestyle habits (smoking, alcohol, physical
 * activity, sleep, diet) and family history. One row per patient, valid across encounters; only a
 * doctor with a care link changes it (checked by the controller) and every change is audited.
 * Allergies stay in paciente_alergias.
 */
public class PatientBackground {

    public static final List<String> TEXT_FIELDS = List.of(
            "doencas_preexistentes", "cirurgias", "internacoes", "medicamentos_uso_continuo", "sono", "alimentacao",
            "historia_familiar");

    private static final Map<String, List<String>> CHOICES = Map.of(
            "tabagismo", List.of("nunca", "ex_fumante", "fumante"),
            "etilismo", List.of("nao", "social", "frequente"),
            "atividade_fisica", List.of("sedentario", "ate_2x_semana", "3x_ou_mais"));

    /** The patient's background, or null when nothing was recorded yet. */
    public Map<String, Object> of(int patientId) {
        return Sql.one("SELECT b.*, u.nome AS atualizado_por_nome FROM paciente_antecedentes b "
                + "LEFT JOIN usuarios u ON u.id_usuario = b.atualizado_por WHERE b.id_paciente = ?", patientId);
    }

    /** Validates and saves (insert or update). Returns per-field errors; empty when saved. */
    public Map<String, String> save(int patientId, Map<String, Object> data, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> clean = new LinkedHashMap<>();
        for (String field : TEXT_FIELDS) {
            String v = data.get(field) == null ? "" : Sanitize.string(data.get(field)).trim();
            int max = "sono".equals(field) || "alimentacao".equals(field) ? 255 : 5000;
            if (v.length() > max) {
                errors.put(field, "No máximo " + max + " caracteres.");
            }
            clean.put(field, v.isEmpty() ? null : v);
        }
        for (Map.Entry<String, List<String>> c : CHOICES.entrySet()) {
            String v = data.get(c.getKey()) == null ? "" : Sanitize.string(data.get(c.getKey())).trim();
            if (!v.isEmpty() && !c.getValue().contains(v)) {
                errors.put(c.getKey(), "Opção inválida.");
            }
            clean.put(c.getKey(), v.isEmpty() ? null : v);
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        // Column names come from the fixed lists above, never from the request.
        StringBuilder cols = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        StringBuilder updates = new StringBuilder();
        List<Object> params = new java.util.ArrayList<>();
        params.add(patientId);
        for (Map.Entry<String, Object> e : clean.entrySet()) {
            cols.append(", ").append(e.getKey());
            marks.append(", ?");
            updates.append(e.getKey()).append(" = VALUES(").append(e.getKey()).append("), ");
            params.add(e.getValue());
        }
        params.add(userId);
        Sql.execute("INSERT INTO paciente_antecedentes (id_paciente" + cols + ", atualizado_por) VALUES (?" + marks + ", ?) "
                + "ON DUPLICATE KEY UPDATE " + updates + "atualizado_por = VALUES(atualizado_por), atualizado_em = NOW()",
                params.toArray());
        AuditLog.record(userId, "antecedentes.alterados", "pacientes", patientId, patientId, null, null);
        return errors;
    }
}
