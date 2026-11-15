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

    public Map<String, Object> autenticar(String emailInput, String senhaInput) {
        try {
            Map<String, Object> user = new DBQuery("usuarios", "idUsuario, nome, email, senha, foto, status, primeiroAcesso")
                    .addCondition("AND", "email", "=", emailInput)
                    .first();

            if (user == null) {
                return null;
            }

            int userStatus = toInt(user.get("status"), 0);
            if (userStatus != 1) {
                return null;
            }

            String storedHash = String.valueOf(user.getOrDefault("senha", ""));
            boolean senhaValida = false;

            if (PasswordSecurity.verify(senhaInput, storedHash)) {
                senhaValida = true;
            } else if (PasswordSecurity.verifyLegacyUnpeppered(senhaInput, storedHash)) {
                senhaValida = true;
                String novoHash = PasswordSecurity.hash(senhaInput);
                Map<String, Object> updateMap = new LinkedHashMap<>();
                updateMap.put("senha", novoHash);
                new DBQuery("usuarios")
                        .addCondition("AND", "idUsuario", "=", toInt(user.get("idUsuario"), 0))
                        .update(updateMap);
            }

            if (!senhaValida) {
                return null;
            }

            int idUsr = toInt(user.get("idUsuario"), 0);

            List<Map<String, Object>> gruposRows = new DBQuery("grupos g", "g.idGrupo, g.grupo, g.isAdmin")
                    .joinRaw("INNER JOIN usuariosGrupos ug ON ug.idGrupo = g.idGrupo")
                    .addCondition("AND", "ug.idUsuario", "=", idUsr)
                    .select();

            List<String> grupos = new ArrayList<>();
            boolean isAdmin = false;
            for (Map<String, Object> g : gruposRows) {
                grupos.add(String.valueOf(g.get("grupo")));
                if (toInt(g.get("isAdmin"), 0) == 1) {
                    isAdmin = true;
                }
            }

            List<Map<String, Object>> permsRows = new DBQuery("permissoes p", "DISTINCT p.permissao")
                    .joinRaw("INNER JOIN gruposPermissoes gp ON gp.idPermissao = p.idPermissao")
                    .joinRaw("INNER JOIN usuariosGrupos ug ON ug.idGrupo = gp.idGrupo")
                    .addCondition("AND", "ug.idUsuario", "=", idUsr)
                    .select();

            List<String> permissoes = new ArrayList<>();
            for (Map<String, Object> p : permsRows) {
                if (p.get("permissao") != null) {
                    permissoes.add(String.valueOf(p.get("permissao")));
                }
            }

            if (isAdmin && !grupos.contains("admin")) {
                grupos.add("admin");
            }

            Map<String, Object> sessionData = new LinkedHashMap<>();
            sessionData.put("idUsuario", idUsr);
            sessionData.put("nome", String.valueOf(user.get("nome")));
            sessionData.put("email", String.valueOf(user.get("email")));
            Object fotoVal = user.get("foto");
            sessionData.put("foto", (fotoVal != null && !String.valueOf(fotoVal).isEmpty()) ? String.valueOf(fotoVal) : null);
            sessionData.put("primeiroAcesso", toInt(user.get("primeiroAcesso"), 0));
            sessionData.put("grupos", grupos);
            sessionData.put("permissoes", permissoes);
            return sessionData;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> cadastrarComSenhaAutomatica(String nomeInput, String emailInput) {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> existe = new DBQuery("usuarios", "idUsuario")
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
        insertUser.put("senha", hash);
        insertUser.put("status", 1);
        insertUser.put("primeiroAcesso", 1);

        int idUsr = (int) new DBQuery("usuarios").insert(insertUser);

        Map<String, Object> grupoRow = new DBQuery("grupos", "idGrupo")
                .addCondition("AND", "grupo", "=", "usuario")
                .first();

        if (grupoRow != null) {
            Map<String, Object> ug = new LinkedHashMap<>();
            ug.put("idUsuario", idUsr);
            ug.put("idGrupo", toInt(grupoRow.get("idGrupo"), 2));
            new DBQuery("usuariosGrupos").insert(ug);
        }

        res.put("status", true);
        res.put("idUsuario", idUsr);
        res.put("senhaInicial", senhaInicial);
        res.put("message", "Usuário registrado com sucesso!");
        return res;
    }

    public Map<String, Object> atualizarPerfil(int idUsr, String nomeInput, String novaSenha, String fotoBase64) {
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("nome", nomeInput);

        if (novaSenha != null && !novaSenha.isEmpty()) {
            updateData.put("senha", PasswordSecurity.hash(novaSenha));
            updateData.put("primeiroAcesso", 0);
        }

        if (fotoBase64 != null) {
            updateData.put("foto", fotoBase64);
        }

        new DBQuery("usuarios")
                .addCondition("AND", "idUsuario", "=", idUsr)
                .update(updateData);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", true);
        res.put("message", "Perfil atualizado com sucesso!");
        return res;
    }

    public boolean redefinirSenhaPorEmail(String emailInput, String novaSenha) {
        Map<String, Object> user = new DBQuery("usuarios", "idUsuario")
                .addCondition("AND", "email", "=", emailInput)
                .first();

        if (user == null) {
            return false;
        }

        String novoHash = PasswordSecurity.hash(novaSenha);
        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("senha", novoHash);
        updateData.put("primeiroAcesso", 1);

        int afetados = new DBQuery("usuarios")
                .addCondition("AND", "idUsuario", "=", toInt(user.get("idUsuario"), 0))
                .update(updateData);

        return afetados >= 0;
    }

    public List<Map<String, Object>> listarTodosComGrupos() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT u.idUsuario, u.nome, u.email, u.foto, u.status, u.dataCriacao, "
                + "GROUP_CONCAT(g.grupo ORDER BY g.grupo SEPARATOR ', ') AS grupos, "
                + "GROUP_CONCAT(g.idGrupo ORDER BY g.idGrupo SEPARATOR ',') AS idsGrupos "
                + "FROM usuarios u "
                + "LEFT JOIN usuariosGrupos ug ON ug.idUsuario = u.idUsuario "
                + "LEFT JOIN grupos g ON g.idGrupo = ug.idGrupo "
                + "GROUP BY u.idUsuario "
                + "ORDER BY u.idUsuario DESC";

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
        new DBQuery("usuariosGrupos")
                .addCondition("AND", "idUsuario", "=", idUsr)
                .delete();

        if (idsGrupos != null) {
            for (Integer idGrupo : idsGrupos) {
                if (idGrupo != null && idGrupo > 0) {
                    Map<String, Object> ug = new LinkedHashMap<>();
                    ug.put("idUsuario", idUsr);
                    ug.put("idGrupo", idGrupo);
                    new DBQuery("usuariosGrupos").insert(ug);
                }
            }
        }
    }

    public void alternarStatus(int idUsr, int novoStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", novoStatus);
        new DBQuery("usuarios")
                .addCondition("AND", "idUsuario", "=", idUsr)
                .update(data);
    }

    public List<String> obterGrupos(int idUsr) {
        List<Map<String, Object>> rows = new DBQuery("grupos g", "g.grupo")
                .joinRaw("INNER JOIN usuariosGrupos ug ON ug.idGrupo = g.idGrupo")
                .addCondition("AND", "ug.idUsuario", "=", idUsr)
                .select();
        List<String> grupos = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r.get("grupo") != null) {
                grupos.add(String.valueOf(r.get("grupo")));
            }
        }
        return grupos;
    }

    public List<String> obterPermissoes(int idUsr) {
        List<Map<String, Object>> rows = new DBQuery("permissoes p", "DISTINCT p.permissao")
                .joinRaw("INNER JOIN gruposPermissoes gp ON gp.idPermissao = p.idPermissao")
                .joinRaw("INNER JOIN usuariosGrupos ug ON ug.idGrupo = gp.idGrupo")
                .addCondition("AND", "ug.idUsuario", "=", idUsr)
                .select();
        List<String> perms = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r.get("permissao") != null) {
                perms.add(String.valueOf(r.get("permissao")));
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
