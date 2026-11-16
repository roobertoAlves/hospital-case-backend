package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;
import utils.Validation;

/**
 * ZTF_J2EE - Patient model
 *
 * Patient registry (RF-PAC-01..06): search by name or CPF with pagination, create and edit with
 * server-side validation, logical deletion (ativo) and the administrative history (scheduled
 * appointments and exams). No clinical data leaves this model.
 */
public class Patient {

    /** Editable form fields, in screen order. */
    public static final List<String> FIELDS = List.of(
            "nome", "cpf", "data_nascimento", "sexo", "telefone", "email",
            "cep", "logradouro", "numero", "complemento", "bairro", "cidade", "uf",
            "contato_emergencia_nome", "contato_emergencia_telefone", "contato_emergencia_parentesco");

    private static final List<String> SEXES = List.of("feminino", "masculino", "outro", "nao_informado");

    public record Page(List<Map<String, Object>> items, long total, int page, int byPage) {
        public int totalPages() {
            return (int) Math.max(1, (total + byPage - 1) / byPage);
        }
    }

    /** Result of create/update: the saved id or the per-field errors. */
    public record Result(long id, Map<String, String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.ofHours(-3));
    }

    /** Search by (partial) name or CPF (digits), with pagination. Without a term, lists by name. */
    public Page search(String term, boolean includeInactive, int page, int byPage) {
        String t = term == null ? "" : term.trim();
        String digits = Validation.digits(t);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!includeInactive) {
            where.append(" AND ativo = 1");
        }
        if (!t.isEmpty()) {
            if (!digits.isEmpty() && digits.length() == t.replaceAll("[.\\-\\s]", "").length()) {
                where.append(" AND cpf LIKE ?");
                params.add(digits + "%");
            } else {
                where.append(" AND nome LIKE ?");
                params.add("%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
        }
        long total = Sql.number("SELECT COUNT(*) FROM pacientes" + where, params.toArray());
        int p = Math.max(1, page);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(byPage);
        pageParams.add((p - 1) * byPage);
        List<Map<String, Object>> items = Sql.list(
                "SELECT id_paciente, nome, cpf, data_nascimento, telefone, ativo FROM pacientes" + where
                        + " ORDER BY nome LIMIT ? OFFSET ?", pageParams.toArray());
        return new Page(items, total, p, byPage);
    }

    public Map<String, Object> byId(int id) {
        return Sql.one("SELECT * FROM pacientes WHERE id_paciente = ?", id);
    }

    public Result create(Map<String, Object> data, int userId) {
        Map<String, Object> clean = new LinkedHashMap<>();
        Map<String, String> errors = validate(data, clean, 0);
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            StringBuilder cols = new StringBuilder();
            StringBuilder marks = new StringBuilder();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> e : clean.entrySet()) {
                cols.append(e.getKey()).append(", ");
                marks.append("?, ");
                params.add(e.getValue());
            }
            params.add(userId);
            params.add(userId);
            long id = Sql.insert(conn, "INSERT INTO pacientes (" + cols + "criado_por, atualizado_por) VALUES ("
                    + marks + "?, ?)", params.toArray());
            return new Result(id, errors);
        } catch (SQLException e) {
            if (Sql.isDuplicate(e)) {
                errors.put("cpf", "Já existe um paciente com este CPF.");
                return new Result(0, errors);
            }
            throw new RuntimeException("Could not register the patient", e);
        }
    }

    public Result update(int id, Map<String, Object> data, int userId) {
        Map<String, Object> clean = new LinkedHashMap<>();
        Map<String, String> errors = validate(data, clean, id);
        if (!errors.isEmpty()) {
            return new Result(id, errors);
        }
        StringBuilder set = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : clean.entrySet()) {
            set.append(e.getKey()).append(" = ?, ");
            params.add(e.getValue());
        }
        params.add(userId);
        params.add(id);
        try (Connection conn = DBConnection.openConnection()) {
            Sql.execute(conn, "UPDATE pacientes SET " + set + "atualizado_por = ? WHERE id_paciente = ?", params.toArray());
            return new Result(id, errors);
        } catch (SQLException e) {
            if (Sql.isDuplicate(e)) {
                errors.put("cpf", "Já existe um paciente com este CPF.");
                return new Result(id, errors);
            }
            throw new RuntimeException("Could not update the patient", e);
        }
    }

    public void setActive(int id, boolean active, int userId) {
        Sql.execute("UPDATE pacientes SET ativo = ?, atualizado_por = ? WHERE id_paciente = ?", active ? 1 : 0, userId, id);
    }

    /**
     * Validates and normalizes the fields. Column names come from the fixed FIELDS list, never
     * from the request. Fills "clean" only with values ready to be saved.
     */
    Map<String, String> validate(Map<String, Object> data, Map<String, Object> clean, int currentId) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (String field : FIELDS) {
            Object v = data.get(field);
            String s = v == null ? "" : Sanitize.string(v).trim();
            clean.put(field, s.isEmpty() ? null : s);
        }

        String name = (String) clean.get("nome");
        if (name == null || name.length() < 3) {
            errors.put("nome", "Informe o nome completo.");
        } else if (name.length() > 150) {
            errors.put("nome", "Nome com no máximo 150 caracteres.");
        }

        String cpf = Validation.digits((String) clean.get("cpf"));
        if (!Validation.validCpf(cpf)) {
            errors.put("cpf", "CPF inválido.");
        } else {
            clean.put("cpf", cpf);
            if (Sql.number("SELECT COUNT(*) FROM pacientes WHERE cpf = ? AND id_paciente <> ?", cpf, currentId) > 0) {
                errors.put("cpf", "Já existe um paciente com este CPF.");
            }
        }

        LocalDate birth = Validation.date((String) clean.get("data_nascimento"));
        if (!Validation.validBirth(birth, today())) {
            errors.put("data_nascimento", "Data de nascimento inválida.");
        } else {
            clean.put("data_nascimento", birth);
        }

        String sex = (String) clean.get("sexo");
        if (sex != null && !SEXES.contains(sex)) {
            errors.put("sexo", "Opção inválida.");
        }

        normalizePhone(clean, "telefone", errors);
        normalizePhone(clean, "contato_emergencia_telefone", errors);

        String email = (String) clean.get("email");
        if (email != null && !Validation.validEmail(email)) {
            errors.put("email", "E-mail inválido.");
        }
        String cep = (String) clean.get("cep");
        if (cep != null) {
            if (!Validation.validCep(cep)) {
                errors.put("cep", "CEP com 8 dígitos.");
            } else {
                clean.put("cep", Validation.digits(cep));
            }
        }
        String uf = (String) clean.get("uf");
        if (uf != null) {
            if (!Validation.validUf(uf)) {
                errors.put("uf", "UF inválida.");
            } else {
                clean.put("uf", uf.toUpperCase());
            }
        }
        return errors;
    }

    private static void normalizePhone(Map<String, Object> clean, String field, Map<String, String> errors) {
        String phone = (String) clean.get(field);
        if (phone == null) {
            return;
        }
        if (!Validation.validPhone(phone)) {
            errors.put(field, "Telefone com DDD (10 ou 11 dígitos).");
            return;
        }
        String d = Validation.digits(phone);
        clean.put(field, d.length() == 11
                ? "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7)
                : "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6));
    }

    /** The patient's appointments and exams, newest first (administrative data). */
    public List<Map<String, Object>> administrativeHistory(int patientId) {
        return Sql.list(
                "SELECT 'consulta' AS tipo, c.id_consulta AS id, c.data_hora, c.status, "
                        + "CONCAT(e.nome, ' · ', p.nome) AS descricao, c.motivo_cancelamento "
                        + "FROM consultas c JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                        + "JOIN profissionais p ON p.id_profissional = c.id_medico WHERE c.id_paciente = ? "
                        + "UNION ALL "
                        + "SELECT 'exame', x.id_exame_agendado, x.data_hora, x.status, "
                        + "CONCAT(t.nome, ' · ', l.nome), x.motivo_cancelamento "
                        + "FROM exames_agendados x JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame "
                        + "JOIN locais_exame l ON l.id_local = x.id_local WHERE x.id_paciente = ? "
                        + "ORDER BY data_hora DESC LIMIT 50", patientId, patientId);
    }
}
