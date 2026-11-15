<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos, utils.Validacao, models.Paciente" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> pac = (Map<String, Object>) request.getAttribute("paciente");
@SuppressWarnings("unchecked")
List<Map<String, Object>> historico = (List<Map<String, Object>>) request.getAttribute("historico");
if (pac == null) { response.sendRedirect(utils.Url.to(request, "pacientes")); return; }
String tituloPagina = String.valueOf(pac.get("nome"));
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%
int idPac = ((Number) pac.get("id_paciente")).intValue();
boolean ativo = ((Number) pac.get("ativo")).intValue() == 1;
Object nasc = pac.get("data_nascimento");
java.time.LocalDate dn = nasc instanceof java.time.LocalDate ? (java.time.LocalDate) nasc : ((java.sql.Date) nasc).toLocalDate();
String endereco = "";
for (String k : new String[]{"logradouro", "numero", "complemento", "bairro", "cidade", "uf"}) {
    if (pac.get(k) != null) { endereco += (endereco.isEmpty() ? "" : ", ") + pac.get(k); }
}
if (pac.get("cep") != null) { String c = String.valueOf(pac.get("cep")); endereco += (endereco.isEmpty() ? "" : " · ") + "CEP " + c.substring(0, 5) + "-" + c.substring(5); }
%>
<div class="hc-breadcrumb"><a href="pacientes">Pacientes</a><span aria-hidden="true">/</span> Ficha</div>
<div class="hc-title-row">
    <div>
        <h1><%= Sanitize.escapeHtml(pac.get("nome")) %></h1>
        <p class="hc-subtitle"><%= Validacao.idade(dn, Paciente.hoje()) %> anos · CPF <%= Validacao.formatarCpf(String.valueOf(pac.get("cpf"))) %>
            · <span class="hc-pill <%= ativo ? "positive" : "neutral" %>"><%= ativo ? "Ativo" : "Inativo" %></span></p>
    </div>
    <div class="hc-actions">
        <% if (layoutPermissoes.contains("pacientes.inativar")) { %>
        <button type="button" class="hc-btn hc-btn-text <%= ativo ? "hc-btn-danger" : "" %>" data-hc-acao="api/pacientes/<%= idPac %>/situacao" data-hc-dados='{"ativo": "<%= ativo ? 0 : 1 %>"}'><%= ativo ? "Inativar" : "Reativar" %></button>
        <% } %>
        <% if (layoutPermissoes.contains("pacientes.edit")) { %>
        <a class="hc-btn" href="pacientes/<%= idPac %>/editar"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-edit"/></svg>Editar cadastro</a>
        <% } %>
        <% if (ativo && layoutPermissoes.contains("exames.agendar")) { %>
        <a class="hc-btn" href="exames/novo?paciente=<%= idPac %>"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-flask"/></svg>Agendar exame</a>
        <% } %>
        <% if (ativo && layoutPermissoes.contains("consultas.agendar")) { %>
        <a class="hc-btn hc-btn-primary" href="agenda/nova?paciente=<%= idPac %>"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-calendar-plus"/></svg>Agendar consulta</a>
        <% } %>
    </div>
</div>

<div class="hc-grid">
    <section class="hc-panel span-5" aria-labelledby="tDados">
        <div class="hc-panel-head"><h2 id="tDados">Dados cadastrais</h2><span class="meta">atualizado em <%= Rotulos.dataHora(pac.get("atualizado_em")) %></span></div>
        <dl class="hc-dl">
            <dt>Nascimento</dt><dd class="num"><%= Rotulos.data(nasc) %></dd>
            <dt>Sexo</dt><dd><%= Rotulos.sexo(pac.get("sexo")) %></dd>
            <dt>Telefone</dt><dd class="num"><%= Rotulos.ou(pac.get("telefone")) %></dd>
            <dt>E-mail</dt><dd><%= Rotulos.ou(pac.get("email")) %></dd>
            <dt>Endereço</dt><dd><%= Rotulos.ou(endereco) %></dd>
            <dt>Emergência</dt><dd><%= pac.get("contato_emergencia_nome") == null ? "—"
                    : Sanitize.escapeHtml(pac.get("contato_emergencia_nome")) + (pac.get("contato_emergencia_parentesco") != null ? " (" + Sanitize.escapeHtml(pac.get("contato_emergencia_parentesco")) + ")" : "")
                    + "<span class=\"hc-cell-sub num\">" + Rotulos.ou(pac.get("contato_emergencia_telefone")) + "</span>" %></dd>
            <dt>Cadastrado em</dt><dd class="num"><%= Rotulos.dataHora(pac.get("criado_em")) %></dd>
        </dl>
    </section>

    <section class="hc-panel span-7" aria-labelledby="tHist">
        <div class="hc-panel-head"><h2 id="tHist">Consultas e exames</h2><span class="meta"><%= historico.size() %> registros · mais recentes primeiro</span></div>
        <% if (historico.isEmpty()) { %>
            <p class="hc-empty">Nenhuma consulta ou exame agendado para este paciente.</p>
        <% } else { %>
        <table class="hc-table">
            <thead><tr><th>Data</th><th>Tipo</th><th>Detalhe</th><th>Situação</th></tr></thead>
            <tbody>
            <% for (Map<String, Object> h : historico) { boolean consulta = "consulta".equals(h.get("tipo")); %>
                <tr>
                    <td class="num"><%= Rotulos.dataHora(h.get("data_hora")) %></td>
                    <td><%= consulta ? "Consulta" : "Exame" %></td>
                    <td><%= Sanitize.escapeHtml(h.get("descricao")) %>
                        <% if (h.get("motivo_cancelamento") != null) { %><span class="hc-cell-sub">Motivo: <%= Sanitize.escapeHtml(h.get("motivo_cancelamento")) %></span><% } %></td>
                    <td><%= consulta ? Rotulos.pilulaConsulta(h.get("status")) : Rotulos.pilulaExame(h.get("status")) %></td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>
</div>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
