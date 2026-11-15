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
String tituloPagina = "Agendar consulta";
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
<div class="hc-breadcrumb"><a href="agenda">Agenda de consultas</a><span aria-hidden="true">/</span> Agendar</div>
<div class="hc-title-row">
    <div>
        <h1>Agendar consulta</h1>
        <p class="hc-subtitle">Escolha o paciente, a especialidade e o médico; depois um horário livre.</p>
    </div>
</div>

<form id="formConsulta" data-hc-form method="POST" action="api/consultas" novalidate>
    <section class="hc-form-section" aria-labelledby="sPac">
        <div><h2 id="sPac">Paciente</h2><p>Busque por nome ou CPF. Só pacientes ativos aparecem.</p></div>
        <div data-hc-busca-paciente <% if (!pacJson.isEmpty()) { %>data-paciente="<%= Sanitize.escapeHtml(pacJson) %>"<% } %>>
            <div class="hc-field w-6" style="max-width:520px">
                <label for="buscaPac">Buscar paciente</label>
                <input id="buscaPac" class="hc-input" data-busca type="search" autocomplete="off" placeholder="Nome ou CPF"
                       role="combobox" aria-expanded="false" aria-controls="listaPac">
                <ul id="listaPac" role="listbox" class="list-unstyled mb-0" hidden></ul>
                <input type="hidden" name="id_paciente" value="">
                <div data-escolhido class="hc-panel mt-2" hidden></div>
                <span class="hc-help">Não encontrou? <a href="pacientes/novo">Cadastrar paciente</a></span>
            </div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sMed">
        <div><h2 id="sMed">Especialidade e médico</h2><p>Só médicos ativos que atendem a especialidade.</p></div>
        <div class="hc-fields">
            <div class="hc-field"><label for="esp">Especialidade <span class="req">*</span></label>
                <select class="hc-select" id="esp" name="id_especialidade" required>
                    <option value="">Escolha…</option>
                    <% for (Map<String, Object> e : especialidades) { %>
                    <option value="<%= e.get("id_especialidade") %>"><%= Sanitize.escapeHtml(e.get("nome")) %></option>
                    <% } %>
                </select></div>
            <div class="hc-field"><label for="med">Médico <span class="req">*</span></label>
                <select class="hc-select" id="med" name="id_medico" required disabled><option value="">Escolha a especialidade primeiro</option></select></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sHora">
        <div><h2 id="sHora">Data e horário</h2><p>Horários na grade do médico. Riscados: ocupados ou já passados.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-2"><label for="dataC">Data <span class="req">*</span></label>
                <input class="hc-input" type="date" id="dataC" name="data" min="<%= hojeF %>" value="<%= hojeF %>" required></div>
            <div class="hc-field w-6"><span class="fw-medium" id="lblHorarios">Horários</span>
                <div id="gradeHorarios" class="hc-slots" role="group" aria-labelledby="lblHorarios"><span class="muted">Escolha o médico e a data.</span></div>
                <input type="hidden" name="hora" id="horaC"></div>
        </div>
    </section>

    <div class="hc-form-actions">
        <span id="resumo" class="muted me-auto" aria-live="polite"></span>
        <a class="hc-btn" href="agenda">Cancelar</a>
        <button class="hc-btn hc-btn-primary" type="submit" id="btnAgendar" disabled>Confirmar agendamento</button>
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
        });
        $hora.val(''); atualizarResumo();
    });
    $med.on('change', carregar);
    $data.on('change', carregar);
    $hora.on('change', atualizarResumo);
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
