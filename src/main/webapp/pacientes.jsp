<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos, utils.Validacao, models.Paciente" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Pacientes";
Paciente.Pagina resultado = (Paciente.Pagina) request.getAttribute("resultado");
if (resultado == null) { response.sendRedirect(utils.Url.to(request, "pacientes")); return; }
String q = request.getAttribute("q") != null ? String.valueOf(request.getAttribute("q")) : "";
boolean inativos = Boolean.TRUE.equals(request.getAttribute("inativos"));
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%
boolean podeCriar = layoutPermissoes.contains("pacientes.create");
boolean podeAgendar = layoutPermissoes.contains("consultas.agendar");
String qs = "q=" + java.net.URLEncoder.encode(q, "UTF-8") + (inativos ? "&inativos=1" : "");
java.time.LocalDate hojeLista = Paciente.hoje();
%>
<div class="hc-title-row">
    <div>
        <h1>Pacientes</h1>
        <p class="hc-subtitle"><%= resultado.total() %> <%= resultado.total() == 1 ? "paciente" : "pacientes" %><%= q.isEmpty() ? "" : " encontrados para \"" + Sanitize.escapeHtml(q) + "\"" %><%= inativos ? " (incluindo inativos)" : "" %></p>
    </div>
    <% if (podeCriar) { %>
    <div class="hc-actions">
        <a class="hc-btn hc-btn-primary" href="pacientes/novo"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-user-plus"/></svg>Cadastrar paciente</a>
    </div>
    <% } %>
</div>

<form class="hc-toolbar" method="GET" action="pacientes" role="search">
    <div class="d-flex gap-2 align-items-center flex-wrap">
        <label class="visually-hidden-hc" for="buscaLista">Nome ou CPF</label>
        <input id="buscaLista" class="hc-input" style="width:320px;height:36px" type="search" name="q" value="<%= Sanitize.escapeHtml(q) %>" placeholder="Nome ou CPF">
        <label class="d-flex align-items-center gap-2 muted"><input type="checkbox" name="inativos" value="1" <%= inativos ? "checked" : "" %>> Incluir inativos</label>
        <button class="hc-btn" type="submit">Buscar</button>
        <% if (!q.isEmpty() || inativos) { %><a class="hc-btn hc-btn-text" href="pacientes">Limpar</a><% } %>
    </div>
    <span class="hc-count">Página <%= resultado.pagina() %> de <%= resultado.totalPaginas() %></span>
</form>

<% if (resultado.itens().isEmpty()) { %>
    <p class="hc-empty"><%= q.isEmpty() ? "Nenhum paciente cadastrado ainda." : "Nenhum paciente encontrado com esse nome ou CPF." %>
    <% if (podeCriar) { %> <a href="pacientes/novo">Cadastrar paciente</a><% } %></p>
<% } else { %>
<table class="hc-table">
    <thead><tr><th>Paciente</th><th class="num">CPF</th><th class="num hide-sm">Idade</th><th class="hide-sm num">Telefone</th><th class="hide-sm">Situação</th><th><span class="visually-hidden-hc">Ações</span></th></tr></thead>
    <tbody>
    <% for (Map<String, Object> p : resultado.itens()) {
           Object nasc = p.get("data_nascimento");
           java.time.LocalDate dn = nasc instanceof java.time.LocalDate ? (java.time.LocalDate) nasc : ((java.sql.Date) nasc).toLocalDate();
           boolean ativo = ((Number) p.get("ativo")).intValue() == 1; %>
        <tr>
            <td><a class="hc-cell-main" href="pacientes/<%= p.get("id_paciente") %>"><%= Sanitize.escapeHtml(p.get("nome")) %></a>
                <span class="hc-cell-sub">Nasc. <%= Rotulos.data(nasc) %></span></td>
            <td class="num"><%= Validacao.cpfMascarado(String.valueOf(p.get("cpf"))) %></td>
            <td class="num hide-sm"><%= Validacao.idade(dn, hojeLista) %> anos</td>
            <td class="hide-sm num"><%= Rotulos.ou(p.get("telefone")) %></td>
            <td class="hide-sm"><span class="hc-pill <%= ativo ? "positive" : "neutral" %>"><%= ativo ? "Ativo" : "Inativo" %></span></td>
            <td class="num">
                <% if (podeAgendar && ativo) { %><a class="hc-btn hc-btn-sm hc-btn-text" href="agenda/nova?paciente=<%= p.get("id_paciente") %>">Agendar</a><% } %>
                <a class="hc-btn hc-btn-sm" href="pacientes/<%= p.get("id_paciente") %>">Ver ficha</a>
            </td>
        </tr>
    <% } %>
    </tbody>
</table>
<div class="hc-pager">
    <span>Mostrando <%= (resultado.pagina() - 1) * resultado.porPagina() + 1 %>–<%= Math.min(resultado.total(), (long) resultado.pagina() * resultado.porPagina()) %> de <%= resultado.total() %></span>
    <span class="d-flex gap-2">
        <% if (resultado.pagina() > 1) { %><a class="hc-btn hc-btn-sm" href="pacientes?<%= qs %>&pagina=<%= resultado.pagina() - 1 %>"><svg class="ic ic-sm" aria-hidden="true"><use href="assets/icons/icons.svg#i-chevron-left"/></svg>Anterior</a><% } %>
        <% if (resultado.pagina() < resultado.totalPaginas()) { %><a class="hc-btn hc-btn-sm" href="pacientes?<%= qs %>&pagina=<%= resultado.pagina() + 1 %>">Próxima<svg class="ic ic-sm" aria-hidden="true"><use href="assets/icons/icons.svg#i-chevron-right"/></svg></a><% } %>
    </span>
</div>
<% } %>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
