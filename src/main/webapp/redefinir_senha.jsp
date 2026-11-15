<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="utils.Router, utils.Url, utils.Sanitize, utils.PasswordSecurity" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String baseHref = Url.base(request);
boolean tokenValido = Boolean.TRUE.equals(request.getAttribute("tokenValido"));
String token = request.getAttribute("token") != null ? String.valueOf(request.getAttribute("token")) : "";
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <%= Csrf.metaTag(request) %>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="referrer" content="no-referrer">
    <title>Redefinir Senha</title>
    <base href="<%= Sanitize.escapeHtml(baseHref) %>">
    <link rel="stylesheet" href="assets/css/bootstrap.min.css">
</head>
<body class="bg-light d-flex align-items-center min-vh-100">
<main class="container" style="max-width: 420px;">
    <div class="card shadow-sm border-0">
        <div class="card-body p-4">
            <h1 class="h4 fw-bold mb-3 text-center">Redefinir senha</h1>
            <div id="alerta" class="alert d-none" role="alert"></div>

            <% if (tokenValido) { %>
            <form id="formRedefinir" action="redefinir-senha" method="POST" novalidate>
                <input type="hidden" name="token" value="<%= Sanitize.escapeHtml(token) %>">
                <div class="mb-3">
                    <label for="nova_senha" class="form-label fw-semibold">Nova senha</label>
                    <input type="password" class="form-control" id="nova_senha" name="nova_senha"
                           minlength="<%= PasswordSecurity.MIN_LENGTH %>" autocomplete="new-password" required>
                    <div class="form-text">Mínimo de <%= PasswordSecurity.MIN_LENGTH %> caracteres, com letras e números.</div>
                </div>
                <div class="mb-3">
                    <label for="confirmar_nova_senha" class="form-label fw-semibold">Confirmar nova senha</label>
                    <input type="password" class="form-control" id="confirmar_nova_senha" name="confirmar_nova_senha"
                           autocomplete="new-password" required>
                </div>
                <button type="submit" class="btn btn-primary w-100 fw-semibold" id="btnRedefinir">Salvar nova senha</button>
            </form>
            <% } else { %>
            <div class="alert alert-warning mb-3">
                Este link de recuperação é inválido, já foi usado ou expirou.
            </div>
            <a class="btn btn-outline-primary w-100" href="./">Solicitar um novo link</a>
            <% } %>
            <a class="d-block text-center mt-3 small" href="login">Voltar para o login</a>
        </div>
    </div>
</main>

<script src="assets/js/jquery.min.js"></script>
<script src="assets/js/zait.ajax.js"></script>
<script>
$(function () {
    const $alerta = $('#alerta');

    function mostrar(msg, sucesso) {
        // .text() evita interpretar a mensagem como HTML (protecao contra XSS)
        $alerta.removeClass('d-none alert-danger alert-success')
               .addClass(sucesso ? 'alert-success' : 'alert-danger')
               .text(msg);
    }

    $('#formRedefinir').on('submit', function (e) {
        e.preventDefault();
        const senha = $('#nova_senha').val();
        if (senha !== $('#confirmar_nova_senha').val()) {
            mostrar('A confirmação da nova senha não confere.', false);
            return;
        }
        const $btn = $('#btnRedefinir').prop('disabled', true);
        ZTF.ajax.submitForm(this)
            .then(function (res) {
                mostrar(res.message, true);
                $('#formRedefinir').addClass('d-none');
                setTimeout(function () { window.location.href = 'login'; }, 2500);
            })
            .catch(function (err) {
                mostrar(err.message || 'Não foi possível redefinir a senha.', false);
                $btn.prop('disabled', false);
            });
    });
});
</script>
</body>
</html>
