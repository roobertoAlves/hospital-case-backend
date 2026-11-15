<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.Map, utils.Router, utils.Url, utils.Sanitize, utils.RequestHelper, utils.PasswordSecurity" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String baseHref = Url.base(request);
Map<String, Object> usuario = RequestHelper.getSessionUser(request);
boolean primeiroAcesso = usuario != null && Sanitize.intVal(usuario.get("primeiro_acesso"), 0) == 1;
String nome = usuario != null ? String.valueOf(usuario.get("nome")) : "";
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <%= Csrf.metaTag(request) %>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Trocar Senha</title>
    <base href="<%= Sanitize.escapeHtml(baseHref) %>">
    <link rel="stylesheet" href="assets/css/bootstrap.min.css">
</head>
<body class="bg-light d-flex align-items-center min-vh-100">
<main class="container" style="max-width: 440px;">
    <div class="card shadow-sm border-0">
        <div class="card-body p-4">
            <h1 class="h4 fw-bold mb-1 text-center">Trocar senha</h1>
            <p class="text-muted small text-center mb-3"><%= Sanitize.escapeHtml(nome) %></p>
            <% if (primeiroAcesso) { %>
            <div class="alert alert-warning small">
                Você está usando uma senha temporária. Defina uma senha pessoal para continuar.
            </div>
            <% } %>
            <div id="alerta" class="alert d-none" role="alert"></div>

            <form id="formTrocarSenha" action="trocar-senha" method="POST" novalidate>
                <div class="mb-3">
                    <label for="senha_atual" class="form-label fw-semibold">Senha atual</label>
                    <input type="password" class="form-control" id="senha_atual" name="senha_atual"
                           autocomplete="current-password" required>
                </div>
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
                <button type="submit" class="btn btn-primary w-100 fw-semibold" id="btnTrocar">Salvar nova senha</button>
            </form>
            <a class="d-block text-center mt-3 small" href="logout">Sair</a>
        </div>
    </div>
</main>

<script src="assets/js/jquery.min.js"></script>
<script src="assets/js/zait.ajax.js"></script>
<script>
$(function () {
    const $alerta = $('#alerta');

    function mostrar(msg, sucesso) {
        $alerta.removeClass('d-none alert-danger alert-success')
               .addClass(sucesso ? 'alert-success' : 'alert-danger')
               .text(msg);
    }

    $('#formTrocarSenha').on('submit', function (e) {
        e.preventDefault();
        if ($('#nova_senha').val() !== $('#confirmar_nova_senha').val()) {
            mostrar('A confirmação da nova senha não confere.', false);
            return;
        }
        const $btn = $('#btnTrocar').prop('disabled', true);
        ZTF.ajax.submitForm(this)
            .then(function (res) {
                mostrar(res.message, true);
                setTimeout(function () { window.location.href = (res.data && res.data.redirect) || 'dashboard'; }, 1200);
            })
            .catch(function (err) {
                mostrar(err.message || 'Não foi possível trocar a senha.', false);
                $btn.prop('disabled', false);
            });
    });
});
</script>
</body>
</html>
