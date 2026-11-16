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
import utils.Validacao;

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
public class Profissional {

    public static final List<String> TIPOS = List.of("medico", "enfermeiro", "funcionario");

    /** Grupo de acesso de cada tipo (funcionario = recepcao, o unico perfil administrativo do hospital). */
    public static String grupoDoTipo(String tipo) {
        switch (tipo) {
            case "medico":
                return "medico";
            case "enfermeiro":
                return "enfermeiro";
            default:
                return "recepcionista";
        }
    }

    /** Resultado de criar/atualizar: o id gravado ou os erros por campo; senhaTemporaria so quando criou acesso. */
    public record Resultado(long id, Map<String, String> erros, String emailAcesso, String senhaTemporaria) {
        public boolean ok() {
            return erros.isEmpty();
        }

        static Resultado erros(Map<String, String> erros) {
            return new Resultado(0, erros, null, null);
        }
    }

    // ------------------------------------------------------------------ consultas

    /**
     * Dados do profissional ATIVO vinculado ao usuario, com registro (CRM/COREN) quando houver;
     * null se o usuario nao for profissional ou estiver inativo.
     */
    public Map<String, Object> porUsuario(int idUsuario) {
        try (Connection conn = DBConnection.openConnection()) {
            return porUsuario(conn, idUsuario);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao consultar profissional do usuario", e);
        }
    }

    public Map<String, Object> porUsuario(Connection conn, int idUsuario) throws SQLException {
        String sql = "SELECT p.id_profissional, p.tipo, p.nome, p.cargo, "
                + "m.crm, m.crm_uf, e.coren, e.coren_uf "
                + "FROM profissionais p "
                + "LEFT JOIN medicos m ON m.id_profissional = p.id_profissional "
                + "LEFT JOIN enfermeiros e ON e.id_profissional = p.id_profissional "
                + "WHERE p.id_usuario = ? AND p.ativo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
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

    private static final String SELECT_LISTA = "SELECT p.id_profissional, p.tipo, p.nome, p.cargo, p.email, p.telefone, p.ativo, "
            + "p.id_usuario, u.email AS usuario_email, u.status AS usuario_status, m.crm, m.crm_uf, m.duracao_consulta_min, "
            + "e.coren, e.coren_uf, "
            + "(SELECT GROUP_CONCAT(es.nome ORDER BY es.nome SEPARATOR ', ') FROM medico_especialidades me "
            + " JOIN especialidades es ON es.id_especialidade = me.id_especialidade WHERE me.id_profissional = p.id_profissional) AS especialidades "
            + "FROM profissionais p "
            + "LEFT JOIN usuarios u ON u.id_usuario = p.id_usuario "
            + "LEFT JOIN medicos m ON m.id_profissional = p.id_profissional "
            + "LEFT JOIN enfermeiros e ON e.id_profissional = p.id_profissional";

    /** Lista com filtro por nome/registro e tipo; sem inativos, salvo se pedido. */
    public List<Map<String, Object>> listar(String termo, String tipo, boolean incluirInativos) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!incluirInativos) {
            where.append(" AND p.ativo = 1");
        }
        if (tipo != null && TIPOS.contains(tipo)) {
            where.append(" AND p.tipo = ?");
            params.add(tipo);
        }
        String t = termo == null ? "" : termo.trim();
        if (!t.isEmpty()) {
            String like = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (p.nome LIKE ? OR m.crm LIKE ? OR e.coren LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return Sql.lista(SELECT_LISTA + where + " ORDER BY p.ativo DESC, p.nome", params.toArray());
    }

    /** Ficha completa para o formulario: dados, especialidades (ids) e disponibilidade semanal. */
    public Map<String, Object> porId(int id) {
        Map<String, Object> p = Sql.um(SELECT_LISTA + " WHERE p.id_profissional = ?", id);
        if (p == null) {
            return null;
        }
        List<Integer> esp = new ArrayList<>();
        for (Map<String, Object> r : Sql.lista("SELECT id_especialidade FROM medico_especialidades WHERE id_profissional = ?", id)) {
            esp.add(((Number) r.get("id_especialidade")).intValue());
        }
        p.put("ids_especialidades", esp);
        p.put("disponibilidades", Sql.lista("SELECT dia_semana, TIME_FORMAT(hora_inicio, '%H:%i') AS hora_inicio, "
                + "TIME_FORMAT(hora_fim, '%H:%i') AS hora_fim FROM disponibilidades_medico "
                + "WHERE id_profissional = ? AND ativo = 1 ORDER BY dia_semana, hora_inicio", id));
        p.put("consultas_futuras", consultasFuturas(id));
        return p;
    }

    /** Consultas agendadas daqui para frente (bloqueiam a inativacao do medico). */
    public long consultasFuturas(int idProfissional) {
        return Sql.numero("SELECT COUNT(*) FROM consultas WHERE id_medico = ? AND status = 'agendada' AND data_hora >= NOW()",
                idProfissional);
    }

    /** Usuarios ativos ainda sem profissional, para o vinculo (RN-PRO-03), mais o atual quando houver. */
    public List<Map<String, Object>> usuariosSemVinculo(Integer idUsuarioAtual) {
        return Sql.lista("SELECT u.id_usuario, u.nome, u.email FROM usuarios u "
                + "LEFT JOIN profissionais p ON p.id_usuario = u.id_usuario "
                + "WHERE (p.id_profissional IS NULL AND u.status = 1) OR u.id_usuario = ? ORDER BY u.nome",
                idUsuarioAtual == null ? 0 : idUsuarioAtual);
    }

    // ------------------------------------------------------------------ gravacao

    public Resultado criar(Map<String, Object> dados, int idUsuarioLogado) {
        String tipo = texto(dados.get("tipo"));
        Map<String, String> erros = new LinkedHashMap<>();
        if (!TIPOS.contains(tipo)) {
            erros.put("tipo", "Escolha médico, enfermeiro ou funcionário.");
            return Resultado.erros(erros);
        }
        Map<String, Object> limpos = new LinkedHashMap<>();
        List<Integer> especialidades = new ArrayList<>();
        validar(tipo, dados, limpos, especialidades, erros, 0);
        boolean criarAcesso = "1".equals(texto(dados.get("criar_acesso")));
        if (criarAcesso && limpos.get("id_usuario") != null) {
            erros.put("id_usuario", "Escolha um usuário existente OU crie um acesso novo, não os dois.");
        }
        if (criarAcesso && limpos.get("email") == null) {
            erros.put("email", "Informe o e-mail: a senha temporária vai para ele.");
        }
        if (criarAcesso && limpos.get("email") != null
                && Sql.numero("SELECT COUNT(*) FROM usuarios WHERE email = ?", limpos.get("email")) > 0) {
            erros.put("email", "Já existe um usuário com este e-mail. Vincule o usuário existente.");
        }
        if (!erros.isEmpty()) {
            return Resultado.erros(erros);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                String senha = null;
                if (criarAcesso) {
                    senha = CodeGenerator.generatePassword(12);
                    long idNovo = Sql.inserir(conn, "INSERT INTO usuarios (nome, email, senha_hash, status, primeiro_acesso) "
                            + "VALUES (?, ?, ?, 1, 1)", limpos.get("nome"), limpos.get("email"), PasswordSecurity.hash(senha));
                    Sql.executar(conn, "INSERT INTO usuarios_grupos (id_usuario, id_grupo) "
                            + "SELECT ?, id_grupo FROM grupos WHERE nome = ?", idNovo, grupoDoTipo(tipo));
                    limpos.put("id_usuario", (int) idNovo);
                    Auditoria.registrar(conn, idUsuarioLogado, "usuario.criado", "usuarios", idNovo, null,
                            "grupo=" + grupoDoTipo(tipo), null);
                }
                long id = Sql.inserir(conn, "INSERT INTO profissionais (id_usuario, tipo, nome, cargo, email, telefone, criado_por, atualizado_por) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", limpos.get("id_usuario"), tipo, limpos.get("nome"), limpos.get("cargo"),
                        limpos.get("email"), limpos.get("telefone"), idUsuarioLogado, idUsuarioLogado);
                gravarRegistro(conn, (int) id, tipo, limpos, especialidades, true);
                Auditoria.registrar(conn, idUsuarioLogado, "profissional.criado", "profissionais", id, null, "tipo=" + tipo, null);
                conn.commit();
                return new Resultado(id, erros, criarAcesso ? (String) limpos.get("email") : null, senha);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.ehDuplicidade(e)) {
                    return Resultado.erros(duplicidade(e, tipo));
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao cadastrar profissional", e);
        }
    }

    public Resultado atualizar(int id, Map<String, Object> dados, int idUsuarioLogado) {
        Map<String, Object> atual = Sql.um("SELECT tipo FROM profissionais WHERE id_profissional = ?", id);
        Map<String, String> erros = new LinkedHashMap<>();
        if (atual == null) {
            erros.put("_", "Profissional não encontrado.");
            return Resultado.erros(erros);
        }
        String tipo = String.valueOf(atual.get("tipo"));
        Map<String, Object> limpos = new LinkedHashMap<>();
        List<Integer> especialidades = new ArrayList<>();
        validar(tipo, dados, limpos, especialidades, erros, id);
        if (!erros.isEmpty()) {
            return Resultado.erros(erros);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Sql.executar(conn, "UPDATE profissionais SET id_usuario = ?, nome = ?, cargo = ?, email = ?, telefone = ?, "
                        + "atualizado_por = ? WHERE id_profissional = ?", limpos.get("id_usuario"), limpos.get("nome"),
                        limpos.get("cargo"), limpos.get("email"), limpos.get("telefone"), idUsuarioLogado, id);
                gravarRegistro(conn, id, tipo, limpos, especialidades, false);
                Auditoria.registrar(conn, idUsuarioLogado, "profissional.atualizado", "profissionais", id, null, null, null);
                conn.commit();
                return new Resultado(id, erros, null, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.ehDuplicidade(e)) {
                    return Resultado.erros(duplicidade(e, tipo));
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao atualizar profissional", e);
        }
    }

    /** Ativa/inativa. Devolve a mensagem de bloqueio, ou null quando gravou. */
    public String definirAtivo(int id, boolean ativo, int idUsuarioLogado) {
        if (!ativo) {
            long futuras = consultasFuturas(id);
            if (futuras > 0) {
                return "Este médico tem " + futuras + (futuras == 1 ? " consulta agendada" : " consultas agendadas")
                        + " daqui para frente. Remarque ou cancele antes de inativar.";
            }
        }
        Sql.executar("UPDATE profissionais SET ativo = ?, atualizado_por = ? WHERE id_profissional = ?", ativo ? 1 : 0, idUsuarioLogado, id);
        return null;
    }

    /**
     * Substitui a disponibilidade semanal do medico (RF-PRO-06). Cada faixa: dia_semana 1..7
     * (segunda..domingo), hora_inicio < hora_fim, sem sobrepor outra faixa do mesmo dia.
     * Consultas ja marcadas fora da nova grade continuam valendo.
     */
    public Map<String, String> salvarDisponibilidade(int idMedico, List<Map<String, Object>> faixas, int idUsuarioLogado) {
        Map<String, String> erros = new LinkedHashMap<>();
        if (Sql.um("SELECT 1 FROM medicos WHERE id_profissional = ?", idMedico) == null) {
            erros.put("_", "Disponibilidade só para médicos.");
            return erros;
        }
        List<int[]> limpas = new ArrayList<>();
        if (faixas.size() > 28) {
            erros.put("_", "No máximo 28 faixas por semana.");
            return erros;
        }
        for (int i = 0; i < faixas.size(); i++) {
            Map<String, Object> f = faixas.get(i);
            int dia = Sanitize.intVal(f.get("dia_semana"), 0);
            LocalTime ini = hora(f.get("hora_inicio"));
            LocalTime fim = hora(f.get("hora_fim"));
            if (dia < 1 || dia > 7 || ini == null || fim == null) {
                erros.put("faixa_" + i, "Preencha o dia e os dois horários.");
            } else if (!fim.isAfter(ini)) {
                erros.put("faixa_" + i, "O fim precisa ser depois do início.");
            } else {
                int a = ini.toSecondOfDay() / 60;
                int b = fim.toSecondOfDay() / 60;
                for (int[] o : limpas) {
                    if (o[0] == dia && a < o[2] && b > o[1]) {
                        erros.put("faixa_" + i, "Sobrepõe outra faixa do mesmo dia.");
                    }
                }
                limpas.add(new int[] {dia, a, b});
            }
        }
        if (!erros.isEmpty()) {
            return erros;
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Sql.executar(conn, "DELETE FROM disponibilidades_medico WHERE id_profissional = ?", idMedico);
                for (int[] f : limpas) {
                    Sql.executar(conn, "INSERT INTO disponibilidades_medico (id_profissional, dia_semana, hora_inicio, hora_fim) "
                            + "VALUES (?, ?, ?, ?)", idMedico, f[0], LocalTime.ofSecondOfDay(f[1] * 60L), LocalTime.ofSecondOfDay(f[2] * 60L));
                }
                Auditoria.registrar(conn, idUsuarioLogado, "profissional.disponibilidade", "profissionais", idMedico, null,
                        "faixas=" + limpas.size(), null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao salvar disponibilidade", e);
        }
        return erros;
    }

    // ------------------------------------------------------------------ auxiliares

    /** Valida os campos comuns e os do tipo; nomes de coluna fixos, nunca vindos da requisicao. */
    void validar(String tipo, Map<String, Object> dados, Map<String, Object> limpos, List<Integer> especialidades,
                 Map<String, String> erros, int idAtual) {
        String nome = texto(dados.get("nome"));
        if (nome.length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        } else if (nome.length() > 150) {
            erros.put("nome", "Nome com no máximo 150 caracteres.");
        }
        limpos.put("nome", nome);

        String email = texto(dados.get("email")).toLowerCase();
        if (!email.isEmpty() && (!Validacao.emailValido(email) || email.length() > 150)) {
            erros.put("email", "E-mail inválido.");
        }
        limpos.put("email", email.isEmpty() ? null : email);

        String tel = texto(dados.get("telefone"));
        if (!tel.isEmpty()) {
            if (!Validacao.telefoneValido(tel)) {
                erros.put("telefone", "Telefone com DDD (10 ou 11 dígitos).");
            } else {
                String d = Validacao.digitos(tel);
                tel = d.length() == 11
                        ? "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7)
                        : "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
            }
        }
        limpos.put("telefone", tel.isEmpty() ? null : tel);

        int idUsuario = Sanitize.intVal(dados.get("id_usuario"), 0);
        if (idUsuario > 0) {
            if (Sql.um("SELECT 1 FROM usuarios WHERE id_usuario = ?", idUsuario) == null) {
                erros.put("id_usuario", "Usuário inexistente.");
            } else if (Sql.numero("SELECT COUNT(*) FROM profissionais WHERE id_usuario = ? AND id_profissional <> ?",
                    idUsuario, idAtual) > 0) {
                erros.put("id_usuario", "Este usuário já está vinculado a outro profissional.");
            }
            limpos.put("id_usuario", idUsuario);
        } else {
            limpos.put("id_usuario", null);
        }

        limpos.put("cargo", null);
        switch (tipo) {
            case "medico": {
                String crm = Validacao.digitos(texto(dados.get("crm")));
                if (crm.isEmpty() || crm.length() > 10) {
                    erros.put("crm", "CRM com até 10 dígitos.");
                }
                limpos.put("crm", crm);
                uf(dados, "crm_uf", limpos, erros);
                int duracao = Sanitize.intVal(dados.get("duracao_consulta_min"), 30);
                if (duracao < 5 || duracao > 240 || duracao % 5 != 0) {
                    erros.put("duracao_consulta_min", "Entre 5 e 240 minutos, de 5 em 5.");
                }
                limpos.put("duracao_consulta_min", duracao);
                Set<Integer> ids = new LinkedHashSet<>();
                Object raw = dados.get("especialidades");
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
                    if (Sql.um("SELECT 1 FROM especialidades WHERE id_especialidade = ? AND ativo = 1", e) == null) {
                        erros.put("especialidades", "Especialidade inexistente ou inativa.");
                    }
                }
                if (ids.isEmpty()) {
                    erros.put("especialidades", "Escolha ao menos uma especialidade.");
                }
                especialidades.addAll(ids);
                if (!erros.containsKey("crm") && !erros.containsKey("crm_uf")
                        && Sql.numero("SELECT COUNT(*) FROM medicos WHERE crm = ? AND crm_uf = ? AND id_profissional <> ?",
                        crm, limpos.get("crm_uf"), idAtual) > 0) {
                    erros.put("crm", "Já existe um médico com este CRM nesta UF.");
                }
                break;
            }
            case "enfermeiro": {
                String coren = texto(dados.get("coren")).toUpperCase().replaceAll("[^0-9A-Z]", "");
                if (coren.isEmpty() || coren.length() > 12) {
                    erros.put("coren", "COREN com até 12 caracteres (números e letras).");
                }
                limpos.put("coren", coren);
                uf(dados, "coren_uf", limpos, erros);
                if (!erros.containsKey("coren") && !erros.containsKey("coren_uf")
                        && Sql.numero("SELECT COUNT(*) FROM enfermeiros WHERE coren = ? AND coren_uf = ? AND id_profissional <> ?",
                        coren, limpos.get("coren_uf"), idAtual) > 0) {
                    erros.put("coren", "Já existe um enfermeiro com este COREN nesta UF.");
                }
                break;
            }
            default: {
                String cargo = texto(dados.get("cargo"));
                if (cargo.length() < 3 || cargo.length() > 80) {
                    erros.put("cargo", "Informe o cargo (até 80 caracteres).");
                }
                limpos.put("cargo", cargo);
            }
        }
    }

    private static void gravarRegistro(Connection conn, int id, String tipo, Map<String, Object> limpos,
                                       List<Integer> especialidades, boolean novo) throws SQLException {
        if ("medico".equals(tipo)) {
            if (novo) {
                Sql.executar(conn, "INSERT INTO medicos (id_profissional, crm, crm_uf, duracao_consulta_min) VALUES (?, ?, ?, ?)",
                        id, limpos.get("crm"), limpos.get("crm_uf"), limpos.get("duracao_consulta_min"));
            } else {
                Sql.executar(conn, "UPDATE medicos SET crm = ?, crm_uf = ?, duracao_consulta_min = ? WHERE id_profissional = ?",
                        limpos.get("crm"), limpos.get("crm_uf"), limpos.get("duracao_consulta_min"), id);
            }
            Sql.executar(conn, "DELETE FROM medico_especialidades WHERE id_profissional = ?", id);
            for (Integer e : especialidades) {
                Sql.executar(conn, "INSERT INTO medico_especialidades (id_profissional, id_especialidade) VALUES (?, ?)", id, e);
            }
        } else if ("enfermeiro".equals(tipo)) {
            if (novo) {
                Sql.executar(conn, "INSERT INTO enfermeiros (id_profissional, coren, coren_uf) VALUES (?, ?, ?)",
                        id, limpos.get("coren"), limpos.get("coren_uf"));
            } else {
                Sql.executar(conn, "UPDATE enfermeiros SET coren = ?, coren_uf = ? WHERE id_profissional = ?",
                        limpos.get("coren"), limpos.get("coren_uf"), id);
            }
        }
    }

    private static Map<String, String> duplicidade(SQLException e, String tipo) {
        Map<String, String> erros = new LinkedHashMap<>();
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("uk_profissional_usuario")) {
            erros.put("id_usuario", "Este usuário já está vinculado a outro profissional.");
        } else if (msg.contains("usuarios") || msg.contains("email")) {
            erros.put("email", "Já existe um usuário com este e-mail.");
        } else if ("medico".equals(tipo)) {
            erros.put("crm", "Já existe um médico com este CRM nesta UF.");
        } else {
            erros.put("coren", "Já existe um enfermeiro com este COREN nesta UF.");
        }
        return erros;
    }

    private static void uf(Map<String, Object> dados, String campo, Map<String, Object> limpos, Map<String, String> erros) {
        String uf = texto(dados.get(campo)).toUpperCase();
        if (!Validacao.ufValida(uf)) {
            erros.put(campo, "UF inválida.");
        }
        limpos.put(campo, uf);
    }

    private static LocalTime hora(Object v) {
        String s = texto(v);
        if (!s.matches("^\\d{2}:\\d{2}$")) {
            return null;
        }
        try {
            return LocalTime.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String texto(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
