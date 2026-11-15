<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
java.time.LocalDate dia = (java.time.LocalDate) request.getAttribute("dia");
if (dia == null) { response.sendRedirect(utils.Url.to(request, "exames")); return; }
@SuppressWarnings("unchecked")
List<Map<String, Object>> exames = (List<Map<String, Object>>) request.getAttribute("exames");
@SuppressWarnings("unchecked")
List<Map<String, Object>> locais = (List<Map<String, Object>>) request.getAttribute("locais");
String tituloPagina = "Agenda de exames";
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%
boolean ehHoje = dia.equals(models.Agenda.agora().toLocalDate());
boolean podeAlterar = layoutPermissoes.contains("exames.agendar");
boolean verPaciente = layoutPermissoes.contains("pacientes.view");
int ativos = 0, realizados = 0;
for (Map<String, Object> x : exames) {
    if (!"cancelado".equals(x.get("status"))) { ativos++; }
    if ("realizado".equals(x.get("status"))) { realizados++; }
}
%>
<div class="hc-title-row">
    <div>
        <h1>Agenda de exames</h1>
        <p class="hc-subtitle"><%= Sanitize.escapeHtml(Rotulos.diaPorExtenso(dia)) %><%= ehHoje ? " (hoje)" : "" %> ·
            <%= ativos %> <%= ativos == 1 ? "exame" : "exames" %>, <%= realizados %> realizados</p>
    </div>
    <div class="hc-actions">
        <a class="hc-btn" href="exames?data=<%= dia.minusDays(1) %>" aria-label="Dia anterior"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-chevron-left"/></svg></a>
        <form method="GET" action="exames"><label class="visually-hidden-hc" for="dataEx">Data</label>
            <input id="dataEx" class="hc-input" style="height:36px;width:160px" type="date" name="data" value="<%= dia %>" onchange="this.form.submit()"></form>
        <a class="hc-btn" href="exames?data=<%= dia.plusDays(1) %>" aria-label="Próximo dia"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-chevron-right"/></svg></a>
        <% if (!ehHoje) { %><a class="hc-btn hc-btn-text" href="exames">Hoje</a><% } %>
        <% if (podeAlterar) { %><a class="hc-btn hc-btn-primary" href="exames/novo"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-flask"/></svg>Agendar exame</a><% } %>
    </div>
</div>

<div class="hc-grid">
<% for (Map<String, Object> l : locais) {
       int idL = ((Number) l.get("id_local")).intValue();
       List<Map<String, Object>> doLocal = new ArrayList<Map<String, Object>>();
       for (Map<String, Object> x : exames) { if (((Number) x.get("id_local")).intValue() == idL) { doLocal.add(x); } } %>
    <section class="hc-panel span-12" aria-labelledby="tLocal<%= idL %>">
        <div class="hc-panel-head">
            <h2 id="tLocal<%= idL %>"><%= Sanitize.escapeHtml(l.get("nome")) %></h2>
            <span class="meta num">atende <%= String.valueOf(l.get("hora_inicio")).substring(0, 5) %>–<%= String.valueOf(l.get("hora_fim")).substring(0, 5) %> · <%= doLocal.size() %> no dia</span>
        </div>
        <% if (doLocal.isEmpty()) { %>
            <p class="hc-empty">Nenhum exame neste local no dia.</p>
        <% } else { %>
        <table class="hc-table is-agenda">
            <colgroup><col class="col-hora"><col><col><col class="col-situacao"><col class="col-acoes"></colgroup>
            <thead><tr><th>Hora</th><th>Paciente</th><th>Exame</th><th>Situação</th><th class="num"><span class="visually-hidden-hc">Ações</span></th></tr></thead>
            <tbody>
            <% for (Map<String, Object> x : doLocal) {
                   String st = String.valueOf(x.get("status")); Object idX = x.get("id_exame_agendado"); %>
                <tr>
                    <td class="hc-hora"><%= Sanitize.escapeHtml(x.get("hora")) %><span class="hc-cell-sub"><%= x.get("duracao_min") %> min</span></td>
                    <td><% if (verPaciente) { %><a href="pacientes/<%= x.get("id_paciente") %>"><%= Sanitize.escapeHtml(x.get("paciente")) %></a><% } else { %><%= Sanitize.escapeHtml(x.get("paciente")) %><% } %></td>
                    <td><%= Sanitize.escapeHtml(x.get("exame")) %></td>
                    <td><%= Rotulos.pilulaExame(st) %></td>
                    <td class="num">
                    <% if (podeAlterar && "agendado".equals(st)) { %>
                        <% if (ehHoje) { %>
                        <button type="button" class="hc-btn hc-btn-sm" data-hc-acao="api/exames-agendados/<%= idX %>/situacao" data-hc-dados='{"situacao":"realizado"}'>Realizado</button>
                        <button type="button" class="hc-btn hc-btn-sm hc-btn-text" data-hc-acao="api/exames-agendados/<%= idX %>/situacao" data-hc-dados='{"situacao":"ausente"}'>Faltou</button>
                        <% } %>
                        <button type="button" class="hc-btn hc-btn-sm hc-btn-text hc-btn-danger" data-hc-acao="api/exames-agendados/<%= idX %>/cancelar" data-hc-motivo="1"
                                data-hc-alvo="exame das <%= Sanitize.escapeHtml(x.get("hora")) %> de <%= Sanitize.escapeHtml(x.get("paciente")) %>">Cancelar</button>
                    <% } %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>
<% } %>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
