<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="utils.Router, utils.Sanitize, models.Profissional" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Painel";
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%
Map<String, Object> profissional = null;
int idUsuarioPainel = utils.RequestHelper.getSessionUserId(request);
if (idUsuarioPainel > 0) {
    profissional = new Profissional().porUsuario(idUsuarioPainel);
}
%>
<h1 class="h4 fw-bold mb-3">Olá, <%= Sanitize.escapeHtml(layoutNome) %></h1>

<div class="row g-3">
    <div class="col-md-5">
        <div class="card border-0 shadow-sm h-100">
            <div class="card-body">
                <h2 class="h6 text-uppercase text-muted fw-bold">Seu perfil</h2>
                <p class="mb-1"><strong>Perfil:</strong> <%= Sanitize.escapeHtml(Menu.perfilPrincipal(layoutGrupos)) %></p>
                <% if (profissional != null) { %>
                <p class="mb-1"><strong>Profissional:</strong> <%= Sanitize.escapeHtml(profissional.get("nome")) %></p>
                <% if (profissional.get("registro") != null) { %>
                <p class="mb-1"><strong>Registro:</strong> <%= Sanitize.escapeHtml(profissional.get("registro")) %></p>
                <% } %>
                <% if (profissional.get("cargo") != null) { %>
                <p class="mb-1"><strong>Cargo:</strong> <%= Sanitize.escapeHtml(profissional.get("cargo")) %></p>
                <% } %>
                <% } else { %>
                <p class="text-muted small mb-0">Seu usuário ainda não está vinculado a um cadastro de profissional.</p>
                <% } %>
            </div>
        </div>
    </div>
    <div class="col-md-7">
        <div class="card border-0 shadow-sm h-100">
            <div class="card-body">
                <h2 class="h6 text-uppercase text-muted fw-bold">Indicadores do perfil</h2>
                <p class="text-muted small mb-0">
                    Os indicadores do seu perfil (agenda do dia, fila de triagem, atendimentos) serão exibidos
                    aqui quando os módulos de atendimento forem liberados. Os itens marcados como
                    "em breve" no menu mostram o que está previsto para o seu perfil.
                </p>
            </div>
        </div>
    </div>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
