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

@WebServlet(name = "ProfileController", urlPatterns = {"/api/profile"})
public class ProfileController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;

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
        String photoBase64 = RequestHelper.getParam(request, "photo_base64", "");

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

        if (photoBase64 != null && !photoBase64.trim().isEmpty()) {
            photoBase64 = photoBase64.trim();
            if (!Base64Files.isSafeImageDataUri(photoBase64, MAX_PHOTO_BYTES)) {
                JsonResponse.error(response, 400, "ERR_INVALID_IMAGE",
                        "Foto inválida: envie uma imagem PNG, JPEG, GIF ou WEBP de até 2 MB.");
                return;
            }
        } else {
            photoBase64 = null;
        }

        User userModel = new User();
        userModel.updateProfile(userId, name, newPassword, photoBase64);

        sessionUser.put("nome", name);
        if (photoBase64 != null) {
            sessionUser.put("foto", photoBase64);
        }
        request.getSession().setAttribute("user", sessionUser);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nome", name);
        data.put("foto", sessionUser.get("foto"));

        JsonResponse.success(response, data, "Perfil atualizado com sucesso!");
    }
}
