package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import mail.Mail;
import models.Usuario;
import utils.CodeGenerator;
import utils.Config;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Url;

/**
 * ZTF_J2EE - AuthController
 *
 * Controlador de autenticacao (login, loginAuth, registerAuth, recuperarSenha, logout).
 */
@WebServlet(
    name = "AuthController",
    urlPatterns = {"/login", "/cadastro", "/recuperar-senha", "/logout"}
)
public class AuthController extends Controller {

    private static final long serialVersionUID = 1L;

    public void loginPage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        renderView(request, response, "login.jsp");
    }

    public void loginAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = RequestHelper.getParam(request, "email", "").trim();
        String senha = RequestHelper.getParam(request, "senha", "");

        if (email.isEmpty() || senha.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_FIELDS", "E-mail e senha são obrigatórios.");
            return;
        }

        Usuario usuarioModel = new Usuario();
        Map<String, Object> dadosUsuario = usuarioModel.autenticar(email, senha);

        if (dadosUsuario == null) {
            JsonResponse.error(response, 401, "ERR_INVALID_CREDENTIALS", "Credenciais inválidas. Verifique seu e-mail e senha.");
            return;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("user", dadosUsuario);

        List<String> grupos = RequestHelper.getSessionUserGroups(request);
        String redirect = "dashboard/usuario";
        if (grupos != null) {
            if (grupos.contains("admin")) {
                redirect = "dashboard/admin";
            } else if (grupos.contains("gerente")) {
                redirect = "dashboard/gerente";
            } else if (grupos.contains("financeiro")) {
                redirect = "dashboard/financeiro";
            } else if (grupos.contains("suporte")) {
                redirect = "dashboard/suporte";
            }
        }

        Map<String, Object> usuarioInfo = new LinkedHashMap<>();
        usuarioInfo.put("id", dadosUsuario.get("idUsuario"));
        usuarioInfo.put("nome", dadosUsuario.get("nome"));
        usuarioInfo.put("email", dadosUsuario.get("email"));
        usuarioInfo.put("grupos", grupos);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usuario", usuarioInfo);
        data.put("redirect", redirect);

        JsonResponse.success(response, data, "Autenticado com sucesso!");
    }

    public void registerAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String nome  = Sanitize.string(RequestHelper.getParam(request, "nome", ""));
        String email = Sanitize.email(RequestHelper.getParam(request, "email", ""));

        if (nome.isEmpty() || email.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_FIELDS", "Nome e e-mail são obrigatórios.");
            return;
        }

        if (!Sanitize.isValidEmail(email)) {
            JsonResponse.error(response, 400, "ERR_INVALID_EMAIL", "Formato de e-mail inválido.");
            return;
        }

        Usuario usuarioModel = new Usuario();
        Map<String, Object> resultado = usuarioModel.cadastrarComSenhaAutomatica(nome, email);

        if (!Boolean.TRUE.equals(resultado.get("status"))) {
            JsonResponse.error(response, 400, "ERR_REGISTER_FAILED", String.valueOf(resultado.get("message")));
            return;
        }

        String senhaInicial = String.valueOf(resultado.get("senhaInicial"));

        String corpoEmail = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e1e4e8; border-radius: 8px;'>"
                + "<h2 style='color: #0d6efd;'>Bem-vindo ao ZTF_J2EE!</h2>"
                + "<p>Olá, <strong>" + Sanitize.escapeHtml(nome) + "</strong>!</p>"
                + "<p>Sua conta foi criada com sucesso no nível inicial <strong>Usuário</strong>.</p>"
                + "<p>Sua senha inicial de acesso gerada automaticamente pelo sistema é:</p>"
                + "<div style='background: #f8f9fa; padding: 15px; font-size: 20px; font-weight: bold; text-align: center; border-radius: 6px; letter-spacing: 2px; color: #212529; border: 1px solid #dee2e6;'>"
                + senhaInicial
                + "</div>"
                + "<p style='color: #6c757d; font-size: 13px; margin-top: 20px;'>"
                + "Após efetuar o primeiro login, você poderá alterar esta senha e personalizar sua foto no seu perfil."
                + "</p></div>";

        try {
            Mail.quickSend(email, "Sua Senha Inicial de Acesso - ZTF_J2EE", corpoEmail);
        } catch (Exception ignored) {
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", email);
        data.put("nome", nome);
        data.put("simulated_password", Config.getBoolean("mail.simulate_in_dev", false) ? senhaInicial : null);

        JsonResponse.success(response, data, "Cadastro concluído! Sua senha inicial foi gerada e enviada para o seu e-mail.");
    }

    public void recuperarSenha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = Sanitize.email(RequestHelper.getParam(request, "email", ""));

        if (email.isEmpty() || !Sanitize.isValidEmail(email)) {
            JsonResponse.error(response, 400, "ERR_INVALID_EMAIL", "Informe um endereço de e-mail válido.");
            return;
        }

        String novaSenha = CodeGenerator.generatePassword(10);

        Usuario usuarioModel = new Usuario();
        try {
            usuarioModel.redefinirSenhaPorEmail(email, novaSenha);
        } catch (Exception ignored) {
        }

        String corpoEmail = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e1e4e8; border-radius: 8px;'>"
                + "<h2 style='color: #0366d6;'>Recuperação de Senha - ZTF_J2EE</h2>"
                + "<p>Olá,</p>"
                + "<p>Recebemos uma solicitação para redefinir a sua senha de acesso.</p>"
                + "<p>Sua nova senha temporária é:</p>"
                + "<div style='background: #f6f8fa; padding: 15px; font-size: 20px; font-weight: bold; text-align: center; border-radius: 6px; letter-spacing: 2px; color: #24292e;'>"
                + novaSenha
                + "</div>"
                + "<p style='color: #586069; font-size: 13px; margin-top: 20px;'>"
                + "Recomendamos que você acesse o sistema e altere esta senha imediatamente na seção de preferências."
                + "</p><hr style='border: none; border-top: 1px solid #e1e4e8; margin: 20px 0;'>"
                + "<small style='color: #8c959f;'>Se você não solicitou esta alteração, entre em contato imediatamente com o suporte.</small>"
                + "</div>";

        try {
            Mail.quickSend(email, "Recuperação de Senha - ZTF_J2EE", corpoEmail);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("email", email);
            data.put("simulated_password", Config.getBoolean("mail.simulate_in_dev", false) ? novaSenha : null);
            JsonResponse.success(response, data, "Uma nova senha temporária foi gerada e enviada para o seu e-mail!");
        } catch (Exception e) {
            JsonResponse.error(response, 500, "ERR_MAIL_SEND", "Não foi possível enviar o e-mail de recuperação: " + e.getMessage());
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("JSESSIONID".equals(c.getName()) || "ZTSESSID".equals(c.getName())) {
                    c.setValue("");
                    c.setMaxAge(0);
                    c.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
                    response.addCookie(c);
                }
            }
        }

        Url.redirect(request, response, "login");
    }
}
