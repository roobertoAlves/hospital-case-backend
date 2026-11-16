<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> pac = (Map<String, Object>) request.getAttribute("paciente");
if (pac == null) { response.sendRedirect(utils.Url.to(request, "pacientes")); return; }
String nivel = String.valueOf(request.getAttribute("nivel"));
boolean medico = "MEDICO".equals(nivel);
boolean clinico = medico || "ENFERMAGEM".equals(nivel);
String tituloPagina = "Histórico · " + pac.get("nome");
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(HttpServletRequest req, String chave) {
        Object v = req.getAttribute(chave);
        return v instanceof List ? (List<Map<String, Object>>) v : new ArrayList<Map<String, Object>>();
    }
    private static String n(Object o) {
        if (o instanceof java.math.BigDecimal) { o = ((java.math.BigDecimal) o).stripTrailingZeros().toPlainString(); }
        return o == null ? "—" : utils.Sanitize.escapeHtml(String.valueOf(o));
    }
%>
<div class="hc-breadcrumb"><a href="pacientes">Pacientes</a><span aria-hidden="true">/</span><a href="pacientes/<%= pac.get("id_paciente") %>"><%= Sanitize.escapeHtml(pac.get("nome")) %></a><span aria-hidden="true">/</span>Histórico</div>
<div class="hc-title-row">
    <div>
        <h1>Histórico de <%= Sanitize.escapeHtml(pac.get("nome")) %></h1>
        <p class="hc-subtitle"><%= medico ? "Visão médica: atendimentos, triagens, receitas e exames." : (clinico ? "Visão da enfermagem: triagens e alergias." : "Visão administrativa: consultas e exames agendados, sem dados clínicos.") %>
            O acesso fica registrado na auditoria.</p>
    </div>
</div>

<% if (clinico) { List<Map<String, Object>> alergias = lista(request, "alergias"); %>
<div class="hc-alert <%= alergias.isEmpty() ? "note" : "err" %>" role="<%= alergias.isEmpty() ? "status" : "alert" %>">
    <strong>Alergias:</strong>
    <% if (alergias.isEmpty()) { %> nenhuma registrada.<% } %>
    <% for (int i = 0; i < alergias.size(); i++) { Map<String, Object> a = alergias.get(i); %>
        <%= i > 0 ? " · " : "" %><%= Sanitize.escapeHtml(a.get("substancia")) %><%= a.get("gravidade") != null ? " (" + Sanitize.escapeHtml(a.get("gravidade")) + ")" : "" %>
    <% } %>
</div>
<% } %>

<div class="hc-grid">
<% if (medico) { List<Map<String, Object>> pront = lista(request, "prontuarios"); %>
    <section class="hc-panel span-8" aria-labelledby="tPront">
        <div class="hc-panel-head"><h2 id="tPront">Atendimentos</h2><span class="meta"><%= pront.size() %> finalizados</span></div>
        <% if (pront.isEmpty()) { %><p class="hc-empty">Nenhum atendimento finalizado.</p><% } %>
        <% for (Map<String, Object> p : pront) { %>
            <div class="hc-entry">
                <div class="hc-entry-meta"><%= Rotulos.data(p.get("data_hora")) %> · <%= Sanitize.escapeHtml(p.get("medico")) %> · <%= Sanitize.escapeHtml(p.get("especialidade")) %></div>
                <p class="mb-1"><strong>Queixa:</strong> <%= Rotulos.ou(p.get("queixa_principal")) %></p>
                <p class="mb-1"><strong>Hipóteses:</strong> <%= Rotulos.ou(p.get("hipoteses_diagnosticas")) %></p>
                <p class="mb-0 hc-note-block"><strong>Conduta:</strong> <%= Rotulos.ou(p.get("conduta")) %></p>
            </div>
        <% } %>
    </section>
    <div class="span-4">
        <section class="hc-panel mb-3" aria-labelledby="tRec">
            <div class="hc-panel-head"><h2 id="tRec">Receitas</h2></div>
            <% List<Map<String, Object>> rec = lista(request, "receitas"); if (rec.isEmpty()) { %><p class="hc-help mb-0">Nenhuma receita.</p><% } %>
            <% for (Map<String, Object> r : rec) { %>
                <div class="hc-entry"><div class="hc-entry-meta"><%= Rotulos.data(r.get("emitida_em")) %> · <%= Sanitize.escapeHtml(r.get("medico")) %><%= "cancelada".equals(r.get("status")) ? " · cancelada" : "" %></div>
                <% for (Object o : (List<?>) r.get("itens")) { @SuppressWarnings("unchecked") Map<String, Object> i = (Map<String, Object>) o; %>
                    <div><%= Sanitize.escapeHtml(i.get("medicamento")) %> <%= Sanitize.escapeHtml(i.get("dosagem")) %> · <%= Sanitize.escapeHtml(i.get("frequencia")) %></div>
                <% } %></div>
            <% } %>
        </section>
        <section class="hc-panel" aria-labelledby="tExa">
            <div class="hc-panel-head"><h2 id="tExa">Exames solicitados</h2></div>
            <% List<Map<String, Object>> exa = lista(request, "exames"); if (exa.isEmpty()) { %><p class="hc-help mb-0">Nenhum exame.</p><% } %>
            <% for (Map<String, Object> e : exa) { %>
                <div class="hc-row"><span class="grow"><span class="hc-cell-main"><%= Sanitize.escapeHtml(e.get("nome_exame")) %></span>
                    <span class="hc-cell-sub"><%= Rotulos.data(e.get("solicitado_em")) %> · <%= Sanitize.escapeHtml(e.get("medico")) %><%= "cancelado".equals(e.get("status")) ? " · cancelado" : "" %></span></span></div>
            <% } %>
        </section>
    </div>
<% } %>

<% if (clinico) { List<Map<String, Object>> tri = lista(request, "triagens"); %>
    <section class="hc-panel span-12" aria-labelledby="tTri">
        <div class="hc-panel-head"><h2 id="tTri">Triagens</h2><span class="meta">inclui versões retificadas</span></div>
        <% if (tri.isEmpty()) { %><p class="hc-empty">Nenhuma triagem.</p><% } else { %>
        <table class="hc-table">
            <thead><tr><th>Data</th><th>Prioridade</th><th>PA</th><th class="hide-sm">FC</th><th class="hide-sm">Temp.</th><th class="hide-sm">SpO₂</th><th>Queixa</th><th class="hide-sm">Enfermeiro</th></tr></thead>
            <tbody>
            <% for (Map<String, Object> t : tri) { %>
                <tr<%= t.get("retifica_id") != null ? " title=\"Retificação: " + Sanitize.escapeHtml(t.get("motivo_retificacao")) + "\"" : "" %>>
                    <td class="num"><%= Rotulos.dataHora(t.get("registrado_em")) %><%= t.get("retifica_id") != null ? "<span class=\"hc-cell-sub\">retificação</span>" : "" %></td>
                    <td><span class="hc-priority"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(t.get("cor_hex")) %>"></span><%= Sanitize.escapeHtml(t.get("prioridade")) %></span></td>
                    <td class="num"><%= t.get("pa_sistolica") != null ? n(t.get("pa_sistolica")) + "/" + n(t.get("pa_diastolica")) : "—" %></td>
                    <td class="num hide-sm"><%= n(t.get("frequencia_cardiaca")) %></td>
                    <td class="num hide-sm"><%= n(t.get("temperatura")) %></td>
                    <td class="num hide-sm"><%= n(t.get("saturacao_o2")) %></td>
                    <td><%= Sanitize.escapeHtml(t.get("queixa_principal")) %></td>
                    <td class="hide-sm"><%= Sanitize.escapeHtml(t.get("enfermeiro")) %></td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>
<% } %>

<% List<Map<String, Object>> adm = lista(request, "administrativo"); %>
    <section class="hc-panel span-12" aria-labelledby="tAdm">
        <div class="hc-panel-head"><h2 id="tAdm">Consultas e exames agendados</h2><span class="meta">últimos 50</span></div>
        <% if (adm.isEmpty()) { %><p class="hc-empty">Nada agendado ainda.</p><% } else { %>
        <table class="hc-table">
            <thead><tr><th style="width:150px">Data</th><th style="width:90px">Tipo</th><th>Descrição</th><th>Situação</th></tr></thead>
            <tbody>
            <% for (Map<String, Object> h : adm) { boolean exame = "exame".equals(h.get("tipo")); %>
                <tr><td class="num"><%= Rotulos.dataHora(h.get("data_hora")) %></td>
                    <td><%= exame ? "Exame" : "Consulta" %></td>
                    <td><%= Sanitize.escapeHtml(h.get("descricao")) %><%= h.get("motivo_cancelamento") != null ? "<span class=\"hc-cell-sub\">" + Sanitize.escapeHtml(h.get("motivo_cancelamento")) + "</span>" : "" %></td>
                    <td><%= exame ? Rotulos.pilulaExame(h.get("status")) : Rotulos.pilulaConsulta(h.get("status")) %></td></tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
