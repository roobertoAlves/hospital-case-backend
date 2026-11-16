package controllers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import database.DBConnection;
import database.DBQuery;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Usuario;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - AdminController
 *
 * Painel de governanca RBAC completo (usuarios, grupos, status e matriz de permissoes).
 */
@WebServlet(
    name = "AdminController",
    urlPatterns = {
        "/admin",
        "/api/admin/usuarios",
        "/api/admin/usuario/grupos",
        "/api/admin/usuario/status",
        "/api/admin/grupos",
        "/api/admin/grupo/permissoes",
        "/api/admin/usuario/desbloquear"
    }
)
public class AdminController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final Gson GSON = new Gson();

    public void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        renderView(request, response, "admin.jsp");
    }

    public void listarUsuarios(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Usuario usuarioModel = new Usuario();
        List<Map<String, Object>> usuarios = usuarioModel.listarTodosComGrupos();
        JsonResponse.success(response, usuarios);
    }

    public void salvarGruposUsuario(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = Sanitize.intVal(RequestHelper.getParam(request, "id_usuario", "0"), 0);
        String gruposRaw = RequestHelper.getParam(request, "grupos", "[]");

        if (idUsuario <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de usuário inválido.");
            return;
        }

        List<Integer> idsGrupos = parseIntegerList(gruposRaw);

        if (idsGrupos.isEmpty()) {
            Map<String, Object> padrao = new DBQuery("grupos", "id_grupo")
                    .addCondition("AND", "nome", "=", "usuario")
                    .first();
            if (padrao != null && padrao.get("id_grupo") != null) {
                idsGrupos.add(Sanitize.intVal(padrao.get("id_grupo"), 2));
            }
        }

        Usuario usuarioModel = new Usuario();
        usuarioModel.sincronizarGruposUsuario(idUsuario, idsGrupos);
        Usuario.invalidarAcl();
        auditar(request, "usuario.grupos_alterados", "usuarios", idUsuario, null, "grupos=" + idsGrupos);

        JsonResponse.success(response, null, "Grupos do usuário atualizados com sucesso!");
    }

    public void alternarStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario  = Sanitize.intVal(RequestHelper.getParam(request, "id_usuario", "0"), 0);
        int novoStatus = Sanitize.intVal(RequestHelper.getParam(request, "status", "0"), 0);

        if (idUsuario <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de usuário inválido.");
            return;
        }

        int idLogado = RequestHelper.getSessionUserId(request);
        if (idLogado > 0 && idUsuario == idLogado && novoStatus == 0) {
            JsonResponse.error(response, 400, "ERR_SELF_DEACTIVATE", "Você não pode inativar sua própria conta de administrador.");
            return;
        }

        Usuario usuarioModel = new Usuario();
        usuarioModel.alternarStatus(idUsuario, novoStatus);
        Usuario.invalidarAcl();
        auditar(request, novoStatus == 1 ? "usuario.ativado" : "usuario.inativado", "usuarios", idUsuario, null, null);

        JsonResponse.success(response, null, "Status do usuário alterado com sucesso!");
    }

    public void gruposEPermissoes(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> grupos = new DBQuery("grupos", "*").orderBy("id_grupo", "ASC").select();
        List<Map<String, Object>> permissoes = new DBQuery("permissoes", "*").orderBy("id_permissao", "ASC").select();
        List<Map<String, Object>> relacoes = new DBQuery("grupos_permissoes", "id_grupo, id_permissao").select();

        Map<Integer, List<Integer>> mapPermissoes = new LinkedHashMap<>();
        for (Map<String, Object> r : relacoes) {
            int idG = Sanitize.intVal(r.get("id_grupo"), 0);
            int idP = Sanitize.intVal(r.get("id_permissao"), 0);
            mapPermissoes.computeIfAbsent(idG, k -> new ArrayList<>()).add(idP);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("grupos", grupos);
        data.put("permissoes", permissoes);
        data.put("grupo_permissoes", mapPermissoes);

        JsonResponse.success(response, data);
    }

    public void salvarPermissoesGrupo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idGrupo = Sanitize.intVal(RequestHelper.getParam(request, "id_grupo", "0"), 0);
        String permsRaw = RequestHelper.getParam(request, "permissoes", "[]");

        if (idGrupo <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de grupo inválido.");
            return;
        }

        List<Integer> permissoes = parseIntegerList(permsRaw);

        try {
            Connection conn = DBConnection.getStaticConnection();
            try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM grupos_permissoes WHERE id_grupo = ?")) {
                psDel.setInt(1, idGrupo);
                psDel.executeUpdate();
            }
            try (PreparedStatement psIns = conn.prepareStatement("INSERT INTO grupos_permissoes (id_grupo, id_permissao) VALUES (?, ?)")) {
                for (Integer idPerm : permissoes) {
                    if (idPerm != null && idPerm > 0) {
                        psIns.setInt(1, idGrupo);
                        psIns.setInt(2, idPerm);
                        psIns.executeUpdate();
                    }
                }
            }
            Usuario.invalidarAcl();
            auditar(request, "grupo.permissoes_alteradas", "grupos", idGrupo, null, "permissoes=" + permissoes);
            JsonResponse.success(response, null, "Permissões do grupo atualizadas com sucesso!");
        } catch (Exception e) {
            JsonResponse.serverError(response, "Erro ao atualizar permissões", e);
        }
    }

    /** POST /api/admin/usuario/desbloquear — remove o bloqueio por tentativas de login. */
    public void desbloquearUsuario(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = Sanitize.intVal(RequestHelper.getParam(request, "id_usuario", "0"), 0);
        if (idUsuario <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de usuário inválido.");
            return;
        }
        models.SegurancaLogin.desbloquear(idUsuario);
        auditar(request, "usuario.desbloqueado", "usuarios", idUsuario, null, null);
        JsonResponse.success(response, null, "Usuário desbloqueado.");
    }

    private List<Integer> parseIntegerList(String raw) {
        List<Integer> list = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return list;
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("[")) {
                Type type = new TypeToken<List<Object>>() {}.getType();
                List<Object> parsed = GSON.fromJson(trimmed, type);
                if (parsed != null) {
                    for (Object o : parsed) {
                        int v = Sanitize.intVal(o, 0);
                        if (v > 0) list.add(v);
                    }
                }
            } else {
                for (String part : trimmed.split(",")) {
                    int v = Sanitize.intVal(part.trim(), 0);
                    if (v > 0) list.add(v);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
