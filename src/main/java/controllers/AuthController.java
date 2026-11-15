package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import mail.Mail;
import models.RecuperacaoSenha;
import models.Usuario;
import utils.Config;
import utils.JsonResponse;
import utils.PasswordSecurity;
import utils.RateLimiter;
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
    urlPatterns = {"/login", "/cadastro", "/recuperar-senha", "/redefinir-senha", "/logout"}
)
public class AuthController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(AuthController.class.getName());

    private static final long QUINZE_MINUTOS = 15L * 60 * 1000;
    private static final RateLimiter LIMITE_RECUPERACAO_IP = new RateLimiter(10, QUINZE_MINUTOS);
    private static final RateLimiter LIMITE_RECUPERACAO_EMAIL = new RateLimiter(3, QUINZE_MINUTOS);
    private static final RateLimiter LIMITE_REDEFINICAO_IP = new RateLimiter(10, QUINZE_MINUTOS);

    private static final String MSG_RECUPERACAO_GENERICA =
            "Se o e-mail estiver cadastrado, você receberá em instantes um link para redefinir a senha.";

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

        // Evita Session Fixation: descarta a sessao anonima anterior e cria um novo ID.
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
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
        usuarioInfo.put("id", dadosUsuario.get("id_usuario"));
        usuarioInfo.put("nome", dadosUsuario.get("nome"));
        usuarioInfo.put("email", dadosUsuario.get("email"));
        usuarioInfo.put("grupos", grupos);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usuario", usuarioInfo);
        data.put("redirect", redirect);

        JsonResponse.success(response, data, "Autenticado com sucesso!");
    }

    public void registerAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Sistema hospitalar: contas sao criadas pelo administrador. O auto-cadastro do
        // exemplo original so funciona com security.public_registration = true.
        if (!Config.getBoolean("security.public_registration", false)) {
            JsonResponse.error(response, 404, "ERR_REGISTRATION_DISABLED", "Cadastro público desativado. Procure o administrador.");
            return;
        }
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

    /**
     * POST /recuperar-senha
     * Envia por e-mail um link com token de uso unico. A senha atual nao muda aqui.
     * A resposta e sempre a mesma, exista ou nao o e-mail (evita enumeracao de usuarios),
     * e o numero de pedidos e limitado por IP e por e-mail.
     */
    public void recuperarSenha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String email = Sanitize.email(RequestHelper.getParam(request, "email", "")).toLowerCase();
        if (email.isEmpty() || !Sanitize.isValidEmail(email)) {
            JsonResponse.error(response, 400, "ERR_INVALID_EMAIL", "Informe um endereço de e-mail válido.");
            return;
        }

        String ip = request.getRemoteAddr();
        if (!LIMITE_RECUPERACAO_IP.tryAcquire(ip) || !LIMITE_RECUPERACAO_EMAIL.tryAcquire(email)) {
            JsonResponse.error(response, 429, "ERR_TOO_MANY_REQUESTS",
                    "Muitas solicitações. Aguarde alguns minutos e tente novamente.");
            return;
        }

        try {
            RecuperacaoSenha.Solicitacao pedido = new RecuperacaoSenha().solicitar(email, ip);
            if (pedido != null) {
                enviarEmailRecuperacao(request, pedido);
            }
        } catch (Exception e) {
            // Falha interna nao altera a resposta: o cliente nao deve distinguir os casos.
            LOG.log(Level.WARNING, "Falha ao processar recuperacao de senha", e);
        }

        JsonResponse.success(response, null, MSG_RECUPERACAO_GENERICA);
    }

    /** GET /redefinir-senha?token=... — exibe o formulario de nova senha. */
    public void redefinirSenhaPage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = RequestHelper.getParam(request, "token", "");
        boolean valido;
        try {
            valido = new RecuperacaoSenha().tokenValido(token);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha ao validar token de recuperacao", e);
            valido = false;
        }
        // O token esta na URL: nao deixar vazar via Referer nem ficar em cache.
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute("tokenValido", valido);
        request.setAttribute("token", valido ? token : "");
        renderView(request, response, "redefinir_senha.jsp");
    }

    /** POST /redefinir-senha — consome o token e grava a nova senha. */
    public void redefinirSenha(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = RequestHelper.getParam(request, "token", "");
        String novaSenha = RequestHelper.getParam(request, "nova_senha", "");
        String confirmacao = RequestHelper.getParam(request, "confirmar_nova_senha", "");

        if (!LIMITE_REDEFINICAO_IP.tryAcquire(request.getRemoteAddr())) {
            JsonResponse.error(response, 429, "ERR_TOO_MANY_REQUESTS",
                    "Muitas tentativas. Aguarde alguns minutos e tente novamente.");
            return;
        }
        String erroSenha = PasswordSecurity.strengthError(novaSenha);
        if (erroSenha != null) {
            JsonResponse.error(response, 400, "ERR_WEAK_PASSWORD", erroSenha);
            return;
        }
        if (!novaSenha.equals(confirmacao)) {
            JsonResponse.error(response, 400, "ERR_PASSWORD_MISMATCH", "A confirmação da nova senha não confere.");
            return;
        }

        boolean redefinida;
        try {
            redefinida = new RecuperacaoSenha().redefinir(token, novaSenha);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Falha ao redefinir senha", e);
            JsonResponse.error(response, 500, "ERR_SERVER_ERROR", "Não foi possível redefinir a senha agora. Tente novamente.");
            return;
        }
        if (!redefinida) {
            JsonResponse.error(response, 400, "ERR_INVALID_TOKEN", "Link de recuperação inválido ou expirado. Solicite um novo.");
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redirect", "login");
        JsonResponse.success(response, data, "Senha redefinida com sucesso! Faça login com a nova senha.");
    }

    private void enviarEmailRecuperacao(HttpServletRequest request, RecuperacaoSenha.Solicitacao pedido) {
        String link = linkRedefinicao(request, pedido.token());
        if (link == null) {
            LOG.severe("app.url nao configurado em producao: e-mail de recuperacao nao enviado.");
            return;
        }
        String corpoEmail = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e1e4e8; border-radius: 8px;'>"
                + "<h2 style='color: #0366d6;'>Recuperação de Senha</h2>"
                + "<p>Olá, <strong>" + Sanitize.escapeHtml(pedido.nome()) + "</strong>.</p>"
                + "<p>Recebemos uma solicitação para redefinir a sua senha de acesso.</p>"
                + "<p><a href='" + Sanitize.escapeHtml(link) + "' style='display:inline-block;padding:12px 20px;background:#0d6efd;color:#fff;border-radius:6px;text-decoration:none;'>Definir nova senha</a></p>"
                + "<p style='font-size: 13px;'>Ou copie o endereço: " + Sanitize.escapeHtml(link) + "</p>"
                + "<p style='color: #586069; font-size: 13px;'>O link vale por " + RecuperacaoSenha.ttlMinutes()
                + " minutos e só pode ser usado uma vez.</p>"
                + "<small style='color: #8c959f;'>Se você não solicitou esta alteração, ignore este e-mail: sua senha atual continua válida.</small>"
                + "</div>";
        try {
            Mail.quickSend(pedido.email(), "Recuperação de Senha", corpoEmail);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha no envio do e-mail de recuperacao", e);
        }
    }

    /**
     * Monta o link absoluto de redefinicao. Usa app.url do config.json quando definido; o
     * cabecalho Host da requisicao so e aceito fora de producao, pois ele e controlado pelo
     * cliente (um atacante poderia fazer o e-mail apontar para o dominio dele).
     */
    private String linkRedefinicao(HttpServletRequest request, String token) {
        String path = "redefinir-senha?token=" + token;
        String appUrl = Config.getString("app.url", "").trim();
        if (!appUrl.isEmpty()) {
            return appUrl.replaceAll("/+$", "") + "/" + path;
        }
        if ("production".equalsIgnoreCase(Config.getString("app.env", "development"))) {
            return null;
        }
        return Url.full(request, path);
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
