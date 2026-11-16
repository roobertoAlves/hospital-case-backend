package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import utils.CodeGenerator;
import utils.PasswordSecurity;
import utils.Sanitize;
import utils.Validation;

/**
 * ZTF_J2EE - Model Profissional
 *
 * Cadastro de profissionais (RF-PRO-01..06): medicos (CRM/UF, especialidades, duracao da
 * consulta, disponibilidade semanal), enfermeiros (COREN/UF) e funcionarios (cargo).
 *
 * Regras:
 * - RN-PRO-01: CRM + UF e COREN + UF unicos (UNIQUE no banco e conferido aqui);
 * - RN-PRO-02: medico atende ao menos uma especialidade ativa;
 * - RN-PRO-03: um usuario se vincula a no maximo um profissional;
 * - o tipo nao muda depois do cadastro (os dados ficam em tabelas diferentes);
 * - inativar um medico com consultas futuras agendadas e bloqueado: antes, remarque ou cancele.
 *
 * O acesso ao sistema pode ser criado junto: usuario no grupo do perfil, senha temporaria
 * enviada por e-mail e troca obrigatoria no primeiro acesso.
 */
public class Professional {

    public static final List<String> TYPES = List.of("medico", "enfermeiro", "funcionario");

    /** Grupo de acesso de cada tipo (funcionario = recepcao, o unico perfil administrativo do hospital). */
    public static String typeGroup(String type) {
        switch (type) {
            case "medico":
                return "medico";
            case "enfermeiro":
                return "enfermeiro";
            default:
                return "recepcionista";
        }
    }

    /** Resultado de criar/atualizar: o id gravado ou os erros por campo; senhaTemporaria so quando criou acesso. */
    public record Result(long id, Map<String, String> errors, String accessEmail, String temporaryPassword) {
        public boolean ok() {
            return errors.isEmpty();
        }

        static Result errors(Map<String, String> errors) {
            return new Result(0, errors, null, null);
        }
    }

    // ------------------------------------------------------------------ consultas

    /**
     * Dados do profissional ATIVO vinculado ao usuario, com registro (CRM/COREN) quando houver;
     * null se o usuario nao for profissional ou estiver inativo.
     */
    public Map<String, Object> byUser(int userId) {
        try (Connection conn = DBConnection.openConnection()) {
            return byUser(conn, userId);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao consultar profissional do usuario", e);
        }
    }

    public Map<String, Object> byUser(Connection conn, int userId) throws SQLException {
        String sql = "SELECT p.id_profissional, p.tipo, p.nome, p.cargo, "
                + "m.crm, m.crm_uf, e.coren, e.coren_uf "
                + "FROM profissionais p "
                + "LEFT JOIN medicos m ON m.id_profissional = p.id_profissional "
                + "LEFT JOIN enfermeiros e ON e.id_profissional = p.id_profissional "
                + "WHERE p.id_usuario = ? AND p.ativo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id_profissional", rs.getInt("id_profissional"));
                p.put("tipo", rs.getString("tipo"));
                p.put("nome", rs.getString("nome"));
                p.put("cargo", rs.getString("cargo"));
                if (rs.getString("crm") != null) {
                    p.put("registro", "CRM " + rs.getString("crm") + "/" + rs.getString("crm_uf"));
                } else if (rs.getString("coren") != null) {
                    p.put("registro", "COREN " + rs.getString("coren") + "/" + rs.getString("coren_uf"));
                }
                return p;
            }
        }
    }

    private static final String SELECT_LIST = "SELECT p.id_profissional, p.tipo, p.nome, p.cargo, p.email, p.telefone, p.ativo, "
            + "p.id_usuario, u.email AS usuario_email, u.status AS usuario_status, m.crm, m.crm_uf, m.duracao_consulta_min, "
            + "e.coren, e.coren_uf, "
            + "(SELECT GROUP_CONCAT(es.nome ORDER BY es.nome SEPARATOR ', ') FROM medico_especialidades me "
            + " JOIN especialidades es ON es.id_especialidade = me.id_especialidade WHERE me.id_profissional = p.id_profissional) AS especialidades "
            + "FROM profissionais p "
            + "LEFT JOIN usuarios u ON u.id_usuario = p.id_usuario "
            + "LEFT JOIN medicos m ON m.id_profissional = p.id_profissional "
            + "LEFT JOIN enfermeiros e ON e.id_profissional = p.id_profissional";

    /** Lista com filtro por nome/registro e tipo; sem inativos, salvo se pedido. */
    public List<Map<String, Object>> list(String term, String type, boolean includeInactive) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!includeInactive) {
            where.append(" AND p.ativo = 1");
        }
        if (type != null && TYPES.contains(type)) {
            where.append(" AND p.tipo = ?");
            params.add(type);
        }
        String t = term == null ? "" : term.trim();
        if (!t.isEmpty()) {
            String like = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (p.nome LIKE ? OR m.crm LIKE ? OR e.coren LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return Sql.list(SELECT_LIST + where + " ORDER BY p.ativo DESC, p.nome", params.toArray());
    }

    /** Ficha completa para o formulario: dados, especialidades (ids) e disponibilidade semanal. */
    public Map<String, Object> byId(int id) {
        Map<String, Object> p = Sql.one(SELECT_LIST + " WHERE p.id_profissional = ?", id);
        if (p == null) {
            return null;
        }
        List<Integer> esp = new ArrayList<>();
        for (Map<String, Object> r : Sql.list("SELECT id_especialidade FROM medico_especialidades WHERE id_profissional = ?", id)) {
            esp.add(((Number) r.get("id_especialidade")).intValue());
        }
        p.put("ids_especialidades", esp);
        p.put("disponibilidades", Sql.list("SELECT dia_semana, TIME_FORMAT(hora_inicio, '%H:%i') AS hora_inicio, "
                + "TIME_FORMAT(hora_fim, '%H:%i') AS hora_fim FROM disponibilidades_medico "
                + "WHERE id_profissional = ? AND ativo = 1 ORDER BY dia_semana, hora_inicio", id));
        p.put("consultas_futuras", futureAppointments(id));
        return p;
    }

    /** Consultas agendadas daqui para frente (bloqueiam a inativacao do medico). */
    public long futureAppointments(int professionalId) {
        return Sql.number("SELECT COUNT(*) FROM consultas WHERE id_medico = ? AND status = 'agendada' AND data_hora >= NOW()",
                professionalId);
    }

    /** Usuarios ativos ainda sem profissional, para o vinculo (RN-PRO-03), mais o atual quando houver. */
    public List<Map<String, Object>> usersWithoutLink(Integer currentUserId) {
        return Sql.list("SELECT u.id_usuario, u.nome, u.email FROM usuarios u "
                + "LEFT JOIN profissionais p ON p.id_usuario = u.id_usuario "
                + "WHERE (p.id_profissional IS NULL AND u.status = 1) OR u.id_usuario = ? ORDER BY u.nome",
                currentUserId == null ? 0 : currentUserId);
    }

    // ------------------------------------------------------------------ gravacao

    public Result create(Map<String, Object> data, int loggedInUserId) {
        String type = text(data.get("tipo"));
        Map<String, String> errors = new LinkedHashMap<>();
        if (!TYPES.contains(type)) {
            errors.put("tipo", "Escolha médico, enfermeiro ou funcionário.");
            return Result.errors(errors);
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        List<Integer> specialties = new ArrayList<>();
        validate(type, data, clean, specialties, errors, 0);
        boolean createAccess = "1".equals(text(data.get("criar_acesso")));
        if (createAccess && clean.get("id_usuario") != null) {
            errors.put("id_usuario", "Escolha um usuário existente OU crie um acesso novo, não os dois.");
        }
        if (createAccess && clean.get("email") == null) {
            errors.put("email", "Informe o e-mail: a senha temporária vai para ele.");
        }
        if (createAccess && clean.get("email") != null
                && Sql.number("SELECT COUNT(*) FROM usuarios WHERE email = ?", clean.get("email")) > 0) {
            errors.put("email", "Já existe um usuário com este e-mail. Vincule o usuário existente.");
        }
        if (!errors.isEmpty()) {
            return Result.errors(errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                String password = null;
                if (createAccess) {
                    password = CodeGenerator.generatePassword(12);
                    long newId = Sql.insert(conn, "INSERT INTO usuarios (nome, email, senha_hash, status, primeiro_acesso) "
                            + "VALUES (?, ?, ?, 1, 1)", clean.get("nome"), clean.get("email"), PasswordSecurity.hash(password));
                    Sql.execute(conn, "INSERT INTO usuarios_grupos (id_usuario, id_grupo) "
                            + "SELECT ?, id_grupo FROM grupos WHERE nome = ?", newId, typeGroup(type));
                    clean.put("id_usuario", (int) newId);
                    AuditLog.record(conn, loggedInUserId, "usuario.criado", "usuarios", newId, null,
                            "grupo=" + typeGroup(type), null);
                }
                long id = Sql.insert(conn, "INSERT INTO profissionais (id_usuario, tipo, nome, cargo, email, telefone, criado_por, atualizado_por) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", clean.get("id_usuario"), type, clean.get("nome"), clean.get("cargo"),
                        clean.get("email"), clean.get("telefone"), loggedInUserId, loggedInUserId);
                saveRecord(conn, (int) id, type, clean, specialties, true);
                AuditLog.record(conn, loggedInUserId, "profissional.criado", "profissionais", id, null, "tipo=" + type, null);
                conn.commit();
                return new Result(id, errors, createAccess ? (String) clean.get("email") : null, password);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.isDuplicate(e)) {
                    return Result.errors(duplicate(e, type));
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao cadastrar profissional", e);
        }
    }

    public Result update(int id, Map<String, Object> data, int loggedInUserId) {
        Map<String, Object> current = Sql.one("SELECT tipo FROM profissionais WHERE id_profissional = ?", id);
        Map<String, String> errors = new LinkedHashMap<>();
        if (current == null) {
            errors.put("_", "Profissional não encontrado.");
            return Result.errors(errors);
        }
        String type = String.valueOf(current.get("tipo"));
        Map<String, Object> clean = new LinkedHashMap<>();
        List<Integer> specialties = new ArrayList<>();
        validate(type, data, clean, specialties, errors, id);
        if (!errors.isEmpty()) {
            return Result.errors(errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Sql.execute(conn, "UPDATE profissionais SET id_usuario = ?, nome = ?, cargo = ?, email = ?, telefone = ?, "
                        + "atualizado_por = ? WHERE id_profissional = ?", clean.get("id_usuario"), clean.get("nome"),
                        clean.get("cargo"), clean.get("email"), clean.get("telefone"), loggedInUserId, id);
                saveRecord(conn, id, type, clean, specialties, false);
                AuditLog.record(conn, loggedInUserId, "profissional.atualizado", "profissionais", id, null, null, null);
                conn.commit();
                return new Result(id, errors, null, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.isDuplicate(e)) {
                    return Result.errors(duplicate(e, type));
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao atualizar profissional", e);
        }
    }

    /** Ativa/inativa. Devolve a mensagem de bloqueio, ou null quando gravou. */
    public String setActive(int id, boolean active, int loggedInUserId) {
        if (!active) {
            long future = futureAppointments(id);
            if (future > 0) {
                return "Este médico tem " + future + (future == 1 ? " consulta agendada" : " consultas agendadas")
                        + " daqui para frente. Remarque ou cancele antes de inativar.";
            }
        }
        Sql.execute("UPDATE profissionais SET ativo = ?, atualizado_por = ? WHERE id_profissional = ?", active ? 1 : 0, loggedInUserId, id);
        return null;
    }

    /**
     * Substitui a disponibilidade semanal do medico (RF-PRO-06). Cada faixa: dia_semana 1..7
     * (segunda..domingo), hora_inicio < hora_fim, sem sobrepor outra faixa do mesmo dia.
     * Consultas ja marcadas fora da nova grade continuam valendo.
     */
    public Map<String, String> saveAvailability(int doctorId, List<Map<String, Object>> ranges, int loggedInUserId) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (Sql.one("SELECT 1 FROM medicos WHERE id_profissional = ?", doctorId) == null) {
            errors.put("_", "Disponibilidade só para médicos.");
            return errors;
        }
        List<int[]> clean = new ArrayList<>();
        if (ranges.size() > 28) {
            errors.put("_", "No máximo 28 faixas por semana.");
            return errors;
        }
        for (int i = 0; i < ranges.size(); i++) {
            Map<String, Object> f = ranges.get(i);
            int day = Sanitize.intVal(f.get("dia_semana"), 0);
            LocalTime ini = time(f.get("hora_inicio"));
            LocalTime end = time(f.get("hora_fim"));
            if (day < 1 || day > 7 || ini == null || end == null) {
                errors.put("faixa_" + i, "Preencha o dia e os dois horários.");
            } else if (!end.isAfter(ini)) {
                errors.put("faixa_" + i, "O fim precisa ser depois do início.");
            } else {
                int a = ini.toSecondOfDay() / 60;
                int b = end.toSecondOfDay() / 60;
                for (int[] o : clean) {
                    if (o[0] == day && a < o[2] && b > o[1]) {
                        errors.put("faixa_" + i, "Sobrepõe outra faixa do mesmo dia.");
                    }
                }
                clean.add(new int[] {day, a, b});
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Sql.execute(conn, "DELETE FROM disponibilidades_medico WHERE id_profissional = ?", doctorId);
                for (int[] f : clean) {
                    Sql.execute(conn, "INSERT INTO disponibilidades_medico (id_profissional, dia_semana, hora_inicio, hora_fim) "
                            + "VALUES (?, ?, ?, ?)", doctorId, f[0], LocalTime.ofSecondOfDay(f[1] * 60L), LocalTime.ofSecondOfDay(f[2] * 60L));
                }
                AuditLog.record(conn, loggedInUserId, "profissional.disponibilidade", "profissionais", doctorId, null,
                        "faixas=" + clean.size(), null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao salvar disponibilidade", e);
        }
        return errors;
    }

    // ------------------------------------------------------------------ auxiliares

    /** Valida os campos comuns e os do tipo; nomes de coluna fixos, nunca vindos da requisicao. */
    void validate(String type, Map<String, Object> data, Map<String, Object> clean, List<Integer> specialties,
                 Map<String, String> errors, int currentId) {
        String name = text(data.get("nome"));
        if (name.length() < 3) {
            errors.put("nome", "Informe o nome completo.");
        } else if (name.length() > 150) {
            errors.put("nome", "Nome com no máximo 150 caracteres.");
        }
        clean.put("nome", name);

        String email = text(data.get("email")).toLowerCase();
        if (!email.isEmpty() && (!Validation.validEmail(email) || email.length() > 150)) {
            errors.put("email", "E-mail inválido.");
        }
        clean.put("email", email.isEmpty() ? null : email);

        String tel = text(data.get("telefone"));
        if (!tel.isEmpty()) {
            if (!Validation.validPhone(tel)) {
                errors.put("telefone", "Telefone com DDD (10 ou 11 dígitos).");
            } else {
                String d = Validation.digits(tel);
                tel = d.length() == 11
                        ? "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7)
                        : "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
            }
        }
        clean.put("telefone", tel.isEmpty() ? null : tel);

        int userId = Sanitize.intVal(data.get("id_usuario"), 0);
        if (userId > 0) {
            if (Sql.one("SELECT 1 FROM usuarios WHERE id_usuario = ?", userId) == null) {
                errors.put("id_usuario", "Usuário inexistente.");
            } else if (Sql.number("SELECT COUNT(*) FROM profissionais WHERE id_usuario = ? AND id_profissional <> ?",
                    userId, currentId) > 0) {
                errors.put("id_usuario", "Este usuário já está vinculado a outro profissional.");
            }
            clean.put("id_usuario", userId);
        } else {
            clean.put("id_usuario", null);
        }

        clean.put("cargo", null);
        switch (type) {
            case "medico": {
                String crm = Validation.digits(text(data.get("crm")));
                if (crm.isEmpty() || crm.length() > 10) {
                    errors.put("crm", "CRM com até 10 dígitos.");
                }
                clean.put("crm", crm);
                uf(data, "crm_uf", clean, errors);
                int duration = Sanitize.intVal(data.get("duracao_consulta_min"), 30);
                if (duration < 5 || duration > 240 || duration % 5 != 0) {
                    errors.put("duracao_consulta_min", "Entre 5 e 240 minutos, de 5 em 5.");
                }
                clean.put("duracao_consulta_min", duration);
                Set<Integer> ids = new LinkedHashSet<>();
                Object raw = data.get("especialidades");
                if (raw instanceof List<?>) {
                    for (Object o : (List<?>) raw) {
                        ids.add(Sanitize.intVal(o, 0));
                    }
                } else if (raw != null) {
                    for (String s : String.valueOf(raw).split(",")) {
                        ids.add(Sanitize.intVal(s.trim(), 0));
                    }
                }
                ids.remove(0);
                for (Integer e : ids) {
                    if (Sql.one("SELECT 1 FROM especialidades WHERE id_especialidade = ? AND ativo = 1", e) == null) {
                        errors.put("especialidades", "Especialidade inexistente ou inativa.");
                    }
                }
                if (ids.isEmpty()) {
                    errors.put("especialidades", "Escolha ao menos uma especialidade.");
                }
                specialties.addAll(ids);
                if (!errors.containsKey("crm") && !errors.containsKey("crm_uf")
                        && Sql.number("SELECT COUNT(*) FROM medicos WHERE crm = ? AND crm_uf = ? AND id_profissional <> ?",
                        crm, clean.get("crm_uf"), currentId) > 0) {
                    errors.put("crm", "Já existe um médico com este CRM nesta UF.");
                }
                break;
            }
            case "enfermeiro": {
                String coren = text(data.get("coren")).toUpperCase().replaceAll("[^0-9A-Z]", "");
                if (coren.isEmpty() || coren.length() > 12) {
                    errors.put("coren", "COREN com até 12 caracteres (números e letras).");
                }
                clean.put("coren", coren);
                uf(data, "coren_uf", clean, errors);
                if (!errors.containsKey("coren") && !errors.containsKey("coren_uf")
                        && Sql.number("SELECT COUNT(*) FROM enfermeiros WHERE coren = ? AND coren_uf = ? AND id_profissional <> ?",
                        coren, clean.get("coren_uf"), currentId) > 0) {
                    errors.put("coren", "Já existe um enfermeiro com este COREN nesta UF.");
                }
                break;
            }
            default: {
                String role = text(data.get("cargo"));
                if (role.length() < 3 || role.length() > 80) {
                    errors.put("cargo", "Informe o cargo (até 80 caracteres).");
                }
                clean.put("cargo", role);
            }
        }
    }

    private static void saveRecord(Connection conn, int id, String type, Map<String, Object> clean,
                                       List<Integer> specialties, boolean newForm) throws SQLException {
        if ("medico".equals(type)) {
            if (newForm) {
                Sql.execute(conn, "INSERT INTO medicos (id_profissional, crm, crm_uf, duracao_consulta_min) VALUES (?, ?, ?, ?)",
                        id, clean.get("crm"), clean.get("crm_uf"), clean.get("duracao_consulta_min"));
            } else {
                Sql.execute(conn, "UPDATE medicos SET crm = ?, crm_uf = ?, duracao_consulta_min = ? WHERE id_profissional = ?",
                        clean.get("crm"), clean.get("crm_uf"), clean.get("duracao_consulta_min"), id);
            }
            Sql.execute(conn, "DELETE FROM medico_especialidades WHERE id_profissional = ?", id);
            for (Integer e : specialties) {
                Sql.execute(conn, "INSERT INTO medico_especialidades (id_profissional, id_especialidade) VALUES (?, ?)", id, e);
            }
        } else if ("enfermeiro".equals(type)) {
            if (newForm) {
                Sql.execute(conn, "INSERT INTO enfermeiros (id_profissional, coren, coren_uf) VALUES (?, ?, ?)",
                        id, clean.get("coren"), clean.get("coren_uf"));
            } else {
                Sql.execute(conn, "UPDATE enfermeiros SET coren = ?, coren_uf = ? WHERE id_profissional = ?",
                        clean.get("coren"), clean.get("coren_uf"), id);
            }
        }
    }

    private static Map<String, String> duplicate(SQLException e, String type) {
        Map<String, String> errors = new LinkedHashMap<>();
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("uk_profissional_usuario")) {
            errors.put("id_usuario", "Este usuário já está vinculado a outro profissional.");
        } else if (msg.contains("usuarios") || msg.contains("email")) {
            errors.put("email", "Já existe um usuário com este e-mail.");
        } else if ("medico".equals(type)) {
            errors.put("crm", "Já existe um médico com este CRM nesta UF.");
        } else {
            errors.put("coren", "Já existe um enfermeiro com este COREN nesta UF.");
        }
        return errors;
    }

    private static void uf(Map<String, Object> data, String field, Map<String, Object> clean, Map<String, String> errors) {
        String uf = text(data.get(field)).toUpperCase();
        if (!Validation.validUf(uf)) {
            errors.put(field, "UF inválida.");
        }
        clean.put(field, uf);
    }

    private static LocalTime time(Object v) {
        String s = text(v);
        if (!s.matches("^\\d{2}:\\d{2}$")) {
            return null;
        }
        try {
            return LocalTime.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String text(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
