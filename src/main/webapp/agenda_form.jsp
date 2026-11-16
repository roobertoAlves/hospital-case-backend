<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Validacao" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
List<Map<String, Object>> especialidades = (List<Map<String, Object>>) request.getAttribute("especialidades");
if (especialidades == null) { response.sendRedirect(utils.Url.to(request, "agenda/nova")); return; }
@SuppressWarnings("unchecked")
Map<String, Object> pacPre = (Map<String, Object>) request.getAttribute("paciente");
java.time.LocalDate hojeF = (java.time.LocalDate) request.getAttribute("hoje");
@SuppressWarnings("unchecked")
Map<String, Object> original = (Map<String, Object>) request.getAttribute("original");
boolean remarcar = original != null;
String espOriginal = remarcar ? String.valueOf(original.get("id_especialidade")) : "";
String medOriginal = remarcar ? String.valueOf(original.get("id_medico")) : "";
String tituloPagina = remarcar ? "Remarcar consulta" : "Agendar consulta";
java.time.LocalDate dataInicial = hojeF;
if (remarcar) {
    java.time.LocalDate dOrig = java.time.LocalDate.parse(String.valueOf(original.get("data_hora")).substring(0, 10));
    if (dOrig.isAfter(hojeF)) { dataInicial = dOrig; }
}
String pacJson = "";
if (pacPre != null) {
    Map<String, Object> pj = new LinkedHashMap<String, Object>();
    pj.put("id_paciente", pacPre.get("id_paciente"));
    pj.put("nome", pacPre.get("nome"));
    pj.put("cpf", Validacao.cpfMascarado(String.valueOf(pacPre.get("cpf"))));
    pacJson = new com.google.gson.Gson().toJson(pj);
}
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<div class="hc-breadcrumb"><a href="agenda">Agenda de consultas</a><span aria-hidden="true">/</span> <%= remarcar ? "Remarcar" : "Agendar" %></div>
<div class="hc-title-row">
    <div>
        <h1><%= remarcar ? "Remarcar consulta" : "Agendar consulta" %></h1>
        <p class="hc-subtitle"><%= remarcar ? "A consulta atual é cancelada e a nova fica ligada a ela. O paciente recebe o aviso do novo horário." : "Escolha o paciente, a especialidade e o médico; depois um horário livre." %></p>
    </div>
</div>
<% if (remarcar && !"agendada".equals(original.get("status"))) { %>
<div class="hc-alert err" role="alert">Esta consulta não está mais agendada (situação: <%= utils.Rotulos.pilulaConsulta(original.get("status")) %>). Só consultas antes da chegada podem ser remarcadas.</div>
<% } %>
<% if (remarcar) { %>
<div class="hc-alert note" role="status"><strong>Horário atual:</strong> <%= utils.Rotulos.data(original.get("data_hora")) %> às <%= Sanitize.escapeHtml(original.get("hora")) %>
    · <%= Sanitize.escapeHtml(original.get("medico")) %> · <%= Sanitize.escapeHtml(original.get("especialidade")) %></div>
<% } %>

<form id="formConsulta" data-hc-form method="POST" action="<%= remarcar ? "api/consultas/" + original.get("id_consulta") + "/remarcar" : "api/consultas" %>" novalidate>
    <section class="hc-form-section" aria-labelledby="sPac">
        <div><h2 id="sPac">Paciente</h2><p><%= remarcar ? "Na remarcação o paciente não muda." : "Busque por nome ou CPF. Só pacientes ativos aparecem." %></p></div>
        <div data-hc-busca-paciente <% if (!pacJson.isEmpty()) { %>data-paciente="<%= Sanitize.escapeHtml(pacJson) %>"<% } %>>
            <div class="hc-field w-6" style="max-width:520px"<%= remarcar ? " hidden" : "" %>>
                <label for="buscaPac">Buscar paciente</label>
                <input id="buscaPac" class="hc-input" data-busca type="search" autocomplete="off" placeholder="Nome ou CPF"
                       role="combobox" aria-expanded="false" aria-controls="listaPac">
                <ul id="listaPac" role="listbox" class="list-unstyled mb-0" hidden></ul>
                <input type="hidden" name="id_paciente" value="">
                <span class="hc-help">Não encontrou? <a href="pacientes/novo">Cadastrar paciente</a></span>
            </div>
            <div data-escolhido class="hc-panel mt-2" style="max-width:520px" hidden></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sMed">
        <div><h2 id="sMed">Especialidade e médico</h2><p>Só médicos ativos que atendem a especialidade.</p></div>
        <div class="hc-fields">
            <div class="hc-field"><label for="esp">Especialidade <span class="req">*</span></label>
                <select class="hc-select" id="esp" name="id_especialidade" required<%= remarcar ? " disabled" : "" %>>
                    <option value="">Escolha…</option>
                    <% for (Map<String, Object> e : especialidades) { %>
                    <option value="<%= e.get("id_especialidade") %>"<%= String.valueOf(e.get("id_especialidade")).equals(espOriginal) ? " selected" : "" %>><%= Sanitize.escapeHtml(e.get("nome")) %></option>
                    <% } %>
                </select></div>
            <div class="hc-field"><label for="med">Médico <span class="req">*</span></label>
                <select class="hc-select" id="med" name="id_medico" required disabled data-atual="<%= Sanitize.escapeHtml(medOriginal) %>"><option value="">Escolha a especialidade primeiro</option></select></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sHora">
        <div><h2 id="sHora">Data e horário</h2><p>Horários na grade do médico. Riscados: ocupados ou já passados.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-2"><label for="dataC">Data <span class="req">*</span></label>
                <input class="hc-input" type="date" id="dataC" name="data" min="<%= hojeF %>" value="<%= dataInicial %>" required></div>
            <div class="hc-field w-6"><span class="fw-medium" id="lblHorarios">Horários</span>
                <div id="gradeHorarios" class="hc-slots" role="group" aria-labelledby="lblHorarios"><span class="muted">Escolha o médico e a data.</span></div>
                <input type="hidden" name="hora" id="horaC"></div>
        </div>
    </section>

    <% if (remarcar) { %>
    <section class="hc-form-section" aria-labelledby="sMot">
        <div><h2 id="sMot">Motivo</h2><p>Opcional. Fica no registro da consulta cancelada, não vai no e-mail.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-6"><label for="motivoR">Motivo da remarcação</label>
                <input class="hc-input" id="motivoR" name="motivo" maxlength="200" placeholder="Ex.: pedido do paciente"></div>
        </div>
    </section>
    <% } %>

    <div class="hc-form-actions">
        <span id="resumo" class="muted me-auto" aria-live="polite"></span>
        <a class="hc-btn" href="agenda">Cancelar</a>
        <button class="hc-btn hc-btn-primary" type="submit" id="btnAgendar" disabled><%= remarcar ? "Confirmar remarcação" : "Confirmar agendamento" %></button>
    </div>
</form>
<script>
document.addEventListener("DOMContentLoaded", function () {
    const $esp = $('#esp'), $med = $('#med'), $data = $('#dataC'), $hora = $('#horaC'), $grade = $('#gradeHorarios');
    let paciente = null;

    function atualizarResumo() {
        const pronto = paciente && $med.val() && $hora.val();
        $('#btnAgendar').prop('disabled', !pronto);
        $('#resumo').text(pronto ? paciente.nome + ' · ' + $med.find(':selected').text() + ' · '
            + $data.val().split('-').reverse().join('/') + ' às ' + $hora.val() : '');
    }
    function carregar() {
        if (!$med.val() || !$data.val()) { return; }
        HC.carregarHorarios($grade, 'api/agenda/horarios', { medico: $med.val(), data: $data.val() }, $hora);
        atualizarResumo();
    }
    $('[data-hc-busca-paciente]').on('paciente:escolhido', function (e, p) { paciente = p; atualizarResumo(); });
    $esp.on('change', function () {
        $med.prop('disabled', true).empty().append($('<option value=""></option>').text('Carregando…'));
        if (!$esp.val()) { return; }
        ZTF.ajax.get('api/agenda/medicos', { especialidade: $esp.val() }).then(function (res) {
            $med.empty().append($('<option value=""></option>').text('Escolha…'));
            (res.data || []).forEach(function (m) {
                $('<option></option>').val(m.id_profissional).text(m.nome + ' · CRM ' + m.crm + '/' + m.crm_uf).appendTo($med);
            });
            $med.prop('disabled', false);
            const atual = String($med.data('atual') || '');
            if (atual && $med.find('option[value="' + atual + '"]').length) { $med.val(atual).trigger('change'); $med.data('atual', ''); }
        });
        $hora.val(''); atualizarResumo();
    });
    if ($esp.val()) { $esp.trigger('change'); }
    $med.on('change', carregar);
    $data.on('change', carregar);
    $hora.on('change', atualizarResumo);
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
