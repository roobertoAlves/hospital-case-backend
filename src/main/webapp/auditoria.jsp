<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos, models.ConsultaAuditoria" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String aba = String.valueOf(request.getAttribute("aba"));
if (!"auditoria".equals(aba) && !"notificacoes".equals(aba)) { response.sendRedirect(utils.Url.to(request, "auditoria")); return; }
boolean abaNotif = "notificacoes".equals(aba);
String tituloPagina = "Auditoria";
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    private static String sel(Object atual, Object valor) { return String.valueOf(valor).equals(String.valueOf(atual)) ? " selected" : ""; }
    private static String statusNotif(Object s) {
        String v = String.valueOf(s);
        if ("enviada".equals(v)) return "<span class=\"hc-pill positive\">Enviada</span>";
        if ("simulada".equals(v)) return "<span class=\"hc-pill accent\">Simulada</span>";
        if ("falhou".equals(v)) return "<span class=\"hc-pill negative\">Falhou</span>";
        return "<span class=\"hc-pill neutral\">Pendente</span>";
    }
%>
<div class="hc-title-row">
    <div>
        <h1>Auditoria</h1>
        <p class="hc-subtitle">Registro somente leitura de acessos e alterações. Não guarda conteúdo clínico, senhas nem tokens. Esta consulta também fica registrada.</p>
    </div>
</div>

<nav class="hc-tabs" aria-label="Seções da auditoria">
    <a class="hc-tab" href="auditoria" <%= abaNotif ? "" : "aria-current=\"page\"" %>>Trilha de auditoria</a>
    <a class="hc-tab" href="auditoria?aba=notificacoes" <%= abaNotif ? "aria-current=\"page\"" : "" %>>E-mails enviados</a>
</nav>

<% if (abaNotif) {
       @SuppressWarnings("unchecked")
       List<Map<String, Object>> notif = (List<Map<String, Object>>) request.getAttribute("notificacoes"); %>
    <p class="hc-help">Últimos 100 avisos. O texto do e-mail não é guardado (pode ter senha temporária ou link de recuperação); em desenvolvimento, o conteúdo simulado fica em <span class="hc-code">WEB-INF/storage/logs/mail.log</span>.</p>
    <% if (notif.isEmpty()) { %><p class="hc-empty">Nenhum e-mail enviado ainda.</p><% } else { %>
    <table class="hc-table">
        <thead><tr><th style="width:150px">Quando</th><th>Assunto</th><th class="hide-sm">Destinatário</th><th class="hide-sm">Disparado por</th><th style="width:110px">Situação</th></tr></thead>
        <tbody>
        <% for (Map<String, Object> n : notif) { %>
            <tr>
                <td class="num"><%= Rotulos.dataHora(n.get("criado_em")) %></td>
                <td><span class="hc-cell-main"><%= Sanitize.escapeHtml(n.get("assunto")) %></span><span class="hc-cell-sub"><span class="hc-code"><%= Sanitize.escapeHtml(n.get("tipo")) %></span></span>
                    <% if (n.get("erro") != null) { %><span class="hc-cell-sub text-danger"><%= Sanitize.escapeHtml(n.get("erro")) %></span><% } %></td>
                <td class="hide-sm"><%= Sanitize.escapeHtml(ConsultaAuditoria.emailMascarado(n.get("destinatario"))) %></td>
                <td class="hide-sm"><%= Rotulos.ou(n.get("usuario")) %></td>
                <td><%= statusNotif(n.get("status")) %></td>
            </tr>
        <% } %>
        </tbody>
    </table>
    <% } %>
<% } else {
       ConsultaAuditoria.Filtro f = (ConsultaAuditoria.Filtro) request.getAttribute("filtro");
       ConsultaAuditoria.Pagina r = (ConsultaAuditoria.Pagina) request.getAttribute("resultado");
       @SuppressWarnings("unchecked")
       List<String> modulos = (List<String>) request.getAttribute("modulos");
       @SuppressWarnings("unchecked")
       List<Map<String, Object>> usuarios = (List<Map<String, Object>>) request.getAttribute("usuarios");
       String qs = "de=" + f.de() + "&ate=" + f.ate() + (f.idUsuario() != null ? "&usuario=" + f.idUsuario() : "")
               + (f.modulo().isEmpty() ? "" : "&modulo=" + f.modulo()) + (f.idPaciente() != null ? "&paciente=" + f.idPaciente() : ""); %>
    <form class="hc-filters" method="GET" action="auditoria">
        <div class="hc-field"><label for="fDe">De</label><input class="hc-input" type="date" id="fDe" name="de" value="<%= f.de() %>"></div>
        <div class="hc-field"><label for="fAte">Até</label><input class="hc-input" type="date" id="fAte" name="ate" value="<%= f.ate() %>"></div>
        <div class="hc-field"><label for="fUsu">Usuário</label>
            <select class="hc-select" id="fUsu" name="usuario"><option value="">Todos</option>
            <% for (Map<String, Object> u : usuarios) { %><option value="<%= u.get("id_usuario") %>"<%= sel(f.idUsuario(), u.get("id_usuario")) %>><%= Sanitize.escapeHtml(u.get("nome")) %></option><% } %>
            </select></div>
        <div class="hc-field"><label for="fMod">Módulo</label>
            <select class="hc-select" id="fMod" name="modulo"><option value="">Todos</option>
            <% for (String m : modulos) { %><option value="<%= Sanitize.escapeHtml(m) %>"<%= sel(f.modulo(), m) %>><%= Sanitize.escapeHtml(m) %></option><% } %>
            </select></div>
        <div class="hc-field"><label for="fPac">Nº do paciente</label><input class="hc-input num" id="fPac" name="paciente" inputmode="numeric" value="<%= f.idPaciente() != null ? f.idPaciente() : "" %>" placeholder="Ex.: 7"></div>
        <div class="d-flex gap-2"><button class="hc-btn hc-btn-primary" type="submit">Filtrar</button><a class="hc-btn hc-btn-text" href="auditoria">Limpar</a></div>
    </form>

    <div class="hc-toolbar"><span class="muted"><%= r.total() %> <%= r.total() == 1 ? "registro" : "registros" %> de <%= Rotulos.data(f.de()) %> a <%= Rotulos.data(f.ate()) %></span>
        <span class="hc-count">Página <%= r.pagina() %> de <%= r.totalPaginas() %></span></div>
    <% if (r.itens().isEmpty()) { %><p class="hc-empty">Nenhum registro com esse filtro.</p><% } else { %>
    <table class="hc-table">
        <thead><tr><th style="width:160px">Quando</th><th>Ação</th><th>Usuário</th><th class="hide-sm">Paciente</th><th class="hide-sm">Detalhes</th></tr></thead>
        <tbody>
        <% for (Map<String, Object> a : r.itens()) { %>
            <tr>
                <td class="num"><%= Rotulos.dataHora(a.get("registrado_em")) %></td>
                <td><span class="hc-code"><%= Sanitize.escapeHtml(a.get("acao")) %></span>
                    <% if (a.get("entidade") != null) { %><span class="hc-cell-sub"><%= Sanitize.escapeHtml(a.get("entidade")) %><%= a.get("id_entidade") != null ? " nº " + a.get("id_entidade") : "" %></span><% } %></td>
                <td><span class="hc-cell-main"><%= Rotulos.ou(a.get("usuario")) %></span><% if (a.get("ip") != null) { %><span class="hc-cell-sub">IP <%= Sanitize.escapeHtml(a.get("ip")) %></span><% } %></td>
                <td class="hide-sm"><% if (a.get("id_paciente") != null) { %><a href="auditoria?<%= "de=" + f.de() + "&ate=" + f.ate() %>&paciente=<%= a.get("id_paciente") %>"><%= Rotulos.ou(a.get("paciente")) %></a><span class="hc-cell-sub">nº <%= a.get("id_paciente") %></span><% } else { %><span class="muted">—</span><% } %></td>
                <td class="hide-sm"><%= Rotulos.ou(a.get("detalhes")) %></td>
            </tr>
        <% } %>
        </tbody>
    </table>
    <div class="hc-pager">
        <span>Mostrando <%= (r.pagina() - 1) * ConsultaAuditoria.POR_PAGINA + 1 %>–<%= Math.min(r.total(), (long) r.pagina() * ConsultaAuditoria.POR_PAGINA) %> de <%= r.total() %></span>
        <span class="d-flex gap-2">
            <% if (r.pagina() > 1) { %><a class="hc-btn hc-btn-sm" href="auditoria?<%= qs %>&pagina=<%= r.pagina() - 1 %>">Anterior</a><% } %>
            <% if (r.pagina() < r.totalPaginas()) { %><a class="hc-btn hc-btn-sm" href="auditoria?<%= qs %>&pagina=<%= r.pagina() + 1 %>">Próxima</a><% } %>
        </span>
    </div>
    <% } %>
<% } %>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
