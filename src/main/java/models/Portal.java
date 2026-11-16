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
import utils.Validacao;

/**
 * ZTF_J2EE - Model Portal (portal do paciente)
 *
 * Regras:
 * - RN-POR-01: uma conta do portal se liga a no maximo uma ficha (pacientes.id_usuario UNIQUE);
 * - RN-POR-02: CPF que ja tem ficha so e ligado com o codigo enviado ao e-mail DA FICHA
 *   (6 digitos, 15 minutos, 5 tentativas, guardado so o hash). Sem e-mail na ficha: recepcao;
 * - RN-POR-03: o paciente so ve e altera os proprios dados (toda consulta filtra pelo id da
 *   ficha ligada a sessao; registro de outra pessoa responde "nao encontrado");
 * - RN-POR-05: limite de agendamentos futuros ativos por paciente (evita reserva em massa).
 */
public class Portal {

    public static final int MAX_CONSULTAS_FUTURAS = 3;
    public static final int MAX_EXAMES_FUTUROS = 5;
    private static final int MINUTOS_CODIGO = 15;
    private static final int MAX_TENTATIVAS = 5;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Resultado de operacoes do portal. */
    public record Resultado(boolean ok, long id, String erro, Map<String, String> erros) {
        static Resultado ok(long id) {
            return new Resultado(true, id, null, Map.of());
        }

        static Resultado falha(String erro) {
            return new Resultado(false, 0, erro, Map.of());
        }

        static Resultado campos(Map<String, String> erros) {
            return new Resultado(false, 0, "Revise os campos destacados.", erros);
        }
    }

    /** Resultado do cadastro: a conta criada e, se a ficha ja existia, o e-mail (mascarado) que recebeu o codigo. */
    public record Cadastro(Resultado resultado, String emailFicha, String codigo, String nome) {
    }

    /** Ficha ligada a conta (so pacientes ativos); null se ainda nao ha vinculo. */
    public static Integer pacienteDoUsuario(int idUsuario) {
        Map<String, Object> p = Sql.um("SELECT id_paciente FROM pacientes WHERE id_usuario = ? AND ativo = 1", idUsuario);
        return p == null ? null : ((Number) p.get("id_paciente")).intValue();
    }

    // ------------------------------------------------------------------ cadastro e vinculo

    public Cadastro cadastrar(Map<String, Object> dados) {
        Map<String, String> erros = new LinkedHashMap<>();
        String email = texto(dados.get("email")).toLowerCase();
        String senha = dados.get("senha") == null ? "" : String.valueOf(dados.get("senha"));
        if (!"1".equals(texto(dados.get("aceite")))) {
            erros.put("aceite", "É preciso aceitar o uso dos dados para atendimento.");
        }
        if (!Validacao.emailValido(email) || email.length() > 150) {
            erros.put("email", "E-mail inválido.");
        } else if (Sql.numero("SELECT COUNT(*) FROM usuarios WHERE email = ?", email) > 0) {
            erros.put("email", "Já existe uma conta com este e-mail. Use “Esqueci minha senha”.");
        }
        String fraca = PasswordSecurity.strengthError(senha);
        if (fraca != null) {
            erros.put("senha", fraca);
        } else if (!senha.equals(String.valueOf(dados.get("confirmar_senha")))) {
            erros.put("confirmar_senha", "A confirmação não confere.");
        }
        String cpf = Validacao.digitos(texto(dados.get("cpf")));
        Map<String, Object> ficha = Validacao.cpfValido(cpf)
                ? Sql.um("SELECT id_paciente, nome, email, id_usuario, ativo FROM pacientes WHERE cpf = ?", cpf) : null;

        if (ficha != null) {
            // CPF ja cadastrado pela recepcao: nada da ficha e alterado aqui.
            if (ficha.get("id_usuario") != null) {
                erros.put("cpf", "Este CPF já tem conta no portal. Use “Esqueci minha senha”.");
            } else if (ficha.get("email") == null || String.valueOf(ficha.get("email")).isBlank()) {
                erros.put("cpf", "Seu cadastro já existe na clínica, mas sem e-mail. Procure a recepção para liberar o acesso.");
            } else if (!"1".equals(String.valueOf(ficha.get("ativo")))) {
                erros.put("cpf", "Cadastro inativo. Procure a recepção.");
            }
            String nome = texto(dados.get("nome"));
            if (nome.length() < 3) {
                erros.put("nome", "Informe o nome completo.");
            }
            if (!erros.isEmpty()) {
                return new Cadastro(Resultado.campos(erros), null, null, null);
            }
            try (Connection conn = DBConnection.openConnection()) {
                conn.setAutoCommit(false);
                try {
                    long idUsuario = criarUsuario(conn, nome, email, senha);
                    String codigo = novoCodigo(conn, idUsuario, ((Number) ficha.get("id_paciente")).intValue());
                    Auditoria.registrar(conn, (int) idUsuario, "portal.cadastro_ficha_existente", "usuarios", idUsuario,
                            ((Number) ficha.get("id_paciente")).intValue(), null, null);
                    conn.commit();
                    return new Cadastro(Resultado.ok(idUsuario), String.valueOf(ficha.get("email")), codigo, nome);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Falha no cadastro do portal", e);
            }
        }

        // CPF novo: cria a ficha (mesma validacao da recepcao) e a conta, ja ligadas.
        Map<String, Object> limpos = new LinkedHashMap<>();
        Map<String, String> errosFicha = new Paciente().validar(dados, limpos, 0);
        errosFicha.forEach(erros::putIfAbsent);
        if (!erros.isEmpty()) {
            return new Cadastro(Resultado.campos(erros), null, null, null);
        }
        if (limpos.get("email") == null) {
            limpos.put("email", email);
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                long idUsuario = criarUsuario(conn, String.valueOf(limpos.get("nome")), email, senha);
                StringBuilder cols = new StringBuilder();
                StringBuilder marcas = new StringBuilder();
                List<Object> params = new java.util.ArrayList<>();
                for (Map.Entry<String, Object> e : limpos.entrySet()) {
                    cols.append(e.getKey()).append(", ");
                    marcas.append("?, ");
                    params.add(e.getValue());
                }
                params.add(idUsuario);
                params.add(idUsuario);
                params.add(idUsuario);
                long idPaciente = Sql.inserir(conn, "INSERT INTO pacientes (" + cols + "id_usuario, criado_por, atualizado_por) VALUES ("
                        + marcas + "?, ?, ?)", params.toArray());
                Auditoria.registrar(conn, (int) idUsuario, "portal.cadastro", "pacientes", idPaciente, (int) idPaciente, null, null);
                conn.commit();
                return new Cadastro(Resultado.ok(idUsuario), null, null, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.ehDuplicidade(e)) {
                    return new Cadastro(Resultado.falha("CPF ou e-mail já cadastrado."), null, null, null);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha no cadastro do portal", e);
        }
    }

    /** Novo codigo para o vinculo pendente da conta (invalida os anteriores). Devolve o codigo e o e-mail da ficha. */
    public String[] reenviarCodigo(int idUsuario) {
        Map<String, Object> v = Sql.um("SELECT v.id_paciente, p.email FROM vinculos_portal v JOIN pacientes p ON p.id_paciente = v.id_paciente "
                + "WHERE v.id_usuario = ? AND p.id_usuario IS NULL ORDER BY v.id_vinculo DESC LIMIT 1", idUsuario);
        if (v == null) {
            return null;
        }
        if (Sql.numero("SELECT COUNT(*) FROM vinculos_portal WHERE id_usuario = ? AND criado_em > DATE_SUB(NOW(), INTERVAL 1 HOUR)", idUsuario) >= 4) {
            return new String[] {null, null};
        }
        try (Connection conn = DBConnection.openConnection()) {
            String codigo = novoCodigo(conn, idUsuario, ((Number) v.get("id_paciente")).intValue());
            return new String[] {codigo, String.valueOf(v.get("email"))};
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao gerar codigo", e);
        }
    }

    /** Confere o codigo e liga a conta a ficha. */
    public Resultado vincular(int idUsuario, String codigo) {
        Map<String, Object> v = Sql.um("SELECT id_vinculo, id_paciente, codigo_hash, expira_em, tentativas FROM vinculos_portal "
                + "WHERE id_usuario = ? AND usado_em IS NULL ORDER BY id_vinculo DESC LIMIT 1", idUsuario);
        if (v == null) {
            return Resultado.falha("Não há vínculo pendente para esta conta.");
        }
        int idVinculo = ((Number) v.get("id_vinculo")).intValue();
        if (((Number) v.get("tentativas")).intValue() >= MAX_TENTATIVAS || Agenda.dataHora(v.get("expira_em")).isBefore(LocalDateTime.now())) {
            return Resultado.falha("Código expirado. Peça um novo.");
        }
        String limpo = Validacao.digitos(codigo == null ? "" : codigo);
        if (!PasswordSecurity.sha256Hex(limpo).equals(v.get("codigo_hash"))) {
            Sql.executar("UPDATE vinculos_portal SET tentativas = tentativas + 1 WHERE id_vinculo = ?", idVinculo);
            Auditoria.registrar(idUsuario, "portal.vinculo_codigo_errado", "vinculos_portal", idVinculo, null, null, null);
            return Resultado.falha("Código incorreto.");
        }
        int idPaciente = ((Number) v.get("id_paciente")).intValue();
        int n = Sql.executar("UPDATE pacientes SET id_usuario = ? WHERE id_paciente = ? AND id_usuario IS NULL", idUsuario, idPaciente);
        Sql.executar("UPDATE vinculos_portal SET usado_em = NOW() WHERE id_vinculo = ?", idVinculo);
        if (n == 0) {
            return Resultado.falha("Esta ficha já foi ligada a outra conta. Procure a recepção.");
        }
        Auditoria.registrar(idUsuario, "portal.vinculo_confirmado", "pacientes", idPaciente, idPaciente, null, null);
        return Resultado.ok(idPaciente);
    }

    /** Recepcao liga uma conta do portal (sem ficha) a ficha, quando a ficha nao tem e-mail. */
    public Resultado vincularNaRecepcao(int idPaciente, String emailConta, int idUsuarioRecepcao) {
        Map<String, Object> ficha = Sql.um("SELECT id_usuario FROM pacientes WHERE id_paciente = ?", idPaciente);
        if (ficha == null) {
            return Resultado.falha("Paciente não encontrado.");
        }
        if (ficha.get("id_usuario") != null) {
            return Resultado.falha("Esta ficha já tem conta no portal.");
        }
        Map<String, Object> conta = Sql.um("SELECT u.id_usuario FROM usuarios u JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario "
                + "JOIN grupos g ON g.id_grupo = ug.id_grupo AND g.nome = 'paciente' "
                + "LEFT JOIN pacientes p ON p.id_usuario = u.id_usuario WHERE u.email = ? AND p.id_paciente IS NULL",
                texto(emailConta).toLowerCase());
        if (conta == null) {
            return Resultado.falha("Não há conta do portal sem ficha com esse e-mail. O paciente precisa se cadastrar no portal antes.");
        }
        int idConta = ((Number) conta.get("id_usuario")).intValue();
        Sql.executar("UPDATE pacientes SET id_usuario = ?, atualizado_por = ? WHERE id_paciente = ? AND id_usuario IS NULL",
                idConta, idUsuarioRecepcao, idPaciente);
        Auditoria.registrar(idUsuarioRecepcao, "portal.vinculo_recepcao", "pacientes", idPaciente, idPaciente, "conta=" + idConta, null);
        return Resultado.ok(idConta);
    }

    // ------------------------------------------------------------------ dados do paciente

    public Map<String, Object> resumo(int idUsuario, int idPaciente) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("paciente", Sql.um("SELECT id_paciente, nome, cpf, data_nascimento, telefone, email, cidade, uf FROM pacientes WHERE id_paciente = ?", idPaciente));
        d.put("proximasConsultas", Sql.lista("SELECT c.id_consulta, c.data_hora, c.status, c.confirmada_em, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN profissionais pr ON pr.id_profissional = c.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.id_paciente = ? AND c.data_hora >= CURDATE() AND c.status NOT IN ('cancelada', 'ausente', 'realizada') ORDER BY c.data_hora LIMIT 5", idPaciente));
        d.put("proximosExames", Sql.lista("SELECT x.id_exame_agendado, x.data_hora, x.status, t.nome AS exame, t.preparo, l.nome AS local, pm.status AS pedido_status "
                + "FROM exames_agendados x JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame JOIN locais_exame l ON l.id_local = x.id_local "
                + "LEFT JOIN pedidos_medicos pm ON pm.id_pedido = x.id_pedido "
                + "WHERE x.id_paciente = ? AND x.data_hora >= CURDATE() AND x.status = 'agendado' ORDER BY x.data_hora LIMIT 5", idPaciente));
        d.put("proximasVacinas", Sql.lista("SELECT a.id_agendamento, a.data_hora, a.dose, v.nome AS vacina FROM vacinas_agendadas a "
                + "JOIN vacinas v ON v.id_vacina = a.id_vacina WHERE a.id_paciente = ? AND a.status = 'agendada' AND a.data_hora >= CURDATE() "
                + "ORDER BY a.data_hora LIMIT 5", idPaciente));
        d.put("receitasRecentes", Sql.numero("SELECT COUNT(*) FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta "
                + "WHERE c.id_paciente = ? AND r.status = 'emitida' AND r.emitida_em >= DATE_SUB(NOW(), INTERVAL 90 DAY)", idPaciente));
        d.put("pedidosPendentes", Sql.numero("SELECT COUNT(*) FROM pedidos_medicos WHERE id_paciente = ? AND status = 'pendente'", idPaciente));
        return d;
    }

    public List<Map<String, Object>> consultas(int idPaciente) {
        return Sql.lista("SELECT c.id_consulta, c.data_hora, c.status, c.confirmada_em, c.motivo_cancelamento, c.remarcada_de, c.origem, "
                + "c.id_medico, c.id_especialidade, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN profissionais pr ON pr.id_profissional = c.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.id_paciente = ? ORDER BY c.data_hora DESC LIMIT 100", idPaciente);
    }

    /** A consulta e do paciente? (RN-POR-03) */
    public static boolean consultaDoPaciente(int idConsulta, int idPaciente) {
        return Sql.um("SELECT 1 FROM consultas WHERE id_consulta = ? AND id_paciente = ?", idConsulta, idPaciente) != null;
    }

    public Resultado agendarConsulta(int idPaciente, int idMedico, int idEspecialidade, LocalDateTime quando, int idUsuario) {
        if (Sql.numero("SELECT COUNT(*) FROM consultas WHERE id_paciente = ? AND status = 'agendada' AND data_hora > NOW()", idPaciente)
                >= MAX_CONSULTAS_FUTURAS) {
            return Resultado.falha("Você já tem " + MAX_CONSULTAS_FUTURAS + " consultas marcadas. Para marcar outra, cancele uma ou fale com a recepção.");
        }
        Agenda.Resultado r = new Agenda().agendar(idPaciente, idMedico, idEspecialidade, quando, idUsuario);
        if (!r.ok()) {
            return Resultado.falha(r.erro());
        }
        Sql.executar("UPDATE consultas SET origem = 'portal' WHERE id_consulta = ?", r.id());
        return Resultado.ok(r.id());
    }

    public Resultado confirmarConsulta(int idConsulta, int idPaciente, int idUsuario) {
        int n = Sql.executar("UPDATE consultas SET confirmada_em = NOW() WHERE id_consulta = ? AND id_paciente = ? AND status = 'agendada' "
                + "AND data_hora > NOW() AND confirmada_em IS NULL", idConsulta, idPaciente);
        if (n == 0) {
            return Resultado.falha("Só consultas futuras ainda não confirmadas podem ser confirmadas.");
        }
        Auditoria.registrar(idUsuario, "portal.consulta_confirmada", "consultas", idConsulta, idPaciente, null, null);
        return Resultado.ok(idConsulta);
    }

    public List<Map<String, Object>> exames(int idPaciente) {
        return Sql.lista("SELECT x.id_exame_agendado, x.data_hora, x.status, x.motivo_cancelamento, x.origem, t.nome AS exame, t.preparo, "
                + "l.nome AS local, x.id_pedido, pm.status AS pedido_status, pm.motivo_recusa "
                + "FROM exames_agendados x JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame JOIN locais_exame l ON l.id_local = x.id_local "
                + "LEFT JOIN pedidos_medicos pm ON pm.id_pedido = x.id_pedido WHERE x.id_paciente = ? ORDER BY x.data_hora DESC LIMIT 100", idPaciente);
    }

    /** Exames pedidos pelos medicos do Asklep e ainda nao agendados (dispensam anexo). */
    public List<Map<String, Object>> solicitacoesAbertas(int idPaciente) {
        return Sql.lista("SELECT x.id_exame, x.nome_exame, x.solicitado_em, pr.nome AS medico FROM exames x "
                + "JOIN consultas c ON c.id_consulta = x.id_consulta JOIN profissionais pr ON pr.id_profissional = x.id_medico "
                + "WHERE c.id_paciente = ? AND x.status = 'solicitado' "
                + "AND NOT EXISTS (SELECT 1 FROM exames_agendados a WHERE a.id_solicitacao = x.id_exame AND a.status <> 'cancelado') "
                + "ORDER BY x.solicitado_em DESC", idPaciente);
    }

    /**
     * RN-POR-04: exame que exige pedido precisa de solicitacao do sistema ou pedido anexado
     * (pendente ou aprovado) do proprio paciente. A recepcao confere o anexo depois.
     */
    public Resultado agendarExame(int idPaciente, int idTipo, LocalDateTime quando, Integer idSolicitacao, Integer idPedido, int idUsuario) {
        Map<String, Object> tipo = Sql.um("SELECT exige_pedido FROM tipos_exame WHERE id_tipo_exame = ? AND ativo = 1", idTipo);
        if (tipo == null) {
            return Resultado.falha("Exame indisponível.");
        }
        if (idPedido != null && Sql.um("SELECT 1 FROM pedidos_medicos WHERE id_pedido = ? AND id_paciente = ? AND status <> 'recusado'",
                idPedido, idPaciente) == null) {
            return Resultado.falha("Pedido médico não encontrado ou recusado.");
        }
        if ("1".equals(String.valueOf(tipo.get("exige_pedido"))) && idSolicitacao == null && idPedido == null) {
            return Resultado.falha("Este exame exige pedido médico: anexe o pedido ou escolha uma solicitação do seu médico.");
        }
        if (Sql.numero("SELECT COUNT(*) FROM exames_agendados WHERE id_paciente = ? AND status = 'agendado' AND data_hora > NOW()", idPaciente)
                >= MAX_EXAMES_FUTUROS) {
            return Resultado.falha("Você já tem " + MAX_EXAMES_FUTUROS + " exames marcados. Para marcar outro, cancele um ou fale com a recepção.");
        }
        Agenda.Resultado r = new AgendaExames().agendar(idPaciente, idTipo, quando, idSolicitacao, idUsuario);
        if (!r.ok()) {
            return Resultado.falha(r.erro());
        }
        Sql.executar("UPDATE exames_agendados SET origem = 'portal', id_pedido = ? WHERE id_exame_agendado = ?", idPedido, r.id());
        return Resultado.ok(r.id());
    }

    public static boolean exameDoPaciente(int idExameAgendado, int idPaciente) {
        return Sql.um("SELECT 1 FROM exames_agendados WHERE id_exame_agendado = ? AND id_paciente = ?", idExameAgendado, idPaciente) != null;
    }

    // ------------------------------------------------------------------ catalogo publico

    /** O que qualquer visitante ve sem login: especialidades e medicos, exames (com preparo) e vacinas. */
    public Map<String, Object> catalogo() {
        Map<String, Object> d = new LinkedHashMap<>();
        List<Map<String, Object>> esp = Sql.lista("SELECT id_especialidade, nome, descricao FROM especialidades WHERE ativo = 1 ORDER BY nome");
        for (Map<String, Object> e : esp) {
            e.put("medicos", Sql.lista("SELECT p.id_profissional, p.nome, m.crm, m.crm_uf FROM medico_especialidades me "
                    + "JOIN profissionais p ON p.id_profissional = me.id_profissional JOIN medicos m ON m.id_profissional = p.id_profissional "
                    + "WHERE me.id_especialidade = ? AND p.ativo = 1 ORDER BY p.nome", e.get("id_especialidade")));
        }
        d.put("especialidades", esp);
        d.put("exames", Sql.lista("SELECT t.id_tipo_exame, t.nome, t.categoria, t.descricao, t.duracao_min, t.preparo, t.exige_pedido, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 AND l.ativo = 1 ORDER BY t.categoria, t.nome"));
        d.put("vacinas", new Vacina().catalogo());
        return d;
    }

    // ------------------------------------------------------------------ auxiliares

    private static long criarUsuario(Connection conn, String nome, String email, String senha) throws SQLException {
        long id = Sql.inserir(conn, "INSERT INTO usuarios (nome, email, senha_hash, status, primeiro_acesso) VALUES (?, ?, ?, 1, 0)",
                nome, email, PasswordSecurity.hash(senha));
        Sql.executar(conn, "INSERT INTO usuarios_grupos (id_usuario, id_grupo) SELECT ?, id_grupo FROM grupos WHERE nome = 'paciente'", id);
        return id;
    }

    private static String novoCodigo(Connection conn, long idUsuario, int idPaciente) throws SQLException {
        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        Sql.executar(conn, "UPDATE vinculos_portal SET usado_em = NOW() WHERE id_usuario = ? AND usado_em IS NULL", idUsuario);
        Sql.executar(conn, "INSERT INTO vinculos_portal (id_usuario, id_paciente, codigo_hash, expira_em) VALUES (?, ?, ?, ?)",
                idUsuario, idPaciente, PasswordSecurity.sha256Hex(codigo), LocalDateTime.now().plusMinutes(MINUTOS_CODIGO));
        return codigo;
    }

    private static String texto(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
