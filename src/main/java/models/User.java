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
public class User {

    public User() {
    }

    // =========================================================================
    // MÉTODOS DE DOMÍNIO RBAC & AUTENTICAÇÃO (ZTF_J2EE)
    // =========================================================================

    // -------------------------------------------------------------------------
    // Resultado detalhado do login (Fase 4): o controller precisa distinguir os casos
    // para bloquear apos falhas, mas a mensagem ao usuario continua generica.
    // -------------------------------------------------------------------------

    public enum LoginStatus { SUCCESS, INVALID_CREDENTIALS, BLOCKED }

    /** idUsuario e preenchido quando o e-mail pertence a um usuario (para contar falhas). */
    public record LoginResult(LoginStatus status, Integer userId, Map<String, Object> session) {
    }

    /**
     * Hash BCrypt de referencia usado quando o e-mail nao existe: a verificacao gasta o mesmo
     * tempo que a de um usuario real, dificultando descobrir e-mails cadastrados pelo tempo
     * de resposta.
     */
    private static volatile String fakeHash;

    private static String fakeHash() {
        if (fakeHash == null) {
            fakeHash = PasswordSecurity.hash("senha-ficticia-para-equalizar-tempo-" + System.nanoTime());
        }
        return fakeHash;
    }

    /** Versao global da ACL: incrementada quando grupos, permissoes ou status mudam. */
    private static final java.util.concurrent.atomic.AtomicLong VERSION_ACL =
            new java.util.concurrent.atomic.AtomicLong(1);

    public static long versionAcl() {
        return VERSION_ACL.get();
    }

    /** Faz as sessoes ativas recarregarem grupos/permissoes/status na proxima requisicao (RN-AUT-06). */
    public static void invalidateAcl() {
        VERSION_ACL.incrementAndGet();
    }

    public LoginResult authenticateDetailed(String emailInput, String passwordInput) {
        Map<String, Object> user = new DBQuery("usuarios", "id_usuario, senha_hash, status")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (user == null) {
            PasswordSecurity.verify(passwordInput, fakeHash());
            return new LoginResult(LoginStatus.INVALID_CREDENTIALS, null, null);
        }

        int idUsr = toInt(user.get("id_usuario"), 0);
        if (LoginSecurity.isBlocked(idUsr)) {
            return new LoginResult(LoginStatus.BLOCKED, idUsr, null);
        }

        String storedHash = String.valueOf(user.getOrDefault("senha_hash", ""));
        boolean validPassword = PasswordSecurity.verify(passwordInput, storedHash);
        if (!validPassword && PasswordSecurity.verifyLegacyUnpeppered(passwordInput, storedHash)) {
            validPassword = true;
            Map<String, Object> updateMap = new LinkedHashMap<>();
            updateMap.put("senha_hash", PasswordSecurity.hash(passwordInput));
            new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", idUsr).update(updateMap);
        }

        if (!validPassword || toInt(user.get("status"), 0) != 1) {
            return new LoginResult(LoginStatus.INVALID_CREDENTIALS, idUsr, null);
        }
        return new LoginResult(LoginStatus.SUCCESS, idUsr, loadSession(idUsr));
    }

    /** Compatibilidade com o framework original: devolve os dados da sessao ou null. */
    public Map<String, Object> authenticate(String emailInput, String passwordInput) {
        try {
            LoginResult r = authenticateDetailed(emailInput, passwordInput);
            return r.status() == LoginStatus.SUCCESS ? r.session() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Monta (ou recarrega) os dados de sessao de um usuario ATIVO: nome, e-mail, foto,
     * primeiro_acesso, grupos, permissoes e a versao da ACL. Devolve null se o usuario nao
     * existir ou estiver inativo.
     */
    public Map<String, Object> loadSession(int idUsr) {
        Map<String, Object> user = new DBQuery("usuarios", "id_usuario, nome, email, foto, status, primeiro_acesso")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .first();
        if (user == null || toInt(user.get("status"), 0) != 1) {
            return null;
        }

        List<Map<String, Object>> groupsRows = new DBQuery("grupos g", "g.id_grupo, g.nome, g.is_admin")
                .joinRaw("INNER JOIN usuarios_grupos ug ON ug.id_grupo = g.id_grupo")
                .addCondition("AND", "ug.id_usuario", "=", idUsr)
                .select();

        List<String> groups = new ArrayList<>();
        boolean isAdmin = false;
        for (Map<String, Object> g : groupsRows) {
            groups.add(String.valueOf(g.get("nome")));
            if (toInt(g.get("is_admin"), 0) == 1) {
                isAdmin = true;
            }
        }
        if (isAdmin && !groups.contains("admin")) {
            groups.add("admin");
        }

        Map<String, Object> sessionData = new LinkedHashMap<>();
        sessionData.put("id_usuario", idUsr);
        sessionData.put("nome", String.valueOf(user.get("nome")));
        sessionData.put("email", String.valueOf(user.get("email")));
        Object fotoVal = user.get("foto");
        sessionData.put("foto", (fotoVal != null && !String.valueOf(fotoVal).isEmpty()) ? String.valueOf(fotoVal) : null);
        sessionData.put("primeiro_acesso", toInt(user.get("primeiro_acesso"), 0));
        sessionData.put("grupos", groups);
        sessionData.put("permissoes", getPermissions(idUsr));
        sessionData.put("acl_versao", versionAcl());
        return sessionData;
    }

    /**
     * Troca a senha conferindo a atual (RF-AUT-04). Retorna mensagem de erro ou null.
     * Tambem encerra o primeiro acesso e invalida pedidos de recuperacao pendentes.
     */
    public String changePassword(int idUsr, String currentPassword, String newPassword) {
        Map<String, Object> user = new DBQuery("usuarios", "senha_hash")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .first();
        if (user == null || !PasswordSecurity.verify(currentPassword, String.valueOf(user.get("senha_hash")))) {
            return "Senha atual incorreta.";
        }
        String error = PasswordSecurity.strengthError(newPassword);
        if (error != null) {
            return error;
        }
        if (newPassword.equals(currentPassword)) {
            return "A nova senha deve ser diferente da atual.";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("senha_hash", PasswordSecurity.hash(newPassword));
        data.put("primeiro_acesso", 0);
        new DBQuery("usuarios").addCondition("AND", "id_usuario", "=", idUsr).update(data);

        try {
            PasswordRecovery.invalidatePending(idUsr);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Falha ao invalidar pedidos de recuperacao", e);
        }
        return null;
    }

    public Map<String, Object> registerWithAutomaticPassword(String nameInput, String emailInput) {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> exists = new DBQuery("usuarios", "id_usuario")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (exists != null) {
            res.put("status", false);
            res.put("message", "Este e-mail já está cadastrado no sistema.");
            return res;
        }

        String initialPassword = CodeGenerator.generatePassword(10);
        String hash = PasswordSecurity.hash(initialPassword);

        Map<String, Object> insertUser = new LinkedHashMap<>();
        insertUser.put("nome", nameInput);
        insertUser.put("email", emailInput);
        insertUser.put("senha_hash", hash);
        insertUser.put("status", 1);
        insertUser.put("primeiro_acesso", 1);

        int idUsr = (int) new DBQuery("usuarios").insert(insertUser);

        Map<String, Object> groupRow = new DBQuery("grupos", "id_grupo")
                .addCondition("AND", "nome", "=", "usuario")
                .first();

        if (groupRow != null) {
            Map<String, Object> ug = new LinkedHashMap<>();
            ug.put("id_usuario", idUsr);
            ug.put("id_grupo", toInt(groupRow.get("id_grupo"), 2));
            new DBQuery("usuarios_grupos").insert(ug);
        }

        res.put("status", true);
        res.put("id_usuario", idUsr);
        res.put("initialPassword", initialPassword);
        res.put("message", "Usuário registrado com sucesso!");
        return res;
    }

    public Map<String, Object> updateProfile(int idUsr, String nameInput, String newPassword, String fotoBase64) {
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("nome", nameInput);

        if (newPassword != null && !newPassword.isEmpty()) {
            updateData.put("senha_hash", PasswordSecurity.hash(newPassword));
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
    public boolean resetPasswordByEmail(String emailInput, String newPassword) {
        Map<String, Object> user = new DBQuery("usuarios", "id_usuario")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (user == null) {
            return false;
        }

        String newHash = PasswordSecurity.hash(newPassword);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("senha_hash", newHash);
        updateData.put("primeiro_acesso", 1);

        int affected = new DBQuery("usuarios")
                .addCondition("AND", "id_usuario", "=", toInt(user.get("id_usuario"), 0))
                .update(updateData);

        return affected >= 0;
    }

    public List<Map<String, Object>> listAllWithGroups() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.id_usuario, u.nome, u.email, u.foto, u.status, u.criado_em, "
                + "GROUP_CONCAT(g.nome ORDER BY g.nome SEPARATOR ', ') AS grupos, "
                + "GROUP_CONCAT(g.id_grupo ORDER BY g.id_grupo SEPARATOR ',') AS ids_grupos, "
                + "(SELECT IF(s.bloqueado_ate > NOW(), 1, 0) FROM usuarios_seguranca s WHERE s.id_usuario = u.id_usuario) AS bloqueado, "
                + "(SELECT s.ultimo_login_em FROM usuarios_seguranca s WHERE s.id_usuario = u.id_usuario) AS ultimo_login_em, "
                + "(SELECT p.nome FROM profissionais p WHERE p.id_usuario = u.id_usuario) AS profissional, "
                + "(SELECT p.id_profissional FROM profissionais p WHERE p.id_usuario = u.id_usuario) AS id_profissional "
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

    public void syncUserGroups(int idUsr, List<Integer> groupIds) {
        new DBQuery("usuarios_grupos")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .delete();

        if (groupIds != null) {
            for (Integer groupId : groupIds) {
                if (groupId != null && groupId > 0) {
                    Map<String, Object> ug = new LinkedHashMap<>();
                    ug.put("id_usuario", idUsr);
                    ug.put("id_grupo", groupId);
                    new DBQuery("usuarios_grupos").insert(ug);
                }
            }
        }
    }

    public void toggleStatus(int idUsr, int newStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", newStatus);
        new DBQuery("usuarios")
                .addCondition("AND", "id_usuario", "=", idUsr)
                .update(data);
    }

    public List<String> getGroups(int idUsr) {
        List<Map<String, Object>> rows = new DBQuery("grupos g", "g.nome")
                .joinRaw("INNER JOIN usuarios_grupos ug ON ug.id_grupo = g.id_grupo")
                .addCondition("AND", "ug.id_usuario", "=", idUsr)
                .select();
        List<String> groups = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r.get("nome") != null) {
                groups.add(String.valueOf(r.get("nome")));
            }
        }
        return groups;
    }

    public List<String> getPermissions(int idUsr) {
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

    public Map<String, Object> createWithTemporaryPassword(String nameInput, String emailInput) {
        return registerWithAutomaticPassword(nameInput, emailInput);
    }

    public Map<String, Object> createWithTemporaryPassword(String nameInput, String emailInput, List<String> groups) {
        Map<String, Object> res = registerWithAutomaticPassword(nameInput, emailInput);
        if (Boolean.TRUE.equals(res.get("status"))) {
            res.put("temporaryPassword", res.get("initialPassword"));
        }
        return res;
    }

    public boolean changePasswordByEmail(String emailInput, String newPassword) {
        return resetPasswordByEmail(emailInput, newPassword);
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

    /** Friendly name of the user's main role (shown in the staff header). */
    public static String roleLabel(List<String> groups) {
        if (groups == null) {
            return "Usuário";
        }
        if (groups.contains("medico")) {
            return "Médico";
        }
        if (groups.contains("enfermeiro")) {
            return "Enfermagem";
        }
        if (groups.contains("recepcionista")) {
            return "Recepção";
        }
        if (groups.contains("admin")) {
            return "Administrador";
        }
        if (groups.contains("paciente")) {
            return "Paciente";
        }
        return "Usuário";
    }
}
