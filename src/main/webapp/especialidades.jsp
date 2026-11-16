<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Especialidades";
@SuppressWarnings("unchecked")
List<Map<String, Object>> lista = (List<Map<String, Object>>) request.getAttribute("especialidades");
if (lista == null) { response.sendRedirect(utils.Url.to(request, "especialidades")); return; }
int ativas = 0;
for (Map<String, Object> e : lista) { if ("1".equals(String.valueOf(e.get("ativo")))) ativas++; }
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<div class="hc-breadcrumb"><a href="profissionais">Profissionais</a><span aria-hidden="true">/</span> Especialidades</div>
<div class="hc-title-row">
    <div>
        <h1>Especialidades</h1>
        <p class="hc-subtitle"><%= ativas %> <%= ativas == 1 ? "ativa" : "ativas" %> de <%= lista.size() %>. Inativa, a especialidade some do agendamento, mas continua no histórico.</p>
    </div>
</div>

<div class="hc-grid">
    <section class="hc-panel span-8" aria-labelledby="tLista">
        <div class="hc-panel-head"><h2 id="tLista">Cadastradas</h2></div>
        <% if (lista.isEmpty()) { %><p class="hc-empty">Nenhuma especialidade cadastrada.</p><% } else { %>
        <table class="hc-table">
            <thead><tr><th>Especialidade</th><th class="num" style="width:90px">Médicos</th><th class="num hide-sm" style="width:130px">Consultas futuras</th><th style="width:90px">Situação</th><th style="width:170px"><span class="visually-hidden-hc">Ações</span></th></tr></thead>
            <tbody>
            <% for (Map<String, Object> e : lista) {
                   boolean ativa = "1".equals(String.valueOf(e.get("ativo")));
                   Object id = e.get("id_especialidade"); %>
                <tr>
                    <td><span class="hc-cell-main"><%= Sanitize.escapeHtml(e.get("nome")) %></span>
                        <% if (e.get("descricao") != null) { %><span class="hc-cell-sub"><%= Sanitize.escapeHtml(e.get("descricao")) %></span><% } %></td>
                    <td class="num"><%= e.get("medicos") %></td>
                    <td class="num hide-sm"><%= e.get("consultas_futuras") %></td>
                    <td><span class="hc-pill <%= ativa ? "positive" : "neutral" %>"><%= ativa ? "Ativa" : "Inativa" %></span></td>
                    <td class="num">
                        <button type="button" class="hc-btn hc-btn-sm hc-btn-text" data-editar
                                data-id="<%= id %>" data-nome="<%= Sanitize.escapeHtml(e.get("nome")) %>"
                                data-descricao="<%= e.get("descricao") == null ? "" : Sanitize.escapeHtml(e.get("descricao")) %>">Editar</button>
                        <button type="button" class="hc-btn hc-btn-sm<%= ativa ? " hc-btn-text hc-btn-danger" : "" %>" data-hc-acao="api/especialidades/<%= id %>/situacao"
                                data-hc-dados='{"ativo":"<%= ativa ? "0" : "1" %>"}'><%= ativa ? "Inativar" : "Reativar" %></button>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <% } %>
    </section>

    <section class="hc-panel span-4" aria-labelledby="tForm">
        <div class="hc-panel-head"><h2 id="tForm">Nova especialidade</h2></div>
        <form id="formEsp" data-hc-form method="POST" action="api/especialidades" novalidate>
            <div class="hc-field w-6 mb-3"><label for="nomeEsp">Nome <span class="req">*</span></label>
                <input class="hc-input" id="nomeEsp" name="nome" maxlength="100" required>
                <span class="hc-error" data-erro-de="nome"></span></div>
            <div class="hc-field w-6 mb-3"><label for="descEsp">Descrição</label>
                <textarea class="hc-input" id="descEsp" name="descricao" maxlength="255"></textarea>
                <span class="hc-error" data-erro-de="descricao"></span></div>
            <div class="d-flex gap-2 justify-content-end">
                <button type="button" class="hc-btn" id="cancelarEdicao" hidden>Cancelar edição</button>
                <button type="submit" class="hc-btn hc-btn-primary" id="salvarEsp">Cadastrar</button>
            </div>
        </form>
    </section>
</div>
<script>
document.addEventListener("DOMContentLoaded", function () {
    const $f = $('#formEsp');
    function modoNovo() {
        $f.attr('action', 'api/especialidades');
        $('#tForm').text('Nova especialidade');
        $('#salvarEsp').text('Cadastrar');
        $('#cancelarEdicao').attr('hidden', true);
        $f[0].reset();
    }
    $(document).on('click', '[data-editar]', function () {
        const $b = $(this);
        $f.attr('action', 'api/especialidades/' + $b.data('id'));
        $('#tForm').text('Editar especialidade');
        $('#salvarEsp').text('Salvar alterações');
        $('#cancelarEdicao').removeAttr('hidden');
        $('#nomeEsp').val($b.data('nome')).trigger('focus');
        $('#descEsp').val($b.data('descricao'));
    });
    $('#cancelarEdicao').on('click', modoNovo);
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
