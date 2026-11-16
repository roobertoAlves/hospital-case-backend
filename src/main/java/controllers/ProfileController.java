package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.User;
import utils.Base64Files;
import utils.JsonResponse;
import utils.PasswordSecurity;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - ProfileController
 *
 * Atualiza dados de perfil do usuario logado (Nome, Nova Senha, Foto Base64).
 * O e-mail NUNCA e alterado.
 */
@WebServlet(name = "ProfileController", urlPatterns = {"/api/profile"})
public class ProfileController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final int MAX_FOTO_BYTES = 2 * 1024 * 1024;

    public void update(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> sessionUser = RequestHelper.getSessionUser(request);
        int userId = RequestHelper.getSessionUserId(request);

        if (sessionUser == null || userId <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Sessão expirada. Faça login novamente.");
            return;
        }

        String name       = Sanitize.string(RequestHelper.getParam(request, "nome", ""));
        String newPassword  = RequestHelper.getParam(request, "new_password", "");
        String confirmPassword  = RequestHelper.getParam(request, "confirm_password", "");
        String fotoBase64 = RequestHelper.getParam(request, "foto_base64", "");

        if (name.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_NAME", "O nome é obrigatório.");
            return;
        }

        if (!newPassword.isEmpty()) {
            String passwordError = PasswordSecurity.strengthError(newPassword);
            if (passwordError != null) {
                JsonResponse.error(response, 400, "ERR_WEAK_PASSWORD", passwordError);
                return;
            }
            String currentPassword = RequestHelper.getParam(request, "current_password", "");
            Map<String, Object> current = new database.DBQuery("usuarios", "senha_hash")
                    .addCondition("AND", "id_usuario", "=", userId).first();
            if (current == null || !PasswordSecurity.verify(currentPassword, String.valueOf(current.get("senha_hash")))) {
                JsonResponse.error(response, 400, "ERR_CURRENT_PASSWORD", "Informe a senha atual correta para trocar a senha.");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                JsonResponse.error(response, 400, "ERR_PASSWORD_MISMATCH", "A confirmação da nova senha não confere.");
                return;
            }
        } else {
            newPassword = null;
        }

        if (fotoBase64 != null && !fotoBase64.trim().isEmpty()) {
            fotoBase64 = fotoBase64.trim();
            // A foto e exibida em <img src> em outras telas (ex.: painel admin): so aceita
            // imagem real de tipo permitido, evitando XSS armazenado.
            if (!Base64Files.isSafeImageDataUri(fotoBase64, MAX_FOTO_BYTES)) {
                JsonResponse.error(response, 400, "ERR_INVALID_IMAGE",
                        "Foto inválida: envie uma imagem PNG, JPEG, GIF ou WEBP de até 2 MB.");
                return;
            }
        } else {
            fotoBase64 = null;
        }

        User userModel = new User();
        userModel.updateProfile(userId, name, newPassword, fotoBase64);

        // getSessionUser() devolve uma copia: e preciso gravar o mapa de volta na sessao.
        sessionUser.put("nome", name);
        if (fotoBase64 != null) {
            sessionUser.put("foto", fotoBase64);
        }
        request.getSession().setAttribute("user", sessionUser);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nome", name);
        data.put("foto", sessionUser.get("foto"));

        JsonResponse.success(response, data, "Perfil atualizado com sucesso!");
    }
}
