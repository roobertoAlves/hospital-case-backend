<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Início";
@SuppressWarnings("unchecked")
Map<String, Object> pn = (Map<String, Object>) request.getAttribute("painel");
if (pn == null) { response.sendRedirect(utils.Url.to(request, "painel")); return; }
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        return v instanceof List ? (List<Map<String, Object>>) v : new ArrayList<Map<String, Object>>();
    }
    private static String n(Map<String, Object> m, String chave) {
        Object v = m.get(chave);
        return v == null ? "0" : String.valueOf(v);
    }
%>
<%
boolean recepcao = Boolean.TRUE.equals(pn.get("recepcao"));
boolean enfermagem = Boolean.TRUE.equals(pn.get("enfermagem"));
boolean medico = Boolean.TRUE.equals(pn.get("medico"));
boolean admin = Boolean.TRUE.equals(pn.get("admin"));
java.time.LocalDate hojePn = (java.time.LocalDate) pn.get("hoje");
String resumo;
if (recepcao) {
    resumo = n(pn, "consultasDoDia") + " consultas e " + n(pn, "examesDoDia") + " exames hoje · "
            + n(pn, "aguardandoTriagem") + " pacientes aguardando triagem";
} else if (enfermagem) {
    resumo = n(pn, "aguardandoTriagem") + " pacientes aguardando triagem";
} else if (medico) {
    resumo = lista(pn, "filaMedico").size() + " pacientes na sua fila · " + lista(pn, "minhaAgenda").size() + " consultas hoje";
} else if (admin) {
    resumo = n(pn, "pacientesAtivos") + " pacientes ativos · " + n(pn, "usuariosBloqueados") + " usuários bloqueados agora";
} else {
    resumo = "";
}
%>
<div class="hc-title-row">
    <div>
        <h1>Início</h1>
        <p class="hc-subtitle"><%= Sanitize.escapeHtml(Rotulos.diaPorExtenso(hojePn)) %> · <%= Sanitize.escapeHtml(resumo) %></p>
    </div>
    <div class="hc-actions">
        <% if (recepcao) { %>
        <a class="hc-btn" href="pacientes/novo"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-user-plus"/></svg>Cadastrar paciente</a>
        <a class="hc-btn hc-btn-primary" href="agenda/nova"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-calendar-plus"/></svg>Agendar consulta</a>
        <% } %>
    </div>
</div>

<section aria-labelledby="tAtalhos" class="mb-4">
    <h2 class="hc-section-title" id="tAtalhos">Seus atalhos</h2>
    <div class="hc-shortcuts">
    <% for (Object o : (List<?>) pn.get("atalhos")) { utils.Menu.Item a = (utils.Menu.Item) o; %>
        <a class="hc-shortcut" href="<%= Sanitize.escapeHtml(a.url()) %>">
            <span class="hc-shortcut-tile"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#<%= a.icone() %>"/></svg></span>
            <%= Sanitize.escapeHtml(a.rotulo()) %>
        </a>
    <% } %>
    </div>
</section>

<% if (recepcao) { %>
<div class="hc-kpis mb-4" role="group" aria-label="Indicadores de hoje">
    <div class="hc-kpi is-lead"><div class="hc-kpi-label">Consultas hoje</div><div class="hc-kpi-value"><%= n(pn, "consultasDoDia") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Pacientes que chegaram</div><div class="hc-kpi-value"><%= n(pn, "chegaram") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Aguardando triagem</div><div class="hc-kpi-value"><%= n(pn, "aguardandoTriagem") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Exames hoje</div><div class="hc-kpi-value"><%= n(pn, "examesDoDia") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Cancelamentos hoje</div><div class="hc-kpi-value"><%= n(pn, "canceladas") %></div></div>
</div>
<% } %>

<% if (admin) { %>
<div class="hc-kpis mb-4" role="group" aria-label="Indicadores gerais">
    <div class="hc-kpi"><div class="hc-kpi-label">Pacientes ativos</div><div class="hc-kpi-value"><%= n(pn, "pacientesAtivos") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Profissionais ativos</div><div class="hc-kpi-value"><%= n(pn, "profissionaisAtivos") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Consultas realizadas no mês</div><div class="hc-kpi-value"><%= n(pn, "realizadasNoMes") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Consultas agendadas à frente</div><div class="hc-kpi-value"><%= n(pn, "agendadasAFrente") %></div></div>
    <div class="hc-kpi"><div class="hc-kpi-label">Usuários ativos</div><div class="hc-kpi-value"><%= n(pn, "usuariosAtivos") %>
        <% if (!"0".equals(n(pn, "usuariosBloqueados"))) { %><small> · <%= n(pn, "usuariosBloqueados") %> bloqueados</small><% } %></div></div>
</div>
<% } %>

<div class="hc-grid">
<%-- ---------------------------------------------------------------- linha do dia --%>
<% if (pn.containsKey("linhaMedicos")) {
       List<Map<String, Object>> ldMedicos = lista(pn, "linhaMedicos");
       List<Map<String, Object>> ldConsultas = lista(pn, "linhaConsultas");
       boolean ldEhHoje = true; %>
    <section class="hc-panel <%= recepcao ? "span-8" : "span-12" %>" aria-labelledby="tLinha">
        <div class="hc-panel-head"><h2 id="tLinha">Linha do dia</h2><a class="meta" href="agenda">Abrir agenda</a></div>
        <%@ include file="/WEB-INF/views/partials/linha_do_dia.jspf" %>
    </section>
<% } %>

<%-- ---------------------------------------------------------------- proximas chegadas (recepcao) --%>
<% if (recepcao) { List<Map<String, Object>> proximas = lista(pn, "proximas"); %>
    <section class="hc-panel span-4" aria-labelledby="tProximas">
        <div class="hc-panel-head"><h2 id="tProximas">Próximas chegadas</h2><span class="meta"><%= proximas.size() %> na lista</span></div>
        <% if (proximas.isEmpty()) { %>
            <p class="hc-empty"><%= "0".equals(n(pn, "consultasDoDia")) ? "Nenhuma consulta marcada para hoje." : "Todos os pacientes agendados para hoje já chegaram." %></p>
        <% } else { %>
        <div class="hc-rows">
        <% for (Map<String, Object> c : proximas) { %>
            <div class="hc-row">
                <span class="hc-time"><%= Sanitize.escapeHtml(c.get("hora")) %></span>
                <span class="grow"><span class="hc-cell-main"><%= Sanitize.escapeHtml(c.get("paciente")) %></span>
                    <span class="hc-cell-sub"><%= Sanitize.escapeHtml(c.get("medico")) %> · <%= Sanitize.escapeHtml(c.get("especialidade")) %></span></span>
                <button type="button" class="hc-btn hc-btn-sm" data-hc-acao="api/consultas/<%= c.get("id_consulta") %>/chegada">Chegou</button>
            </div>
        <% } %>
        </div>
        <% } %>
    </section>
<% } %>

<%-- ---------------------------------------------------------------- enfermagem --%>
<% if (enfermagem) { List<Map<String, Object>> fila = lista(pn, "filaTriagem"); %>
    <section class="hc-panel span-7" aria-labelledby="tFila">
        <div class="hc-panel-head"><h2 id="tFila">Fila de triagem</h2><span class="meta">por ordem de chegada</span></div>
        <% if (fila.isEmpty()) { %>
            <p class="hc-empty">Ninguém aguardando triagem agora.</p>
        <% } else { %>
        <table class="hc-table"><thead><tr><th>Chegada</th><th>Paciente</th><th class="hide-sm">Consulta com</th><th>Situação</th></tr></thead><tbody>
        <% for (Map<String, Object> c : fila) { %>
            <tr><td class="num"><%= Rotulos.hora(c.get("chegada_em")) %></td>
                <td class="hc-cell-main"><%= Sanitize.escapeHtml(c.get("paciente")) %></td>
                <td class="hide-sm"><%= Sanitize.escapeHtml(c.get("medico")) %></td>
                <td><%= Rotulos.pilulaConsulta(c.get("status")) %></td></tr>
        <% } %>
        </tbody></table>
        <% } %>
    </section>
    <section class="hc-panel span-5" aria-labelledby="tPrio">
        <div class="hc-panel-head"><h2 id="tPrio">Triados hoje por prioridade</h2></div>
        <div class="hc-rows">
        <% for (Map<String, Object> p : lista(pn, "porPrioridade")) { %>
            <div class="hc-row"><span class="hc-priority grow"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(p.get("cor_hex")) %>"></span><%= Sanitize.escapeHtml(p.get("nome")) %></span>
                <span class="num"><%= p.get("total") %></span></div>
        <% } %>
        </div>
    </section>
<% } %>

<%-- ---------------------------------------------------------------- medico --%>
<% if (medico) { List<Map<String, Object>> fila = lista(pn, "filaMedico"); List<Map<String, Object>> agenda = lista(pn, "minhaAgenda"); %>
    <section class="hc-panel span-7" aria-labelledby="tFilaMed">
        <div class="hc-panel-head"><h2 id="tFilaMed">Sua fila de atendimento</h2><span class="meta">prioridade, depois chegada</span></div>
        <% if (fila.isEmpty()) { %>
            <p class="hc-empty">Nenhum paciente triado aguardando você.</p>
        <% } else { %>
        <div class="hc-rows">
        <% for (Map<String, Object> c : fila) { %>
            <div class="hc-row">
                <span class="hc-priority" style="width:150px"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(c.get("cor_hex") != null ? c.get("cor_hex") : "#c9cdd1") %>"></span><%= Rotulos.ou(c.get("prioridade")) %></span>
                <span class="grow hc-cell-main"><%= Sanitize.escapeHtml(c.get("paciente")) %></span>
            </div>
        <% } %>
        </div>
        <% } %>
    </section>
    <section class="hc-panel span-5" aria-labelledby="tAgMed">
        <div class="hc-panel-head"><h2 id="tAgMed">Sua agenda de hoje</h2><span class="meta"><%= agenda.size() %> consultas · <%= n(pn, "realizadas") %> realizadas</span></div>
        <% if (agenda.isEmpty()) { %>
            <p class="hc-empty">Nenhuma consulta marcada para hoje.</p>
        <% } else { %>
        <div class="hc-rows">
        <% for (Map<String, Object> c : agenda) { %>
            <div class="hc-row"><span class="hc-time"><%= Sanitize.escapeHtml(c.get("hora")) %></span>
                <span class="grow"><%= Sanitize.escapeHtml(c.get("paciente")) %></span><%= Rotulos.pilulaConsulta(c.get("status")) %></div>
        <% } %>
        </div>
        <% } %>
    </section>
<% } %>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
