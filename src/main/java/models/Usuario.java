package models;

import database.DBConnection;
import database.DBQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.CodeGenerator;
import utils.PasswordSecurity;

/**
 * ZTF_J2EE - Model Usuario
 *
 * Encapsula autenticacao BCrypt+Pepper, gestao de credenciais e resolucao da arvore RBAC
 * (Grupos e Permissoes).
 */
public class Usuario {

    public Usuario() {
    }

    // =========================================================================
    // MÉTODOS DE DOMÍNIO RBAC & AUTENTICAÇÃO (ZTF_J2EE)
    // =========================================================================

    // -------------------------------------------------------------------------
    // Resultado detalhado do login (Fase 4): o controller precisa distinguir os casos
    // para bloquear apos falhas, mas a mensagem ao usuario continua generica.
    // -------------------------------------------------------------------------

    public enum StatusLogin { SUCESSO, CREDENCIAIS_INVALIDAS, BLOQUEADO }

    /** idUsuario e preenchido quando o e-mail pertence a um usuario (para contar falhas). */
    public record ResultadoLogin(StatusLogin status, Integer idUsuario, Map<String, Object> sessao) {
    }

    /**
     * Hash BCrypt de referencia usado quando o e-mail nao existe: a verificacao gasta o mesmo
     * tempo que a de um usuario real, dificultando descobrir e-mails cadastrados pelo tempo
     * de resposta.
     */
    private static volatile String hashFicticio;

    private static String hashFicticio() {
        if (hashFicticio == null) {
            hashFicticio = PasswordSecurity.hash("senha-ficticia-para-equalizar-tempo-" + System.nanoTime());
        }
        return hashFicticio;
    }

    /** Versao global da ACL: incrementada quando grupos, permissoes ou status mudam. */
    private static final java.util.concurrent.atomic.AtomicLong VERSAO_ACL =
            new java.util.concurrent.atomic.AtomicLong(1);

    public static long versaoAcl() {
        return VERSAO_ACL.get();
    }

    /** Faz as sessoes ativas recarregarem grupos/permissoes/status na proxima requisicao (RN-AUT-06). */
    public static void invalidarAcl() {
        VERSAO_ACL.incrementAndGet();
    }

    public ResultadoLogin autenticarDetalhado(String emailInput, String senhaInput) {
        Map<String, Object> user = new DBQuery("usuarios", "id_usuario, senha_hash, status")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (user == null) {
            PasswordSecurity.verify(senhaInput, hashFicticio());
            return new ResultadoLogin(StatusLogin.CREDENCIAIS_INVALIDAS, null, null);
        }

        int idUsr = toInt(user.get("id_usuario"), 0);
        if (SegurancaLogin.estaBloqueado(idUsr)) {
            return new ResultadoLogin(StatusLogin.BLOQUEADO, idUsr, null);
        }

        String storedHash = String.valueOf(user.getOrDefault("senha_hash", ""));
        boolean senhaValida = PasswordSecurity.verify(senhaInput, storedHash);
        if (!senhaValida && PasswordSecurity.verifyLegacyUnpeppered(senhaInput, storedHash)) {
            senhaValida = true;
            Map<String, Object> updateMap = new LinkedHashMap<>();
            updateMap.put("senha_hash", PasswordSecurity.hash(senhaInput));
            new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", idUsr).update(updateMap);
        }

        if (!senhaValida || toInt(user.get("status"), 0) != 1) {
            return new ResultadoLogin(StatusLogin.CREDENCIAIS_INVALIDAS, idUsr, null);
        }
        return new ResultadoLogin(StatusLogin.SUCESSO, idUsr, carregarSessao(idUsr));
    }

    /** Compatibilidade com o framework original: devolve os dados da sessao ou null. */
    public Map<String, Object> autenticar(String emailInput, String senhaInput) {
        try {
            ResultadoLogin r = autenticarDetalhado(emailInput, senhaInput);
            return r.status() == StatusLogin.SUCESSO ? r.sessao() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Monta (ou recarrega) os dados de sessao de um usuario ATIVO: nome, e-mail, foto,
     * primeiro_acesso, grupos, permissoes e a versao da ACL. Devolve null se o usuario nao
     * existir ou estiver inativo.
     */
    public Map<String, Object> carregarSessao(int idUsr) {
        Map<String, Object> user = new DBQuery("usuarios", "id_usuario, nome, email, foto, status, primeiro_acesso")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .first();
        if (user == null || toInt(user.get("status"), 0) != 1) {
            return null;
        }

        List<Map<String, Object>> gruposRows = new DBQuery("grupos g", "g.id_grupo, g.nome, g.is_admin")
                .joinRaw("INNER JOIN usuarios_grupos ug ON ug.id_grupo = g.id_grupo")
                .addCondition("AND", "ug.id_usuario", "=", idUsr)
                .select();

        List<String> grupos = new ArrayList<>();
        boolean isAdmin = false;
        for (Map<String, Object> g : gruposRows) {
            grupos.add(String.valueOf(g.get("nome")));
            if (toInt(g.get("is_admin"), 0) == 1) {
                isAdmin = true;
            }
        }
        if (isAdmin && !grupos.contains("admin")) {
            grupos.add("admin");
        }

        Map<String, Object> sessionData = new LinkedHashMap<>();
        sessionData.put("id_usuario", idUsr);
        sessionData.put("nome", String.valueOf(user.get("nome")));
        sessionData.put("email", String.valueOf(user.get("email")));
        Object fotoVal = user.get("foto");
        sessionData.put("foto", (fotoVal != null && !String.valueOf(fotoVal).isEmpty()) ? String.valueOf(fotoVal) : null);
        sessionData.put("primeiro_acesso", toInt(user.get("primeiro_acesso"), 0));
        sessionData.put("grupos", grupos);
        sessionData.put("permissoes", obterPermissoes(idUsr));
        sessionData.put("acl_versao", versaoAcl());
        return sessionData;
    }

    /**
     * Troca a senha conferindo a atual (RF-AUT-04). Retorna mensagem de erro ou null.
     * Tambem encerra o primeiro acesso e invalida pedidos de recuperacao pendentes.
     */
    public String alterarSenha(int idUsr, String senhaAtual, String novaSenha) {
        Map<String, Object> user = new DBQuery("usuarios", "senha_hash")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .first();
        if (user == null || !PasswordSecurity.verify(senhaAtual, String.valueOf(user.get("senha_hash")))) {
            return "Senha atual incorreta.";
        }
        String erro = PasswordSecurity.strengthError(novaSenha);
        if (erro != null) {
            return erro;
        }
        if (novaSenha.equals(senhaAtual)) {
            return "A nova senha deve ser diferente da atual.";
        }
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("senha_hash", PasswordSecurity.hash(novaSenha));
        dados.put("primeiro_acesso", 0);
        new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", idUsr).update(dados);

        try {
            RecuperacaoSenha.invalidarPendentes(idUsr);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Falha ao invalidar pedidos de recuperacao", e);
        }
        return null;
    }

    public Map<String, Object> cadastrarComSenhaAutomatica(String nomeInput, String emailInput) {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> existe = new DBQuery("usuarios", "id_usuario")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (existe != null) {
            res.put("status", false);
            res.put("message", "Este e-mail já está cadastrado no sistema.");
            return res;
        }

        String senhaInicial = CodeGenerator.generatePassword(10);
        String hash = PasswordSecurity.hash(senhaInicial);

        Map<String, Object> insertUser = new LinkedHashMap<>();
        insertUser.put("nome", nomeInput);
        insertUser.put("email", emailInput);
        insertUser.put("senha_hash", hash);
        insertUser.put("status", 1);
        insertUser.put("primeiro_acesso", 1);

        int idUsr = (int) new DBQuery("usuarios").insert(insertUser);

        Map<String, Object> grupoRow = new DBQuery("grupos", "id_grupo")
                .addCondition("AND", "nome", "=", "usuario")
                .first();

        if (grupoRow != null) {
            Map<String, Object> ug = new LinkedHashMap<>();
            ug.put("id_usuario", idUsr);
            ug.put("id_grupo", toInt(grupoRow.get("id_grupo"), 2));
            new DBQuery("usuarios_grupos").insert(ug);
        }

        res.put("status", true);
        res.put("id_usuario", idUsr);
        res.put("senhaInicial", senhaInicial);
        res.put("message", "Usuário registrado com sucesso!");
        return res;
    }

    public Map<String, Object> atualizarPerfil(int idUsr, String nomeInput, String novaSenha, String fotoBase64) {
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("nome", nomeInput);

        if (novaSenha != null && !novaSenha.isEmpty()) {
            updateData.put("senha_hash", PasswordSecurity.hash(novaSenha));
            updateData.put("primeiro_acesso", 0);
        }

        if (fotoBase64 != null) {
            updateData.put("foto", fotoBase64);
        }

        new DBQuery("usuarios")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .update(updateData);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", true);
        res.put("message", "Perfil atualizado com sucesso!");
        return res;
    }

    /**
     * Troca a senha direto pelo e-mail, sem token. NAO exponha em rota publica: qualquer
     * pessoa poderia trocar a senha de outra. A recuperacao publica usa RecuperacaoSenha.
     * Mantido para uso administrativo/testes.
     */
    public boolean redefinirSenhaPorEmail(String emailInput, String novaSenha) {
        Map<String, Object> user = new DBQuery("usuarios", "id_usuario")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (user == null) {
            return false;
        }

        String novoHash = PasswordSecurity.hash(novaSenha);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("senha_hash", novoHash);
        updateData.put("primeiro_acesso", 1);

        int afetados = new DBQuery("usuarios")
                .addCondition("AND", "id_usuario", "=", toInt(user.get("id_usuario"), 0))
                .update(updateData);

        return afetados >= 0;
    }

    public List<Map<String, Object>> listarTodosComGrupos() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.id_usuario, u.nome, u.email, u.foto, u.status, u.criado_em, "
                + "GROUP_CONCAT(g.nome ORDER BY g.nome SEPARATOR ', ') AS grupos, "
                + "GROUP_CONCAT(g.id_grupo ORDER BY g.id_grupo SEPARATOR ',') AS ids_grupos "
                + "FROM usuarios u "
                + "LEFT JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario "
                + "LEFT JOIN grupos g ON g.id_grupo = ug.id_grupo "
                + "GROUP BY u.id_usuario "
                + "ORDER BY u.id_usuario DESC";

        try {
            Connection conn = DBConnection.getStaticConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        String label = meta.getColumnLabel(i);
                        row.put(label, rs.getObject(i));
                    }
                    list.add(row);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void sincronizarGruposUsuario(int idUsr, List<Integer> idsGrupos) {
        new DBQuery("usuarios_grupos")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .delete();

        if (idsGrupos != null) {
            for (Integer idGrupo : idsGrupos) {
                if (idGrupo != null && idGrupo > 0) {
                    Map<String, Object> ug = new LinkedHashMap<>();
                    ug.put("id_usuario", idUsr);
                    ug.put("id_grupo", idGrupo);
                    new DBQuery("usuarios_grupos").insert(ug);
                }
            }
        }
    }

    public void alternarStatus(int idUsr, int novoStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", novoStatus);
        new DBQuery("usuarios")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .update(data);
    }

    public List<String> obterGrupos(int idUsr) {
        List<Map<String, Object>> rows = new DBQuery("grupos g", "g.nome")
                .joinRaw("INNER JOIN usuarios_grupos ug ON ug.id_grupo = g.id_grupo")
                .addCondition("AND", "ug.id_usuario", "=", idUsr)
                .select();
        List<String> grupos = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r.get("nome") != null) {
                grupos.add(String.valueOf(r.get("nome")));
            }
        }
        return grupos;
    }

    public List<String> obterPermissoes(int idUsr) {
        List<Map<String, Object>> rows = new DBQuery("permissoes p", "DISTINCT p.codigo")
                .joinRaw("INNER JOIN grupos_permissoes gp ON gp.id_permissao = p.id_permissao")
                .joinRaw("INNER JOIN usuarios_grupos ug ON ug.id_grupo = gp.id_grupo")
                .addCondition("AND", "ug.id_usuario", "=", idUsr)
                .select();
        List<String> perms = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r.get("codigo") != null) {
                perms.add(String.valueOf(r.get("codigo")));
            }
        }
        return perms;
    }

    public Map<String, Object> criarComSenhaTemporaria(String nomeInput, String emailInput) {
        return cadastrarComSenhaAutomatica(nomeInput, emailInput);
    }

    public Map<String, Object> criarComSenhaTemporaria(String nomeInput, String emailInput, List<String> grupos) {
        Map<String, Object> res = cadastrarComSenhaAutomatica(nomeInput, emailInput);
        if (Boolean.TRUE.equals(res.get("status"))) {
            res.put("senhaTemporaria", res.get("senhaInicial"));
        }
        return res;
    }

    public boolean alterarSenhaPorEmail(String emailInput, String novaSenha) {
        return redefinirSenhaPorEmail(emailInput, novaSenha);
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }
}
