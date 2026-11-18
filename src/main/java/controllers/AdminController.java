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
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.User;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "AdminController", urlPatterns = {"/api/admin/*"})
public class AdminController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final Gson GSON = new Gson();

    public void listUsers(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User userModel = new User();
        List<Map<String, Object>> users = userModel.listAllWithGroups();
        JsonResponse.success(response, users);
    }

    public void saveUserGroups(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = Sanitize.intVal(RequestHelper.getParam(request, "id_usuario", "0"), 0);
        String groupsRaw = RequestHelper.getParam(request, "groups", "[]");

        if (userId <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de usuário inválido.");
            return;
        }

        List<Integer> groupIds = parseIntegerList(groupsRaw);

        if (groupIds.isEmpty()) {
            Map<String, Object> defaultValue = new DBQuery("grupos", "id_grupo")
                    .addCondition("AND", "nome", "=", "usuario")
                    .first();
            if (defaultValue != null && defaultValue.get("id_grupo") != null) {
                groupIds.add(Sanitize.intVal(defaultValue.get("id_grupo"), 2));
            }
        }

        User userModel = new User();
        userModel.syncUserGroups(userId, groupIds);
        User.invalidateAcl();
        audit(request, "usuario.grupos_alterados", "usuarios", userId, null, "grupos=" + groupIds);

        JsonResponse.success(response, null, "Grupos do usuário atualizados com sucesso!");
    }

    public void toggleStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId  = Sanitize.intVal(RequestHelper.getParam(request, "id_usuario", "0"), 0);
        int newStatus = Sanitize.intVal(RequestHelper.getParam(request, "status", "0"), 0);

        if (userId <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de usuário inválido.");
            return;
        }

        int loggedInId = RequestHelper.getSessionUserId(request);
        if (loggedInId > 0 && userId == loggedInId && newStatus == 0) {
            JsonResponse.error(response, 400, "ERR_SELF_DEACTIVATE", "Você não pode inativar sua própria conta de administrador.");
            return;
        }

        User userModel = new User();
        userModel.toggleStatus(userId, newStatus);
        User.invalidateAcl();
        audit(request, newStatus == 1 ? "usuario.ativado" : "usuario.inativado", "usuarios", userId, null, null);

        JsonResponse.success(response, null, "Status do usuário alterado com sucesso!");
    }

    public void groupsAndPermissions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> groups = new DBQuery("grupos", "*").orderBy("id_grupo", "ASC").select();
        List<Map<String, Object>> permissions = new DBQuery("permissoes", "*").orderBy("id_permissao", "ASC").select();
        List<Map<String, Object>> relations = new DBQuery("grupos_permissoes", "id_grupo, id_permissao").select();

        Map<Integer, List<Integer>> mapPermissions = new LinkedHashMap<>();
        for (Map<String, Object> r : relations) {
            int idG = Sanitize.intVal(r.get("id_grupo"), 0);
            int idP = Sanitize.intVal(r.get("id_permissao"), 0);
            mapPermissions.computeIfAbsent(idG, k -> new ArrayList<>()).add(idP);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groups", groups);
        data.put("permissions", permissions);
        data.put("group_permissions", mapPermissions);

        JsonResponse.success(response, data);
    }

    public void saveGroupPermissions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int groupId = Sanitize.intVal(RequestHelper.getParam(request, "id_grupo", "0"), 0);
        String permsRaw = RequestHelper.getParam(request, "permissions", "[]");

        if (groupId <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de grupo inválido.");
            return;
        }

        List<Integer> permissions = parseIntegerList(permsRaw);

        try {
            Connection conn = DBConnection.getStaticConnection();
            try (PreparedStatement psDel = conn.prepareStatement("DELETE FROM grupos_permissoes WHERE id_grupo = ?")) {
                psDel.setInt(1, groupId);
                psDel.executeUpdate();
            }
            try (PreparedStatement psIns = conn.prepareStatement("INSERT INTO grupos_permissoes (id_grupo, id_permissao) VALUES (?, ?)")) {
                for (Integer idPerm : permissions) {
                    if (idPerm != null && idPerm > 0) {
                        psIns.setInt(1, groupId);
                        psIns.setInt(2, idPerm);
                        psIns.executeUpdate();
                    }
                }
            }
            User.invalidateAcl();
            audit(request, "grupo.permissoes_alteradas", "grupos", groupId, null, "permissoes=" + permissions);
            JsonResponse.success(response, null, "Permissões do grupo atualizadas com sucesso!");
        } catch (Exception e) {
            JsonResponse.serverError(response, "Erro ao atualizar permissões", e);
        }
    }

    public void unlockUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = Sanitize.intVal(RequestHelper.getParam(request, "id_usuario", "0"), 0);
        if (userId <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_ID", "ID de usuário inválido.");
            return;
        }
        models.LoginSecurity.unlock(userId);
        audit(request, "usuario.desbloqueado", "usuarios", userId, null, null);
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
