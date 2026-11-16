<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Fila de atendimento";
@SuppressWarnings("unchecked")
List<Map<String, Object>> fila = (List<Map<String, Object>>) request.getAttribute("fila");
if (fila == null) { response.sendRedirect(utils.Url.to(request, "atendimento")); return; }
boolean semCadastro = Boolean.TRUE.equals(request.getAttribute("semCadastro"));
int emCurso = 0;
for (Map<String, Object> c : fila) { if ("em_atendimento".equals(c.get("status"))) emCurso++; }
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<div class="hc-title-row">
    <div>
        <h1>Fila de atendimento</h1>
        <p class="hc-subtitle"><%= fila.size() - emCurso %> aguardando · <%= emCurso %> em atendimento · ordem: prioridade da triagem, depois chegada</p>
    </div>
    <div class="hc-actions"><a class="hc-btn" href="agenda"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-calendar"/></svg>Minha agenda</a></div>
</div>

<% if (semCadastro) { %>
<div class="hc-alert note">Seu usuário não está ligado a um cadastro de médico ativo. Peça ao administrador para fazer o vínculo.</div>
<% } %>

<section class="hc-panel" aria-labelledby="tFila">
    <div class="hc-panel-head"><h2 id="tFila">Pacientes triados</h2><span class="meta">só os pacientes das suas consultas</span></div>
    <% if (fila.isEmpty()) { %>
        <p class="hc-empty">Nenhum paciente triado aguardando você. Quando a enfermagem concluir uma triagem, o paciente aparece aqui.</p>
    <% } else { %>
    <table class="hc-table">
        <thead><tr><th style="width:170px">Prioridade</th><th>Paciente</th><th class="hide-sm">Queixa na triagem</th><th style="width:84px">Chegada</th><th class="text-end" style="width:180px"><span class="visually-hidden">Ação</span></th></tr></thead>
        <tbody>
        <% for (Map<String, Object> c : fila) {
               Object idC = c.get("id_consulta");
               boolean emAtendimento = "em_atendimento".equals(c.get("status"));
               Object nascObj = c.get("data_nascimento");
               java.time.LocalDate nasc = nascObj instanceof java.sql.Date ? ((java.sql.Date) nascObj).toLocalDate()
                       : (nascObj instanceof java.time.LocalDate ? (java.time.LocalDate) nascObj : null); %>
            <tr>
                <td><span class="hc-priority"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(c.get("cor_hex") != null ? c.get("cor_hex") : "#c9cdd1") %>"></span><%= Rotulos.ou(c.get("prioridade")) %></span></td>
                <td><span class="hc-cell-main"><%= Sanitize.escapeHtml(c.get("paciente")) %></span>
                    <span class="hc-cell-sub"><%= nasc != null ? utils.Validacao.idade(nasc, java.time.LocalDate.now()) + " anos · " : "" %><%= Sanitize.escapeHtml(c.get("especialidade")) %></span></td>
                <td class="hide-sm"><%= Rotulos.ou(c.get("queixa_principal")) %></td>
                <td class="num"><%= Rotulos.hora(c.get("chegada_em")) %></td>
                <td class="text-end">
                    <% if (emAtendimento) { %>
                        <a class="hc-btn hc-btn-sm hc-btn-primary" href="atendimento/<%= idC %>">Continuar</a>
                    <% } else { %>
                        <form data-hc-form method="POST" action="api/atendimento/<%= idC %>/iniciar" class="d-inline">
                            <button class="hc-btn hc-btn-sm hc-btn-primary" type="submit">Chamar e atender</button>
                        </form>
                    <% } %>
                </td>
            </tr>
        <% } %>
        </tbody>
    </table>
    <% } %>
</section>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
