package controllers;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Usuario;
import utils.Base64Files;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - PerfilController
 *
 * Atualiza dados de perfil do usuario logado (Nome, Nova Senha, Foto Base64).
 * O e-mail NUNCA e alterado.
 */
@WebServlet(
    name = "PerfilController",
    urlPatterns = {"/api/perfil/atualizar"}
)
public class PerfilController extends Controller {

    private static final long serialVersionUID = 1L;

    public void atualizar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> sessionUser = RequestHelper.getSessionUser(request);
        int userId = RequestHelper.getSessionUserId(request);

        if (sessionUser == null || userId <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Sessão expirada. Faça login novamente.");
            return;
        }

        String nome       = Sanitize.string(RequestHelper.getParam(request, "nome", ""));
        String novaSenha  = RequestHelper.getParam(request, "nova_senha", "");
        String confSenha  = RequestHelper.getParam(request, "confirmar_nova_senha", "");
        String fotoBase64 = RequestHelper.getParam(request, "foto_base64", "");

        if (nome.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_NAME", "O nome é obrigatório.");
            return;
        }

        if (!novaSenha.isEmpty()) {
            if (novaSenha.length() < 6) {
                JsonResponse.error(response, 400, "ERR_SHORT_PASSWORD", "A nova senha deve possuir no mínimo 6 caracteres.");
                return;
            }
            if (!novaSenha.equals(confSenha)) {
                JsonResponse.error(response, 400, "ERR_PASSWORD_MISMATCH", "A confirmação da nova senha não confere.");
                return;
            }
        } else {
            novaSenha = null;
        }

        if (fotoBase64 != null && !fotoBase64.trim().isEmpty()) {
            fotoBase64 = fotoBase64.trim();
            if (!fotoBase64.startsWith("data:image/")) {
                try {
                    Base64.getMimeDecoder().decode(Base64Files.stripDataUriPrefix(fotoBase64));
                } catch (IllegalArgumentException e) {
                    JsonResponse.error(response, 400, "ERR_INVALID_IMAGE", "Arquivo de foto em formato Base64 inválido.");
                    return;
                }
            }
        } else {
            fotoBase64 = null;
        }

        Usuario usuarioModel = new Usuario();
        usuarioModel.atualizarPerfil(userId, nome, novaSenha, fotoBase64);

        sessionUser.put("nome", nome);
        if (fotoBase64 != null) {
            sessionUser.put("foto", fotoBase64);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nome", nome);
        data.put("foto", sessionUser.get("foto"));

        JsonResponse.success(response, data, "Perfil atualizado com sucesso!");
    }
}
