<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Fila de triagem";
@SuppressWarnings("unchecked")
List<Map<String, Object>> fila = (List<Map<String, Object>>) request.getAttribute("fila");
if (fila == null) { response.sendRedirect(utils.Url.to(request, "triagem")); return; }
@SuppressWarnings("unchecked")
List<Map<String, Object>> recentes = (List<Map<String, Object>>) request.getAttribute("recentes");
@SuppressWarnings("unchecked")
List<Map<String, Object>> classes = (List<Map<String, Object>>) request.getAttribute("classificacoes");
int aguardando = 0;
for (Map<String, Object> c : fila) { if ("aguardando_triagem".equals(c.get("status"))) aguardando++; }
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<div class="hc-title-row">
    <div>
        <h1>Fila de triagem</h1>
        <p class="hc-subtitle"><%= aguardando %> aguardando · <%= fila.size() - aguardando %> em triagem agora · por ordem de chegada</p>
    </div>
</div>

<div class="hc-grid">
    <section class="hc-panel span-8" aria-labelledby="tFila">
        <div class="hc-panel-head"><h2 id="tFila">Pacientes que chegaram</h2><span class="meta">a recepção registra a chegada</span></div>
        <% if (fila.isEmpty()) { %>
            <p class="hc-empty">Ninguém aguardando triagem agora. A fila atualiza quando a recepção registrar uma chegada.</p>
        <% } else { %>
        <table class="hc-table">
            <thead><tr><th style="width:84px">Chegada</th><th>Paciente</th><th class="hide-sm">Consulta com</th><th>Situação</th><th class="text-end" style="width:170px"><span class="visually-hidden">Ação</span></th></tr></thead>
            <tbody>
            <% for (Map<String, Object> c : fila) {
                   Object idC = c.get("id_consulta");
                   boolean emTriagem = "em_triagem".equals(c.get("status"));
                   java.time.LocalDate nasc = c.get("data_nascimento") instanceof java.sql.Date ? ((java.sql.Date) c.get("data_nascimento")).toLocalDate()
                           : (c.get("data_nascimento") instanceof java.time.LocalDate ? (java.time.LocalDate) c.get("data_nascimento") : null); %>
                <tr>
                    <td class="num"><%= Rotulos.hora(c.get("chegada_em")) %></td>
                    <td><span class="hc-cell-main"><%= Sanitize.escapeHtml(c.get("paciente")) %></span>
                        <% if (nasc != null) { %><span class="hc-cell-sub"><%= utils.Validacao.idade(nasc, java.time.LocalDate.now()) %> anos</span><% } %></td>
                    <td class="hide-sm"><span class="hc-cell-main"><%= Sanitize.escapeHtml(c.get("medico")) %></span>
                        <span class="hc-cell-sub"><%= Sanitize.escapeHtml(c.get("especialidade")) %> · <%= Rotulos.hora(c.get("data_hora")) %></span></td>
                    <td><%= Rotulos.pilulaConsulta(c.get("status")) %></td>
                    <td class="text-end">
                        <% if (emTriagem) { %>
                            <a class="hc-btn hc-btn-sm" href="triagem/<%= idC %>">Continuar</a>
                        <% } else { %>
                            <form data-hc-form method="POST" action="api/triagem/<%= idC %>/iniciar" class="d-inline">
                                <button class="hc-btn hc-btn-sm hc-btn-primary" type="submit">Iniciar triagem</button>
                            </form>
                        <% } %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>

    <section class="hc-panel span-4" aria-labelledby="tRec">
        <div class="hc-panel-head"><h2 id="tRec">Suas triagens de hoje</h2><span class="meta"><%= recentes.size() %></span></div>
        <% if (recentes.isEmpty()) { %>
            <p class="hc-empty">Você ainda não registrou triagens hoje.</p>
        <% } else { %>
        <div class="hc-rows">
        <% for (Map<String, Object> t : recentes) { boolean retificada = !"0".equals(String.valueOf(t.get("retificada"))); %>
            <a class="hc-row" href="triagem/<%= t.get("id_consulta") %>">
                <span class="hc-time"><%= Rotulos.hora(t.get("registrado_em")) %></span>
                <span class="grow"><span class="hc-cell-main"><%= Sanitize.escapeHtml(t.get("paciente")) %></span>
                    <span class="hc-cell-sub hc-priority"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(t.get("cor_hex")) %>"></span><%= Sanitize.escapeHtml(t.get("prioridade")) %><%= retificada ? " · retificada" : "" %></span></span>
            </a>
        <% } %>
        </div>
        <% } %>

        <div class="hc-panel-head mt-4"><h2>Classificação de prioridade</h2></div>
        <div class="hc-rows">
        <% for (Map<String, Object> p : classes) { int alvo = ((Number) p.get("tempo_alvo_min")).intValue(); %>
            <div class="hc-row"><span class="hc-priority grow"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(p.get("cor_hex")) %>"></span><%= Sanitize.escapeHtml(p.get("nome")) %></span>
                <span class="hc-cell-sub"><%= alvo == 0 ? "imediato" : "até " + alvo + " min" %></span></div>
        <% } %>
        </div>
    </section>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
