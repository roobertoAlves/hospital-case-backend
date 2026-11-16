package models;

import database.DBConnection;
import database.Sql;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.PasswordSecurity;
import utils.Sanitize;
import utils.Validation;

/**
 * ZTF_J2EE - Model Portal (portal do paciente)
 *
 * Regras:
 * - RN-POR-01: uma conta do portal se liga a no maximo uma ficha (pacientes.id_usuario UNIQUE);
 * - RN-POR-02: CPF que ja tem ficha so e ligado com o codigo enviado ao e-mail DA FICHA
 *   (6 digitos, 15 minutos, 5 tentativas, guardado so o hash). Sem e-mail na ficha: recepcao;
 * - RN-POR-03: o paciente so ve e altera os proprios dados (toda consulta filtra pelo id da
 *   ficha ligada a sessao; registro de outra pessoa responde "nao encontrado");
 */
public class PatientPortal {

    private static final int CODE_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Resultado de operacoes do portal. */
    public record Result(boolean ok, long id, String error, Map<String, String> errors) {
        static Result ok(long id) {
            return new Result(true, id, null, Map.of());
        }

        static Result failure(String error) {
            return new Result(false, 0, error, Map.of());
        }

        static Result fields(Map<String, String> errors) {
            return new Result(false, 0, "Revise os campos destacados.", errors);
        }
    }

    /** Resultado do cadastro: a conta criada e, se a ficha ja existia, o e-mail (mascarado) que recebeu o codigo. */
    public record Registration(Result result, String recordEmail, String code, String name) {
    }

    /** Ficha ligada a conta (so pacientes ativos); null se ainda nao ha vinculo. */
    public static Integer userPatient(int userId) {
        Map<String, Object> p = Sql.one("SELECT id_paciente FROM pacientes WHERE id_usuario = ? AND ativo = 1", userId);
        return p == null ? null : ((Number) p.get("id_paciente")).intValue();
    }

    // ------------------------------------------------------------------ cadastro e vinculo

    public Registration register(Map<String, Object> data) {
        Map<String, String> errors = new LinkedHashMap<>();
        String email = text(data.get("email")).toLowerCase();
        String password = data.get("senha") == null ? "" : String.valueOf(data.get("senha"));
        if (!"1".equals(text(data.get("aceite")))) {
            errors.put("aceite", "É preciso aceitar o uso dos dados para atendimento.");
        }
        if (!Validation.validEmail(email) || email.length() > 150) {
            errors.put("email", "E-mail inválido.");
        } else if (Sql.number("SELECT COUNT(*) FROM usuarios WHERE email = ?", email) > 0) {
            errors.put("email", "Já existe uma conta com este e-mail. Use “Esqueci minha senha”.");
        }
        String weak = PasswordSecurity.strengthError(password);
        if (weak != null) {
            errors.put("senha", weak);
        } else if (!password.equals(String.valueOf(data.get("confirmar_senha")))) {
            errors.put("confirmar_senha", "A confirmação não confere.");
        }
        String cpf = Validation.digits(text(data.get("cpf")));
        Map<String, Object> show = Validation.validCpf(cpf)
                ? Sql.one("SELECT id_paciente, nome, email, id_usuario, ativo FROM pacientes WHERE cpf = ?", cpf) : null;

        if (show != null) {
            // CPF ja cadastrado pela recepcao: nada da ficha e alterado aqui.
            if (show.get("id_usuario") != null) {
                errors.put("cpf", "Este CPF já tem conta no portal. Use “Esqueci minha senha”.");
            } else if (show.get("email") == null || String.valueOf(show.get("email")).isBlank()) {
                errors.put("cpf", "Seu cadastro já existe na clínica, mas sem e-mail. Procure a recepção para liberar o acesso.");
            } else if (!"1".equals(String.valueOf(show.get("ativo")))) {
                errors.put("cpf", "Cadastro inativo. Procure a recepção.");
            }
            String name = text(data.get("nome"));
            if (name.length() < 3) {
                errors.put("nome", "Informe o nome completo.");
            }
            if (!errors.isEmpty()) {
                return new Registration(Result.fields(errors), null, null, null);
            }
            try (Connection conn = DBConnection.openConnection()) {
                conn.setAutoCommit(false);
                try {
                    long userId = createUser(conn, name, email, password);
                    String code = newCode(conn, userId, ((Number) show.get("id_paciente")).intValue());
                    AuditLog.record(conn, (int) userId, "portal.cadastro_ficha_existente", "usuarios", userId,
                            ((Number) show.get("id_paciente")).intValue(), null, null);
                    conn.commit();
                    return new Registration(Result.ok(userId), String.valueOf(show.get("email")), code, name);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Falha no cadastro do portal", e);
            }
        }

        // CPF novo: cria a ficha (mesma validacao da recepcao) e a conta, ja ligadas.
        Map<String, Object> clean = new LinkedHashMap<>();
        Map<String, String> recordErrors = new Patient().validate(data, clean, 0);
        recordErrors.forEach(errors::putIfAbsent);
        if (!errors.isEmpty()) {
            return new Registration(Result.fields(errors), null, null, null);
        }
        if (clean.get("email") == null) {
            clean.put("email", email);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                long userId = createUser(conn, String.valueOf(clean.get("nome")), email, password);
                StringBuilder cols = new StringBuilder();
                StringBuilder marcas = new StringBuilder();
                List<Object> params = new java.util.ArrayList<>();
                for (Map.Entry<String, Object> e : clean.entrySet()) {
                    cols.append(e.getKey()).append(", ");
                    marcas.append("?, ");
                    params.add(e.getValue());
                }
                params.add(userId);
                params.add(userId);
                params.add(userId);
                long patientId = Sql.insert(conn, "INSERT INTO pacientes (" + cols + "id_usuario, criado_por, atualizado_por) VALUES ("
                        + marcas + "?, ?, ?)", params.toArray());
                AuditLog.record(conn, (int) userId, "portal.cadastro", "pacientes", patientId, (int) patientId, null, null);
                conn.commit();
                return new Registration(Result.ok(userId), null, null, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.isDuplicate(e)) {
                    return new Registration(Result.failure("CPF ou e-mail já cadastrado."), null, null, null);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha no cadastro do portal", e);
        }
    }

    /** Novo codigo para o vinculo pendente da conta (invalida os anteriores). Devolve o codigo e o e-mail da ficha. */
    public String[] resendCode(int userId) {
        Map<String, Object> v = Sql.one("SELECT v.id_paciente, p.email FROM vinculos_portal v JOIN pacientes p ON p.id_paciente = v.id_paciente "
                + "WHERE v.id_usuario = ? AND p.id_usuario IS NULL ORDER BY v.id_vinculo DESC LIMIT 1", userId);
        if (v == null) {
            return null;
        }
        if (Sql.number("SELECT COUNT(*) FROM vinculos_portal WHERE id_usuario = ? AND criado_em > DATE_SUB(NOW(), INTERVAL 1 HOUR)", userId) >= 4) {
            return new String[] {null, null};
        }
        try (Connection conn = DBConnection.openConnection()) {
            String code = newCode(conn, userId, ((Number) v.get("id_paciente")).intValue());
            return new String[] {code, String.valueOf(v.get("email"))};
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao gerar codigo", e);
        }
    }

    /** Confere o codigo e liga a conta a ficha. */
    public Result link(int userId, String code) {
        Map<String, Object> v = Sql.one("SELECT id_vinculo, id_paciente, codigo_hash, expira_em, tentativas FROM vinculos_portal "
                + "WHERE id_usuario = ? AND usado_em IS NULL ORDER BY id_vinculo DESC LIMIT 1", userId);
        if (v == null) {
            return Result.failure("Não há vínculo pendente para esta conta.");
        }
        int linkId = ((Number) v.get("id_vinculo")).intValue();
        if (((Number) v.get("tentativas")).intValue() >= MAX_ATTEMPTS || Appointments.dateTime(v.get("expira_em")).isBefore(LocalDateTime.now())) {
            return Result.failure("Código expirado. Peça um novo.");
        }
        String clean = Validation.digits(code == null ? "" : code);
        if (!PasswordSecurity.sha256Hex(clean).equals(v.get("codigo_hash"))) {
            Sql.execute("UPDATE vinculos_portal SET tentativas = tentativas + 1 WHERE id_vinculo = ?", linkId);
            AuditLog.record(userId, "portal.vinculo_codigo_errado", "vinculos_portal", linkId, null, null, null);
            return Result.failure("Código incorreto.");
        }
        int patientId = ((Number) v.get("id_paciente")).intValue();
        int n = Sql.execute("UPDATE pacientes SET id_usuario = ? WHERE id_paciente = ? AND id_usuario IS NULL", userId, patientId);
        Sql.execute("UPDATE vinculos_portal SET usado_em = NOW() WHERE id_vinculo = ?", linkId);
        if (n == 0) {
            return Result.failure("Esta ficha já foi ligada a outra conta. Procure a recepção.");
        }
        AuditLog.record(userId, "portal.vinculo_confirmado", "pacientes", patientId, patientId, null, null);
        return Result.ok(patientId);
    }

    /** Recepcao liga uma conta do portal (sem ficha) a ficha, quando a ficha nao tem e-mail. */
    public Result linkInReception(int patientId, String accountEmail, int receptionUserId) {
        Map<String, Object> show = Sql.one("SELECT id_usuario FROM pacientes WHERE id_paciente = ?", patientId);
        if (show == null) {
            return Result.failure("Paciente não encontrado.");
        }
        if (show.get("id_usuario") != null) {
            return Result.failure("Esta ficha já tem conta no portal.");
        }
        Map<String, Object> account = Sql.one("SELECT u.id_usuario FROM usuarios u JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario "
                + "JOIN grupos g ON g.id_grupo = ug.id_grupo AND g.nome = 'paciente' "
                + "LEFT JOIN pacientes p ON p.id_usuario = u.id_usuario WHERE u.email = ? AND p.id_paciente IS NULL",
                text(accountEmail).toLowerCase());
        if (account == null) {
            return Result.failure("Não há conta do portal sem ficha com esse e-mail. O paciente precisa se cadastrar no portal antes.");
        }
        int accountId = ((Number) account.get("id_usuario")).intValue();
        Sql.execute("UPDATE pacientes SET id_usuario = ?, atualizado_por = ? WHERE id_paciente = ? AND id_usuario IS NULL",
                accountId, receptionUserId, patientId);
        AuditLog.record(receptionUserId, "portal.vinculo_recepcao", "pacientes", patientId, patientId, "conta=" + accountId, null);
        return Result.ok(accountId);
    }

    // ------------------------------------------------------------------ dados do paciente

    public Map<String, Object> summary(int userId, int patientId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("paciente", Sql.one("SELECT id_paciente, nome, cpf, data_nascimento, telefone, email, cidade, uf FROM pacientes WHERE id_paciente = ?", patientId));
        d.put("proximasConsultas", Sql.list("SELECT c.id_consulta, c.data_hora, c.status, c.confirmada_em, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN profissionais pr ON pr.id_profissional = c.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.id_paciente = ? AND c.data_hora >= CURDATE() AND c.status NOT IN ('cancelada', 'ausente', 'realizada') ORDER BY c.data_hora LIMIT 5", patientId));
        d.put("proximosExames", Sql.list("SELECT x.id_exame_agendado, x.data_hora, x.status, t.nome AS exame, t.preparo, l.nome AS local, pm.status AS pedido_status "
                + "FROM exames_agendados x JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame JOIN locais_exame l ON l.id_local = x.id_local "
                + "LEFT JOIN pedidos_medicos pm ON pm.id_pedido = x.id_pedido "
                + "WHERE x.id_paciente = ? AND x.data_hora >= CURDATE() AND x.status = 'agendado' ORDER BY x.data_hora LIMIT 5", patientId));
        d.put("proximasVacinas", Sql.list("SELECT a.id_agendamento, a.data_hora, a.dose, v.nome AS vacina FROM vacinas_agendadas a "
                + "JOIN vacinas v ON v.id_vacina = a.id_vacina WHERE a.id_paciente = ? AND a.status = 'agendada' AND a.data_hora >= CURDATE() "
                + "ORDER BY a.data_hora LIMIT 5", patientId));
        d.put("receitasRecentes", Sql.number("SELECT COUNT(*) FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta "
                + "WHERE c.id_paciente = ? AND r.status = 'emitida' AND r.emitida_em >= DATE_SUB(NOW(), INTERVAL 90 DAY)", patientId));
        d.put("pedidosPendentes", Sql.number("SELECT COUNT(*) FROM pedidos_medicos WHERE id_paciente = ? AND status = 'pendente'", patientId));
        return d;
    }

    public List<Map<String, Object>> appointments(int patientId) {
        return Sql.list("SELECT c.id_consulta, c.data_hora, c.status, c.confirmada_em, c.motivo_cancelamento, c.remarcada_de, c.origem, "
                + "(SELECT n.data_hora FROM consultas n WHERE n.remarcada_de = c.id_consulta) AS remarcada_para, "
                + "c.id_medico, c.id_especialidade, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN profissionais pr ON pr.id_profissional = c.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.id_paciente = ? ORDER BY c.data_hora DESC LIMIT 100", patientId);
    }

    /** A consulta e do paciente? (RN-POR-03) */
    public static boolean patientAppointment(int appointmentId, int patientId) {
        return Sql.one("SELECT 1 FROM consultas WHERE id_consulta = ? AND id_paciente = ?", appointmentId, patientId) != null;
    }

    public Result bookAppointment(int patientId, int doctorId, int specialtyId, LocalDateTime when, int userId) {
        Appointments.Result r = new Appointments().book(patientId, doctorId, specialtyId, when, userId);
        if (!r.ok()) {
            return Result.failure(r.error());
        }
        Sql.execute("UPDATE consultas SET origem = 'portal' WHERE id_consulta = ?", r.id());
        return Result.ok(r.id());
    }

    public Result confirmAppointment(int appointmentId, int patientId, int userId) {
        int n = Sql.execute("UPDATE consultas SET confirmada_em = NOW() WHERE id_consulta = ? AND id_paciente = ? AND status = 'agendada' "
                + "AND data_hora > NOW() AND confirmada_em IS NULL", appointmentId, patientId);
        if (n == 0) {
            return Result.failure("Só consultas futuras ainda não confirmadas podem ser confirmadas.");
        }
        AuditLog.record(userId, "portal.consulta_confirmada", "consultas", appointmentId, patientId, null, null);
        return Result.ok(appointmentId);
    }

    public List<Map<String, Object>> exams(int patientId) {
        return Sql.list("SELECT x.id_exame_agendado, x.data_hora, x.status, x.motivo_cancelamento, x.origem, t.nome AS exame, t.preparo, "
                + "l.nome AS local, x.id_pedido, pm.status AS pedido_status, pm.motivo_recusa "
                + "FROM exames_agendados x JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame JOIN locais_exame l ON l.id_local = x.id_local "
                + "LEFT JOIN pedidos_medicos pm ON pm.id_pedido = x.id_pedido WHERE x.id_paciente = ? ORDER BY x.data_hora DESC LIMIT 100", patientId);
    }

    /** Exames pedidos pelos medicos do Asklep e ainda nao agendados (dispensam anexo). */
    public List<Map<String, Object>> openRequests(int patientId) {
        return Sql.list("SELECT x.id_exame, x.nome_exame, x.solicitado_em, pr.nome AS medico FROM exames x "
                + "JOIN consultas c ON c.id_consulta = x.id_consulta JOIN profissionais pr ON pr.id_profissional = x.id_medico "
                + "WHERE c.id_paciente = ? AND x.status = 'solicitado' "
                + "AND NOT EXISTS (SELECT 1 FROM exames_agendados a WHERE a.id_solicitacao = x.id_exame AND a.status <> 'cancelado') "
                + "ORDER BY x.solicitado_em DESC", patientId);
    }

    /**
     * RN-POR-04: exame que exige pedido precisa de solicitacao do sistema ou pedido anexado
     * (pendente ou aprovado) do proprio paciente. A recepcao confere o anexo depois.
     */
    public Result bookExam(int patientId, int typeId, LocalDateTime when, Integer requestId, Integer orderId, int userId) {
        Map<String, Object> type = Sql.one("SELECT exige_pedido FROM tipos_exame WHERE id_tipo_exame = ? AND ativo = 1", typeId);
        if (type == null) {
            return Result.failure("Exame indisponível.");
        }
        if (orderId != null && Sql.one("SELECT 1 FROM pedidos_medicos WHERE id_pedido = ? AND id_paciente = ? AND status <> 'recusado'",
                orderId, patientId) == null) {
            return Result.failure("Pedido médico não encontrado ou recusado.");
        }
        if ("1".equals(String.valueOf(type.get("exige_pedido"))) && requestId == null && orderId == null) {
            return Result.failure("Este exame exige pedido médico: anexe o pedido ou escolha uma solicitação do seu médico.");
        }
        Appointments.Result r = new ExamSchedule().book(patientId, typeId, when, requestId, userId);
        if (!r.ok()) {
            return Result.failure(r.error());
        }
        Sql.execute("UPDATE exames_agendados SET origem = 'portal', id_pedido = ? WHERE id_exame_agendado = ?", orderId, r.id());
        return Result.ok(r.id());
    }

    public static boolean patientExam(int scheduledExamId, int patientId) {
        return Sql.one("SELECT 1 FROM exames_agendados WHERE id_exame_agendado = ? AND id_paciente = ?", scheduledExamId, patientId) != null;
    }

    // ------------------------------------------------------------------ catalogo publico

    /** O que qualquer visitante ve sem login: especialidades e medicos, exames (com preparo) e vacinas. */
    public Map<String, Object> catalog() {
        Map<String, Object> d = new LinkedHashMap<>();
        List<Map<String, Object>> esp = Sql.list("SELECT id_especialidade, nome, descricao FROM especialidades WHERE ativo = 1 ORDER BY nome");
        for (Map<String, Object> e : esp) {
            e.put("medicos", Sql.list("SELECT p.id_profissional, p.nome, m.crm, m.crm_uf FROM medico_especialidades me "
                    + "JOIN profissionais p ON p.id_profissional = me.id_profissional JOIN medicos m ON m.id_profissional = p.id_profissional "
                    + "WHERE me.id_especialidade = ? AND p.ativo = 1 ORDER BY p.nome", e.get("id_especialidade")));
        }
        d.put("especialidades", esp);
        d.put("exames", Sql.list("SELECT t.id_tipo_exame, t.nome, t.categoria, t.descricao, t.duracao_min, t.preparo, t.exige_pedido, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 AND l.ativo = 1 ORDER BY t.categoria, t.nome"));
        d.put("vacinas", new Vaccine().catalog());
        return d;
    }

    // ------------------------------------------------------------------ auxiliares

    private static long createUser(Connection conn, String name, String email, String password) throws SQLException {
        long id = Sql.insert(conn, "INSERT INTO usuarios (nome, email, senha_hash, status, primeiro_acesso) VALUES (?, ?, ?, 1, 0)",
                name, email, PasswordSecurity.hash(password));
        Sql.execute(conn, "INSERT INTO usuarios_grupos (id_usuario, id_grupo) SELECT ?, id_grupo FROM grupos WHERE nome = 'paciente'", id);
        return id;
    }

    private static String newCode(Connection conn, long userId, int patientId) throws SQLException {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Sql.execute(conn, "UPDATE vinculos_portal SET usado_em = NOW() WHERE id_usuario = ? AND usado_em IS NULL", userId);
        Sql.execute(conn, "INSERT INTO vinculos_portal (id_usuario, id_paciente, codigo_hash, expira_em) VALUES (?, ?, ?, ?)",
                userId, patientId, PasswordSecurity.sha256Hex(code), LocalDateTime.now().plusMinutes(CODE_MINUTES));
        return code;
    }

    private static String text(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
