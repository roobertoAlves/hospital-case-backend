<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Validacao" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
List<Map<String, Object>> tipos = (List<Map<String, Object>>) request.getAttribute("tipos");
if (tipos == null) { response.sendRedirect(utils.Url.to(request, "exames/novo")); return; }
@SuppressWarnings("unchecked")
Map<String, Object> pacPre = (Map<String, Object>) request.getAttribute("paciente");
java.time.LocalDate hojeF = (java.time.LocalDate) request.getAttribute("hoje");
String tituloPagina = "Agendar exame";
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
<div class="hc-breadcrumb"><a href="exames">Agenda de exames</a><span aria-hidden="true">/</span> Agendar</div>
<div class="hc-title-row">
    <div>
        <h1>Agendar exame</h1>
        <p class="hc-subtitle">O local e a duração vêm do tipo de exame. Entregue as orientações de preparo ao paciente.</p>
    </div>
</div>

<form data-hc-form method="POST" action="api/exames-agendados" novalidate>
    <section class="hc-form-section" aria-labelledby="sPacE">
        <div><h2 id="sPacE">Paciente</h2><p>Busque por nome ou CPF. Só pacientes ativos aparecem.</p></div>
        <div data-hc-busca-paciente <% if (!pacJson.isEmpty()) { %>data-paciente="<%= Sanitize.escapeHtml(pacJson) %>"<% } %>>
            <div class="hc-field w-6" style="max-width:520px">
                <label for="buscaPacE">Buscar paciente</label>
                <input id="buscaPacE" class="hc-input" data-busca type="search" autocomplete="off" placeholder="Nome ou CPF"
                       role="combobox" aria-expanded="false" aria-controls="listaPacE">
                <ul id="listaPacE" role="listbox" class="list-unstyled mb-0" hidden></ul>
                <input type="hidden" name="id_paciente" value="">
                <div data-escolhido class="hc-panel mt-2" hidden></div>
                <span class="hc-help">Não encontrou? <a href="pacientes/novo">Cadastrar paciente</a></span>
            </div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sTipo">
        <div><h2 id="sTipo">Exame</h2><p>Duração, local e preparo de cada tipo.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-4"><label for="tipo">Tipo de exame <span class="req">*</span></label>
                <select class="hc-select" id="tipo" name="id_tipo_exame" required>
                    <option value="">Escolha…</option>
                    <% for (Map<String, Object> t : tipos) { %>
                    <option value="<%= t.get("id_tipo_exame") %>" data-local="<%= Sanitize.escapeHtml(t.get("local")) %>"
                            data-duracao="<%= t.get("duracao_min") %>" data-preparo="<%= Sanitize.escapeHtml(t.get("preparo")) %>"><%= Sanitize.escapeHtml(t.get("nome")) %></option>
                    <% } %>
                </select></div>
            <div class="hc-field w-6" id="infoTipo" hidden>
                <dl class="hc-dl">
                    <dt>Local</dt><dd id="infoLocal"></dd>
                    <dt>Duração</dt><dd id="infoDuracao" class="num"></dd>
                    <dt>Preparo</dt><dd id="infoPreparo"></dd>
                </dl>
            </div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sHoraE">
        <div><h2 id="sHoraE">Data e horário</h2><p>Horários do local do exame. Riscados: ocupados ou já passados.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-2"><label for="dataE">Data <span class="req">*</span></label>
                <input class="hc-input" type="date" id="dataE" name="data" min="<%= hojeF %>" value="<%= hojeF %>" required></div>
            <div class="hc-field w-6"><span class="fw-medium" id="lblHorE">Horários</span>
                <div id="gradeE" class="hc-slots" role="group" aria-labelledby="lblHorE"><span class="muted">Escolha o exame e a data.</span></div>
                <input type="hidden" name="hora" id="horaE"></div>
        </div>
    </section>

    <div class="hc-form-actions">
        <span id="resumoE" class="muted me-auto" aria-live="polite"></span>
        <a class="hc-btn" href="exames">Cancelar</a>
        <button class="hc-btn hc-btn-primary" type="submit" id="btnAgendarE" disabled>Confirmar agendamento</button>
    </div>
</form>
<script>
document.addEventListener("DOMContentLoaded", function () {
    const $tipo = $('#tipo'), $data = $('#dataE'), $hora = $('#horaE'), $grade = $('#gradeE');
    let paciente = null;
    function resumo() {
        const ok = paciente && $tipo.val() && $hora.val();
        $('#btnAgendarE').prop('disabled', !ok);
        $('#resumoE').text(ok ? paciente.nome + ' · ' + $tipo.find(':selected').text() + ' · '
            + $data.val().split('-').reverse().join('/') + ' às ' + $hora.val() : '');
    }
    function carregar() {
        if ($tipo.val() && $data.val()) { HC.carregarHorarios($grade, 'api/exames/horarios', { tipo: $tipo.val(), data: $data.val() }, $hora); }
        resumo();
    }
    $('[data-hc-busca-paciente]').on('paciente:escolhido', function (e, p) { paciente = p; resumo(); });
    $tipo.on('change', function () {
        const $o = $tipo.find(':selected');
        $('#infoTipo').prop('hidden', !$tipo.val());
        $('#infoLocal').text($o.data('local') || '');
        $('#infoDuracao').text(($o.data('duracao') || '') + ' min');
        $('#infoPreparo').text($o.data('preparo') || 'Sem preparo especial.');
        carregar();
    });
    $data.on('change', carregar);
    $hora.on('change', resumo);
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
