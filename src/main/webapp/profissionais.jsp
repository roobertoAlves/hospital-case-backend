<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Profissionais";
@SuppressWarnings("unchecked")
List<Map<String, Object>> lista = (List<Map<String, Object>>) request.getAttribute("profissionais");
if (lista == null) { response.sendRedirect(utils.Url.to(request, "profissionais")); return; }
String q = String.valueOf(request.getAttribute("q"));
String tipoF = String.valueOf(request.getAttribute("tipo"));
boolean inativos = Boolean.TRUE.equals(request.getAttribute("inativos"));
int medicos = 0, enfermeiros = 0, funcionarios = 0;
for (Map<String, Object> p : lista) {
    String t = String.valueOf(p.get("tipo"));
    if ("medico".equals(t)) medicos++; else if ("enfermeiro".equals(t)) enfermeiros++; else funcionarios++;
}
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    private static String rotuloTipo(Object t) {
        String s = String.valueOf(t);
        return "medico".equals(s) ? "Médico" : ("enfermeiro".equals(s) ? "Enfermeiro" : "Funcionário");
    }
    private static String sel(String atual, String valor) { return valor.equals(atual) ? " selected" : ""; }
%>
<div class="hc-title-row">
    <div>
        <h1>Profissionais</h1>
        <p class="hc-subtitle"><%= medicos %> <%= medicos == 1 ? "médico" : "médicos" %> · <%= enfermeiros %> <%= enfermeiros == 1 ? "enfermeiro" : "enfermeiros" %> · <%= funcionarios %> <%= funcionarios == 1 ? "funcionário" : "funcionários" %><%= inativos ? " (incluindo inativos)" : "" %></p>
    </div>
    <div class="hc-actions">
        <a class="hc-btn" href="especialidades"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-list"/></svg>Especialidades</a>
        <a class="hc-btn hc-btn-primary" href="profissionais/novo"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-plus"/></svg>Cadastrar profissional</a>
    </div>
</div>

<form class="hc-toolbar" method="GET" action="profissionais" role="search">
    <div class="d-flex gap-2 align-items-center flex-wrap">
        <label class="visually-hidden-hc" for="buscaProf">Nome, CRM ou COREN</label>
        <input id="buscaProf" class="hc-input" style="width:280px;height:36px" type="search" name="q" value="<%= Sanitize.escapeHtml(q) %>" placeholder="Nome, CRM ou COREN">
        <label class="visually-hidden-hc" for="tipoProf">Tipo</label>
        <select id="tipoProf" class="hc-select" style="width:170px;height:36px" name="tipo">
            <option value="">Todos os tipos</option>
            <option value="medico"<%= sel(tipoF, "medico") %>>Médicos</option>
            <option value="enfermeiro"<%= sel(tipoF, "enfermeiro") %>>Enfermeiros</option>
            <option value="funcionario"<%= sel(tipoF, "funcionario") %>>Funcionários</option>
        </select>
        <label class="d-flex align-items-center gap-2 muted"><input type="checkbox" name="inativos" value="1" <%= inativos ? "checked" : "" %>> Incluir inativos</label>
        <button class="hc-btn" type="submit">Filtrar</button>
        <% if (!q.isEmpty() || !tipoF.isEmpty() || inativos) { %><a class="hc-btn hc-btn-text" href="profissionais">Limpar</a><% } %>
    </div>
    <span class="hc-count"><%= lista.size() %> <%= lista.size() == 1 ? "resultado" : "resultados" %></span>
</form>

<% if (lista.isEmpty()) { %>
    <p class="hc-empty"><%= q.isEmpty() && tipoF.isEmpty() ? "Nenhum profissional cadastrado." : "Nenhum profissional com esse filtro." %> <a href="profissionais/novo">Cadastrar profissional</a></p>
<% } else { %>
<table class="hc-table">
    <thead><tr><th>Profissional</th><th style="width:130px">Tipo</th><th class="num" style="width:150px">Registro</th><th class="hide-sm">Acesso ao sistema</th><th class="hide-sm" style="width:100px">Situação</th><th style="width:90px"><span class="visually-hidden-hc">Ações</span></th></tr></thead>
    <tbody>
    <% for (Map<String, Object> p : lista) {
           boolean ativo = ((Number) p.get("ativo")).intValue() == 1;
           String t = String.valueOf(p.get("tipo"));
           String registro = p.get("crm") != null ? "CRM " + p.get("crm") + "/" + p.get("crm_uf")
                   : (p.get("coren") != null ? "COREN " + p.get("coren") + "/" + p.get("coren_uf") : "—");
           String sub = "medico".equals(t) ? String.valueOf(p.get("especialidades") != null ? p.get("especialidades") : "sem especialidade")
                   : ("funcionario".equals(t) ? String.valueOf(p.get("cargo") != null ? p.get("cargo") : "") : ""); %>
        <tr>
            <td><a class="hc-cell-main" href="profissionais/<%= p.get("id_profissional") %>"><%= Sanitize.escapeHtml(p.get("nome")) %></a>
                <% if (!sub.isEmpty()) { %><span class="hc-cell-sub"><%= Sanitize.escapeHtml(sub) %></span><% } %></td>
            <td><%= rotuloTipo(t) %></td>
            <td class="num"><%= Sanitize.escapeHtml(registro) %></td>
            <td class="hide-sm"><% if (p.get("usuario_email") != null) { %><%= Sanitize.escapeHtml(p.get("usuario_email")) %><%= "0".equals(String.valueOf(p.get("usuario_status"))) ? " <span class=\"hc-pill neutral\">usuário inativo</span>" : "" %><% } else { %><span class="muted">sem acesso</span><% } %></td>
            <td class="hide-sm"><span class="hc-pill <%= ativo ? "positive" : "neutral" %>"><%= ativo ? "Ativo" : "Inativo" %></span></td>
            <td class="num"><a class="hc-btn hc-btn-sm" href="profissionais/<%= p.get("id_profissional") %>">Abrir</a></td>
        </tr>
    <% } %>
    </tbody>
</table>
<% } %>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
