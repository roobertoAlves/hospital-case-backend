<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
java.time.LocalDate dia = (java.time.LocalDate) request.getAttribute("dia");
if (dia == null) { response.sendRedirect(utils.Url.to(request, "agenda")); return; }
@SuppressWarnings("unchecked")
List<Map<String, Object>> consultas = (List<Map<String, Object>>) request.getAttribute("consultas");
@SuppressWarnings("unchecked")
List<Map<String, Object>> medicosDia = (List<Map<String, Object>>) request.getAttribute("medicos");
boolean propria = Boolean.TRUE.equals(request.getAttribute("agendaPropria"));
String tituloPagina = propria ? "Minha agenda" : "Agenda de consultas";
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%
java.time.LocalDate hojeAg = models.Agenda.agora().toLocalDate();
boolean ehHoje = dia.equals(hojeAg);
int ativas = 0, chegaram = 0, canceladas = 0;
for (Map<String, Object> c : consultas) {
    String st = String.valueOf(c.get("status"));
    if ("cancelada".equals(st)) { canceladas++; } else { ativas++; }
    if (!"agendada".equals(st) && !"cancelada".equals(st) && !"ausente".equals(st)) { chegaram++; }
}
boolean podeChegada = layoutPermissoes.contains("consultas.chegada");
boolean podeCancelar = layoutPermissoes.contains("consultas.cancelar");
boolean podeRemarcar = layoutPermissoes.contains("consultas.remarcar");
boolean podeAgendar = layoutPermissoes.contains("consultas.agendar");
boolean verPaciente = layoutPermissoes.contains("pacientes.view");
%>
<div class="hc-title-row">
    <div>
        <h1><%= Sanitize.escapeHtml(tituloPagina) %></h1>
        <p class="hc-subtitle"><%= Sanitize.escapeHtml(Rotulos.diaPorExtenso(dia)) %><%= ehHoje ? " (hoje)" : "" %> ·
            <%= ativas %> <%= ativas == 1 ? "consulta" : "consultas" %>, <%= chegaram %> já na unidade<%= canceladas > 0 ? ", " + canceladas + " canceladas" : "" %></p>
    </div>
    <div class="hc-actions">
        <a class="hc-btn" href="agenda?data=<%= dia.minusDays(1) %>" aria-label="Dia anterior"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-chevron-left"/></svg></a>
        <form method="GET" action="agenda" class="d-flex gap-2">
            <label class="visually-hidden-hc" for="dataAgenda">Data</label>
            <input id="dataAgenda" class="hc-input" style="height:36px;width:160px" type="date" name="data" value="<%= dia %>" onchange="this.form.submit()">
        </form>
        <a class="hc-btn" href="agenda?data=<%= dia.plusDays(1) %>" aria-label="Próximo dia"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-chevron-right"/></svg></a>
        <% if (!ehHoje) { %><a class="hc-btn hc-btn-text" href="agenda">Hoje</a><% } %>
        <% if (podeAgendar) { %><a class="hc-btn hc-btn-primary" href="agenda/nova"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-calendar-plus"/></svg>Agendar consulta</a><% } %>
    </div>
</div>

<div class="hc-grid">
    <section class="hc-panel span-12" aria-labelledby="tLinhaAg">
        <div class="hc-panel-head"><h2 id="tLinhaAg">Linha do dia</h2><span class="meta">uma linha por médico</span></div>
        <% List<Map<String, Object>> ldMedicos = medicosDia; List<Map<String, Object>> ldConsultas = consultas; boolean ldEhHoje = ehHoje; %>
        <%@ include file="/WEB-INF/views/partials/linha_do_dia.jspf" %>
    </section>

    <section class="hc-panel span-12" aria-labelledby="tLista">
        <div class="hc-panel-head"><h2 id="tLista">Consultas do dia</h2><span class="meta"><%= consultas.size() %> no total</span></div>
        <% if (consultas.isEmpty()) { %>
            <p class="hc-empty">Nenhuma consulta para este dia.<% if (podeAgendar) { %> <a href="agenda/nova">Agendar consulta</a><% } %></p>
        <% } else { %>
        <table class="hc-table is-agenda">
            <colgroup><col class="col-hora"><col><col class="hide-sm"><col class="col-situacao"><col class="col-acoes"></colgroup>
            <thead><tr><th>Hora</th><th>Paciente</th><th class="hide-sm">Médico</th><th>Situação</th><th class="num"><span class="visually-hidden-hc">Ações</span></th></tr></thead>
            <tbody>
            <% for (Map<String, Object> c : consultas) {
                   String st = String.valueOf(c.get("status"));
                   Object idC = c.get("id_consulta"); %>
                <tr>
                    <td class="hc-hora"><%= Sanitize.escapeHtml(c.get("hora")) %></td>
                    <td><% if (verPaciente) { %><a href="pacientes/<%= c.get("id_paciente") %>"><%= Sanitize.escapeHtml(c.get("paciente")) %></a><% } else { %><%= Sanitize.escapeHtml(c.get("paciente")) %><% } %>
                        <span class="hc-cell-sub"><%= "encaixe".equals(c.get("tipo")) ? "Encaixe · " : "" %><%= Sanitize.escapeHtml(c.get("especialidade")) %></span></td>
                    <td class="hide-sm"><%= Sanitize.escapeHtml(c.get("medico")) %></td>
                    <td><%= Rotulos.pilulaConsulta(st) %></td>
                    <td class="num">
                        <% if (podeChegada && ehHoje && "agendada".equals(st)) { %>
                        <button type="button" class="hc-btn hc-btn-sm" data-hc-acao="api/consultas/<%= idC %>/chegada">Registrar chegada</button>
                        <% } %>
                        <% if (podeRemarcar && "agendada".equals(st)) { %>
                        <a class="hc-btn hc-btn-sm hc-btn-text" href="agenda/<%= idC %>/remarcar">Remarcar</a>
                        <% } %>
                        <% if (podeCancelar && ("agendada".equals(st) || "aguardando_triagem".equals(st))) { %>
                        <button type="button" class="hc-btn hc-btn-sm hc-btn-text hc-btn-danger" data-hc-acao="api/consultas/<%= idC %>/cancelar"
                                data-hc-motivo="1" data-hc-alvo="consulta das <%= Sanitize.escapeHtml(c.get("hora")) %> de <%= Sanitize.escapeHtml(c.get("paciente")) %>">Cancelar</button>
                        <% } %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
