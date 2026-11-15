package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Usuario;
import utils.Base64Files;
import utils.JsonResponse;
import utils.PasswordSecurity;
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

    private static final int MAX_FOTO_BYTES = 2 * 1024 * 1024;

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
            String erroSenha = PasswordSecurity.strengthError(novaSenha);
            if (erroSenha != null) {
                JsonResponse.error(response, 400, "ERR_WEAK_PASSWORD", erroSenha);
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

        Usuario usuarioModel = new Usuario();
        usuarioModel.atualizarPerfil(userId, nome, novaSenha, fotoBase64);

        // getSessionUser() devolve uma copia: e preciso gravar o mapa de volta na sessao.
        sessionUser.put("nome", nome);
        if (fotoBase64 != null) {
            sessionUser.put("foto", fotoBase64);
        }
        request.getSession().setAttribute("user", sessionUser);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nome", nome);
        data.put("foto", sessionUser.get("foto"));

        JsonResponse.success(response, data, "Perfil atualizado com sucesso!");
    }
}
