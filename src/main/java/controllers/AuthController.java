package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import mail.Mail;
import models.AuditLog;
import models.LoginSecurity;
import models.PasswordRecovery;
import models.User;
import utils.Config;
import utils.Csrf;
import utils.JsonResponse;
import utils.PasswordSecurity;
import utils.RateLimiter;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "AuthController", urlPatterns = {"/api/auth/*"})
public class AuthController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(AuthController.class.getName());

    private static final long FIFTEEN_MINUTES = 15L * 60 * 1000;
    private static final RateLimiter RECOVERY_LIMIT_BY_IP = new RateLimiter(10, FIFTEEN_MINUTES);
    private static final RateLimiter RECOVERY_LIMIT_BY_EMAIL = new RateLimiter(3, FIFTEEN_MINUTES);
    private static final RateLimiter RESET_LIMIT_BY_IP = new RateLimiter(10, FIFTEEN_MINUTES);
    private static final RateLimiter LOGIN_LIMIT_BY_IP = new RateLimiter(30, FIFTEEN_MINUTES);

    private static final String MSG_INVALID_LOGIN =
            "Credenciais inválidas ou acesso temporariamente bloqueado. Verifique os dados ou tente novamente em alguns minutos.";

    private static final String MSG_GENERIC_RECOVERY =
            "Se o e-mail estiver cadastrado, você receberá em instantes um link para redefinir a senha.";

    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = RequestHelper.getParam(request, "email", "").trim();
        String password = RequestHelper.getParam(request, "password", "");

        if (email.isEmpty() || password.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_FIELDS", "E-mail e senha são obrigatórios.");
            return;
        }

        String ip = request.getRemoteAddr();
        if (!LOGIN_LIMIT_BY_IP.tryAcquire(ip)) {
            JsonResponse.error(response, 429, "ERR_TOO_MANY_REQUESTS",
                    "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
            return;
        }

        User.LoginResult result = new User().authenticateDetailed(email, password);
        if (result.status() != User.LoginStatus.SUCCESS) {
            Integer id = result.userId();
            if (result.status() == User.LoginStatus.BLOCKED) {
                AuditLog.record(id, "login.bloqueado", "usuarios", id, null, null, ip);
            } else if (id != null) {
                boolean locked = LoginSecurity.recordFailure(id);
                AuditLog.record(id, locked ? "login.bloqueio_aplicado" : "login.falha", "usuarios", id, null, null, ip);
            } else {
                AuditLog.record(null, "login.falha", "usuarios", null, null, "e-mail nao cadastrado", ip);
            }
            JsonResponse.error(response, 401, "ERR_INVALID_CREDENTIALS", MSG_INVALID_LOGIN);
            return;
        }
        LoginSecurity.recordSuccess(result.userId());

        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("user", result.session());
        session.setMaxInactiveInterval(sessionTimeoutSeconds());
        Csrf.rotate(session);
        AuditLog.record(result.userId(), "login.sucesso", "usuarios", result.userId(), null, null, ip);

        JsonResponse.success(response, sessionPayload(request), "Autenticado com sucesso!");
    }

    public void register(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!Config.getBoolean("security.public_registration", false)) {
            JsonResponse.error(response, 404, "ERR_REGISTRATION_DISABLED", "Cadastro público desativado. Procure o administrador.");
            return;
        }
        String name = Sanitize.string(RequestHelper.getParam(request, "nome", ""));
        String email = Sanitize.email(RequestHelper.getParam(request, "email", ""));

        if (name.isEmpty() || email.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_FIELDS", "Nome e e-mail são obrigatórios.");
            return;
        }
        if (!Sanitize.isValidEmail(email)) {
            JsonResponse.error(response, 400, "ERR_INVALID_EMAIL", "Formato de e-mail inválido.");
            return;
        }

        Map<String, Object> result = new User().registerWithAutomaticPassword(name, email);
        if (!Boolean.TRUE.equals(result.get("status"))) {
            JsonResponse.error(response, 400, "ERR_REGISTER_FAILED", String.valueOf(result.get("message")));
            return;
        }

        String initialPassword = String.valueOf(result.get("initialPassword"));
        String emailBody = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e1e4e8; border-radius: 8px;'>"
                + "<h2 style='color: #0d6efd;'>Bem-vindo!</h2>"
                + "<p>Olá, <strong>" + Sanitize.escapeHtml(name) + "</strong>!</p>"
                + "<p>Sua conta foi criada com sucesso no nível inicial <strong>Usuário</strong>.</p>"
                + "<p>Sua senha inicial de acesso gerada automaticamente pelo sistema é:</p>"
                + "<div style='background: #f8f9fa; padding: 15px; font-size: 20px; font-weight: bold; text-align: center; border-radius: 6px; letter-spacing: 2px; color: #212529; border: 1px solid #dee2e6;'>"
                + initialPassword
                + "</div>"
                + "<p style='color: #6c757d; font-size: 13px; margin-top: 20px;'>"
                + "Após efetuar o primeiro login, você poderá alterar esta senha e personalizar sua foto no seu perfil."
                + "</p></div>";
        try {
            Mail.quickSend(email, "Sua senha inicial de acesso", emailBody);
        } catch (Exception ignored) {
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", email);
        data.put("nome", name);
        data.put("simulated_password", Config.getBoolean("mail.simulate_in_dev", false) ? initialPassword : null);
        JsonResponse.success(response, data, "Cadastro concluído! Sua senha inicial foi gerada e enviada para o seu e-mail.");
    }

    public void forgotPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = Sanitize.email(RequestHelper.getParam(request, "email", "")).toLowerCase();
        if (email.isEmpty() || !Sanitize.isValidEmail(email)) {
            JsonResponse.error(response, 400, "ERR_INVALID_EMAIL", "Informe um endereço de e-mail válido.");
            return;
        }

        String ip = request.getRemoteAddr();
        if (!RECOVERY_LIMIT_BY_IP.tryAcquire(ip) || !RECOVERY_LIMIT_BY_EMAIL.tryAcquire(email)) {
            JsonResponse.error(response, 429, "ERR_TOO_MANY_REQUESTS",
                    "Muitas solicitações. Aguarde alguns minutos e tente novamente.");
            return;
        }

        try {
            PasswordRecovery.ResetRequest resetRequest = new PasswordRecovery().request(email, ip);
            if (resetRequest != null) {
                sendRecoveryEmail(request, resetRequest);
            }
            AuditLog.record(null, "senha.recuperacao_solicitada", "usuarios", null, null,
                    resetRequest != null ? "pedido criado" : "e-mail sem usuario ativo", ip);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Password recovery request failed", e);
        }

        JsonResponse.success(response, null, MSG_GENERIC_RECOVERY);
    }

    public void validateResetToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = RequestHelper.getParam(request, "token", "");
        boolean valid;
        try {
            valid = new PasswordRecovery().validToken(token);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Reset token validation failed", e);
            valid = false;
        }
        response.setHeader("Cache-Control", "no-store");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", valid);
        JsonResponse.success(response, data, null);
    }

    public void resetPassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = RequestHelper.getParam(request, "token", "");
        String newPassword = RequestHelper.getParam(request, "new_password", "");
        String confirmation = RequestHelper.getParam(request, "confirm_password", "");

        if (!RESET_LIMIT_BY_IP.tryAcquire(request.getRemoteAddr())) {
            JsonResponse.error(response, 429, "ERR_TOO_MANY_REQUESTS",
                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
            return;
        }
        String passwordError = PasswordSecurity.strengthError(newPassword);
        if (passwordError != null) {
            JsonResponse.error(response, 400, "ERR_WEAK_PASSWORD", passwordError);
            return;
        }
        if (!newPassword.equals(confirmation)) {
            JsonResponse.error(response, 400, "ERR_PASSWORD_MISMATCH", "A confirmação da nova senha não confere.");
            return;
        }

        int resetUserId;
        try {
            resetUserId = new PasswordRecovery().reset(token, newPassword);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Password reset failed", e);
            JsonResponse.error(response, 500, "ERR_SERVER_ERROR", "Não foi possível redefinir a senha agora. Tente novamente.");
            return;
        }
        if (resetUserId <= 0) {
            JsonResponse.error(response, 400, "ERR_INVALID_TOKEN", "Link de recuperação inválido ou expirado. Solicite um novo.");
            return;
        }
        LoginSecurity.unlock(resetUserId);
        AuditLog.record(resetUserId, "senha.redefinida_por_token", "usuarios", resetUserId, null, null,
                request.getRemoteAddr());
        JsonResponse.success(response, null, "Senha redefinida com sucesso! Faça login com a nova senha.");
    }

    private void sendRecoveryEmail(HttpServletRequest request, PasswordRecovery.ResetRequest resetRequest) {
        String link = resetLink(resetRequest.token());
        if (link == null) {
            LOG.severe("app.frontend_url is not configured in production: recovery e-mail not sent.");
            return;
        }
        String emailBody = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e1e4e8; border-radius: 8px;'>"
                + "<h2 style='color: #0366d6;'>Recuperação de Senha</h2>"
                + "<p>Olá, <strong>" + Sanitize.escapeHtml(resetRequest.name()) + "</strong>.</p>"
                + "<p>Recebemos uma solicitação para redefinir a sua senha de acesso.</p>"
                + "<p><a href='" + Sanitize.escapeHtml(link) + "' style='display:inline-block;padding:12px 20px;background:#0d6efd;color:#fff;border-radius:6px;text-decoration:none;'>Definir nova senha</a></p>"
                + "<p style='font-size: 13px;'>Ou copie o endereço: " + Sanitize.escapeHtml(link) + "</p>"
                + "<p style='color: #586069; font-size: 13px;'>O link vale por " + PasswordRecovery.ttlMinutes()
                + " minutos e só pode ser usado uma vez.</p>"
                + "<small style='color: #8c959f;'>Se você não solicitou esta alteração, ignore este e-mail: sua senha atual continua válida.</small>"
                + "</div>";
        try {
            Mail.quickSend(resetRequest.email(), "Recuperação de Senha", emailBody);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Recovery e-mail could not be sent", e);
        }
    }

    static String resetLink(String token) {
        String path = "redefinir-senha?token=" + token;
        String frontendUrl = Config.getString("app.frontend_url", "").trim();
        if (!frontendUrl.isEmpty()) {
            return frontendUrl.replaceAll("/+$", "") + "/" + path;
        }
        if ("production".equalsIgnoreCase(Config.getString("app.env", "development"))) {
            return null;
        }
        return "http://localhost:5173/" + path;
    }

    public void changePassword(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        String current = RequestHelper.getParam(request, "current_password", "");
        String newPassword = RequestHelper.getParam(request, "new_password", "");
        String confirmation = RequestHelper.getParam(request, "confirm_password", "");

        if (!newPassword.equals(confirmation)) {
            JsonResponse.error(response, 400, "ERR_PASSWORD_MISMATCH", "A confirmação da nova senha não confere.");
            return;
        }
        String error = new User().changePassword(userId, current, newPassword);
        if (error != null) {
            audit(request, "senha.troca_recusada", "usuarios", userId, null, null);
            JsonResponse.error(response, 400, "ERR_PASSWORD_CHANGE", error);
            return;
        }

        request.changeSessionId();
        HttpSession session = request.getSession();
        session.setAttribute("user", new User().loadSession(userId));
        Csrf.rotate(session);
        audit(request, "senha.alterada", "usuarios", userId, null, null);
        JsonResponse.success(response, sessionPayload(request), "Senha alterada com sucesso!");
    }

    private static int sessionTimeoutSeconds() {
        int seconds = Config.getInt("security.sessionLifetime", 1800);
        return seconds > 0 ? seconds : 1800;
    }

    public void session(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonResponse.success(response, sessionPayload(request), null);
    }

    private static Map<String, Object> sessionPayload(HttpServletRequest request) {
        Map<String, Object> user = RequestHelper.getSessionUser(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("csrf", Csrf.token(request));
        if (user == null || user.isEmpty()) {
            data.put("authenticated", false);
            return data;
        }
        List<String> groups = RequestHelper.getSessionUserGroups(request);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", user.get("id_usuario"));
        u.put("nome", user.get("nome"));
        u.put("email", user.get("email"));
        u.put("foto", user.get("foto"));
        u.put("groups", groups);
        u.put("permissions", user.get("permissoes"));
        u.put("role_label", User.roleLabel(groups));
        u.put("primeiro_acesso", Sanitize.intVal(user.get("primeiro_acesso"), 0) == 1);
        data.put("authenticated", true);
        data.put("user", u);
        return data;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int leavingId = RequestHelper.getSessionUserId(request);
        if (leavingId > 0) {
            AuditLog.record(leavingId, "logout", "usuarios", leavingId, null, null, request.getRemoteAddr());
        }
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
        JsonResponse.success(response, null, "Sessão encerrada.");
    }
}
