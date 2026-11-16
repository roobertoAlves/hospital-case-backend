<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> prof = (Map<String, Object>) request.getAttribute("profissional");
if (prof == null) { response.sendRedirect(utils.Url.to(request, "profissionais")); return; }
@SuppressWarnings("unchecked")
List<Map<String, Object>> especialidades = (List<Map<String, Object>>) request.getAttribute("especialidades");
@SuppressWarnings("unchecked")
List<Map<String, Object>> usuarios = (List<Map<String, Object>>) request.getAttribute("usuarios");
boolean edicao = prof.get("id_profissional") != null;
String tipo = String.valueOf(prof.get("tipo"));
boolean medico = "medico".equals(tipo);
boolean enfermeiro = "enfermeiro".equals(tipo);
boolean ativo = !edicao || "1".equals(String.valueOf(prof.get("ativo")));
List<?> idsEsp = prof.get("ids_especialidades") instanceof List ? (List<?>) prof.get("ids_especialidades") : new ArrayList<Object>();
StringBuilder espCsv = new StringBuilder();
for (Object o : idsEsp) { if (espCsv.length() > 0) espCsv.append(','); espCsv.append(o); }
String rotuloTipo = medico ? "médico" : (enfermeiro ? "enfermeiro" : "funcionário");
String tituloPagina = edicao ? String.valueOf(prof.get("nome")) : "Cadastrar " + rotuloTipo;
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    private static String v(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? "" : utils.Sanitize.escapeHtml(String.valueOf(o));
    }
    private static final String[] UFS = {"AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"};
    private static String ufs(Object atual) {
        StringBuilder sb = new StringBuilder("<option value=\"\">UF</option>");
        for (String uf : UFS) { sb.append("<option").append(uf.equals(String.valueOf(atual)) ? " selected" : "").append(">").append(uf).append("</option>"); }
        return sb.toString();
    }
%>
<%
boolean podeDisponibilidade = layoutPermissoes.contains("agenda.disponibilidade.manage");
%>
<div class="hc-breadcrumb"><a href="profissionais">Profissionais</a><span aria-hidden="true">/</span> <%= edicao ? Sanitize.escapeHtml(prof.get("nome")) : "Novo" %></div>
<div class="hc-title-row">
    <div>
        <h1><%= edicao ? Sanitize.escapeHtml(prof.get("nome")) : "Cadastrar profissional" %></h1>
        <p class="hc-subtitle"><%= edicao ? (medico ? "Médico" : (enfermeiro ? "Enfermeiro" : "Funcionário")) + " · " + (ativo ? "ativo" : "inativo") : "Campos com <span class=\"text-danger\">*</span> são obrigatórios." %></p>
    </div>
    <% if (edicao) { %>
    <div class="hc-actions">
        <% if (ativo) { %>
        <button type="button" class="hc-btn hc-btn-danger" data-hc-acao="api/profissionais/<%= prof.get("id_profissional") %>/situacao" data-hc-dados='{"ativo":"0"}'>Inativar</button>
        <% } else { %>
        <button type="button" class="hc-btn" data-hc-acao="api/profissionais/<%= prof.get("id_profissional") %>/situacao" data-hc-dados='{"ativo":"1"}'>Reativar</button>
        <% } %>
    </div>
    <% } %>
</div>
<% if (edicao && !ativo) { %>
<div class="hc-alert note" role="status">Profissional inativo: não aparece no agendamento nem nas filas, e o acesso dele não abre telas clínicas.</div>
<% } %>

<form id="formProf" data-hc-form method="POST" action="<%= edicao ? "api/profissionais/" + prof.get("id_profissional") : "api/profissionais" %>" novalidate>
    <section class="hc-form-section" aria-labelledby="sTipo">
        <div><h2 id="sTipo">Tipo</h2><p><%= edicao ? "O tipo não muda depois do cadastro." : "Define o registro profissional e o perfil de acesso." %></p></div>
        <div>
            <% if (edicao) { %>
                <p class="mb-0"><strong><%= medico ? "Médico" : (enfermeiro ? "Enfermeiro" : "Funcionário") %></strong></p>
            <% } else { %>
            <div class="hc-choices" role="radiogroup" aria-label="Tipo de profissional">
                <a class="hc-choice<%= medico ? " is-on" : "" %>" href="profissionais/novo?tipo=medico" <%= medico ? "aria-current=\"true\"" : "" %>><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-stethoscope"/></svg>Médico</a>
                <a class="hc-choice<%= enfermeiro ? " is-on" : "" %>" href="profissionais/novo?tipo=enfermeiro" <%= enfermeiro ? "aria-current=\"true\"" : "" %>><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-pulse"/></svg>Enfermeiro</a>
                <a class="hc-choice<%= !medico && !enfermeiro ? " is-on" : "" %>" href="profissionais/novo?tipo=funcionario" <%= !medico && !enfermeiro ? "aria-current=\"true\"" : "" %>><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-id-card"/></svg>Funcionário (recepção)</a>
            </div>
            <input type="hidden" name="tipo" value="<%= Sanitize.escapeHtml(tipo) %>">
            <span class="hc-error" data-erro-de="tipo"></span>
            <% } %>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sIdent">
        <div><h2 id="sIdent">Identificação e contato</h2><p>Nome como aparece na agenda, nas filas e nas receitas.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-4"><label for="nome">Nome completo <span class="req">*</span></label>
                <input class="hc-input" id="nome" name="nome" value="<%= v(prof, "nome") %>" maxlength="150" autocomplete="off" required>
                <span class="hc-error" data-erro-de="nome"></span></div>
            <div class="hc-field w-2"><label for="telefone">Telefone</label>
                <input class="hc-input num" id="telefone" name="telefone" data-mask="telefone" inputmode="tel" value="<%= v(prof, "telefone") %>">
                <span class="hc-error" data-erro-de="telefone"></span></div>
            <div class="hc-field w-4"><label for="email">E-mail</label>
                <input class="hc-input" type="email" id="email" name="email" value="<%= v(prof, "email") %>" maxlength="150">
                <span class="hc-help">Obrigatório se for criar o acesso ao sistema.</span>
                <span class="hc-error" data-erro-de="email"></span></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sReg">
        <div><h2 id="sReg"><%= medico ? "Registro e especialidades" : (enfermeiro ? "Registro profissional" : "Cargo") %></h2>
            <p><%= medico ? "CRM e UF não se repetem. Só especialidades ativas aparecem." : (enfermeiro ? "COREN e UF não se repetem." : "Função do funcionário na unidade.") %></p></div>
        <div class="hc-fields">
        <% if (medico) { %>
            <div class="hc-field w-2"><label for="crm">CRM <span class="req">*</span></label>
                <input class="hc-input num" id="crm" name="crm" value="<%= v(prof, "crm") %>" maxlength="10" inputmode="numeric" required>
                <span class="hc-error" data-erro-de="crm"></span></div>
            <div class="hc-field w-1"><label for="crm_uf">UF <span class="req">*</span></label>
                <select class="hc-select" id="crm_uf" name="crm_uf" required><%= ufs(prof.get("crm_uf")) %></select>
                <span class="hc-error" data-erro-de="crm_uf"></span></div>
            <div class="hc-field w-2"><label for="duracao">Duração da consulta <span class="req">*</span></label>
                <select class="hc-select" id="duracao" name="duracao_consulta_min">
                <% int dur = prof.get("duracao_consulta_min") instanceof Number ? ((Number) prof.get("duracao_consulta_min")).intValue() : 30;
                   int[] opcoes = {15, 20, 30, 40, 45, 60};
                   boolean achou = false;
                   for (int o : opcoes) { if (o == dur) achou = true; %>
                    <option value="<%= o %>"<%= o == dur ? " selected" : "" %>><%= o %> minutos</option>
                <% } if (!achou) { %><option value="<%= dur %>" selected><%= dur %> minutos</option><% } %>
                </select>
                <span class="hc-help">Define a grade de horários do agendamento.</span>
                <span class="hc-error" data-erro-de="duracao_consulta_min"></span></div>
            <fieldset class="hc-field w-6"><legend class="fw-medium" style="font-size:inherit">Especialidades <span class="req">*</span></legend>
                <div class="hc-choices" id="listaEsp">
                <% for (Map<String, Object> e : especialidades) {
                       boolean marcada = false;
                       for (Object o : idsEsp) { if (String.valueOf(o).equals(String.valueOf(e.get("id_especialidade")))) marcada = true; } %>
                    <label class="hc-choice"><input type="checkbox" value="<%= e.get("id_especialidade") %>" <%= marcada ? "checked" : "" %>>
                        <span class="hc-check" aria-hidden="true"></span><%= Sanitize.escapeHtml(e.get("nome")) %></label>
                <% } %>
                </div>
                <input type="hidden" name="especialidades" id="espCsv" value="<%= espCsv %>">
                <span class="hc-help"><a href="especialidades">Gerenciar especialidades</a></span>
                <span class="hc-error" data-erro-de="especialidades"></span>
            </fieldset>
        <% } else if (enfermeiro) { %>
            <div class="hc-field w-2"><label for="coren">COREN <span class="req">*</span></label>
                <input class="hc-input num" id="coren" name="coren" value="<%= v(prof, "coren") %>" maxlength="12" required>
                <span class="hc-error" data-erro-de="coren"></span></div>
            <div class="hc-field w-1"><label for="coren_uf">UF <span class="req">*</span></label>
                <select class="hc-select" id="coren_uf" name="coren_uf" required><%= ufs(prof.get("coren_uf")) %></select>
                <span class="hc-error" data-erro-de="coren_uf"></span></div>
        <% } else { %>
            <div class="hc-field w-3"><label for="cargo">Cargo <span class="req">*</span></label>
                <input class="hc-input" id="cargo" name="cargo" value="<%= v(prof, "cargo") %>" maxlength="80" placeholder="Ex.: Recepcionista" required>
                <span class="hc-error" data-erro-de="cargo"></span></div>
        <% } %>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sAcesso">
        <div><h2 id="sAcesso">Acesso ao sistema</h2><p>Perfil <strong><%= medico ? "Médico" : (enfermeiro ? "Enfermagem" : "Recepção") %></strong>. Um usuário só se vincula a um profissional.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-4"><label for="id_usuario">Usuário vinculado</label>
                <select class="hc-select" id="id_usuario" name="id_usuario">
                    <option value="">Sem usuário vinculado</option>
                    <% for (Map<String, Object> u : usuarios) { %>
                    <option value="<%= u.get("id_usuario") %>"<%= String.valueOf(u.get("id_usuario")).equals(String.valueOf(prof.get("id_usuario"))) ? " selected" : "" %>><%= Sanitize.escapeHtml(u.get("nome")) %> · <%= Sanitize.escapeHtml(u.get("email")) %></option>
                    <% } %>
                </select>
                <span class="hc-help">Lista só usuários ativos ainda sem profissional. Os grupos do usuário se ajustam em <a href="admin">Usuários e permissões</a>.</span>
                <span class="hc-error" data-erro-de="id_usuario"></span></div>
            <% if (!edicao) { %>
            <div class="hc-field w-6">
                <label class="d-flex align-items-center gap-2" style="font-weight:400"><input type="checkbox" name="criar_acesso" value="1" id="criarAcesso">
                    Criar um acesso novo com o e-mail acima</label>
                <span class="hc-help">O usuário entra no grupo do perfil e recebe uma senha temporária por e-mail; no primeiro login, troca a senha. A senha não aparece nesta tela.</span>
            </div>
            <% } %>
        </div>
    </section>

    <div class="hc-form-actions">
        <a class="hc-btn" href="profissionais">Voltar</a>
        <button class="hc-btn hc-btn-primary" type="submit"><%= edicao ? "Salvar alterações" : "Cadastrar profissional" %></button>
    </div>
</form>

<% if (edicao && medico) {
       @SuppressWarnings("unchecked")
       List<Map<String, Object>> disp = (List<Map<String, Object>>) prof.get("disponibilidades");
       long futuras = prof.get("consultas_futuras") instanceof Number ? ((Number) prof.get("consultas_futuras")).longValue() : 0; %>
<section class="hc-panel mt-4" aria-labelledby="tDisp" id="disponibilidade">
    <div class="hc-panel-head"><h2 id="tDisp">Disponibilidade semanal</h2><span class="meta"><%= futuras %> <%= futuras == 1 ? "consulta agendada" : "consultas agendadas" %> daqui para frente</span></div>
    <p class="hc-help">O agendamento só oferece horários dentro destas faixas, na duração da consulta. Consultas já marcadas fora da nova grade continuam valendo.</p>
    <% if (podeDisponibilidade) { %>
    <form id="formDisp" novalidate>
        <div id="faixas"></div>
        <p class="hc-error" id="erroDisp" role="alert"></p>
        <div class="d-flex gap-2">
            <button type="button" class="hc-btn" id="addFaixa"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-plus"/></svg>Adicionar faixa</button>
            <button type="submit" class="hc-btn hc-btn-primary">Salvar disponibilidade</button>
        </div>
    </form>
    <script type="application/json" id="dispInicial"><%= new com.google.gson.Gson().toJson(disp).replace("</", "<\\/") %></script>
    <% } else { %>
        <% if (disp.isEmpty()) { %><p class="hc-empty">Sem faixas cadastradas.</p><% } %>
        <% for (Map<String, Object> f : disp) { %><div class="hc-row"><%= utils.Rotulos.diaDaSemana(f.get("dia_semana")) %> · <%= Sanitize.escapeHtml(f.get("hora_inicio")) %>–<%= Sanitize.escapeHtml(f.get("hora_fim")) %></div><% } %>
    <% } %>
</section>
<% } %>

<script>
document.addEventListener("DOMContentLoaded", function () {
    const $csv = $('#espCsv');
    $('#listaEsp').on('change', 'input[type=checkbox]', function () {
        $csv.val($('#listaEsp input:checked').map(function () { return this.value; }).get().join(','));
    });

    const $faixas = $('#faixas');
    if (!$faixas.length) { return; }
    const DIAS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
    function linha(f) {
        const $l = $('<div class="hc-rx-item hc-disp-item"></div>');
        const $dia = $('<select class="hc-select" aria-label="Dia da semana"></select>');
        DIAS.forEach(function (d, i) { $('<option></option>').val(i + 1).text(d).appendTo($dia); });
        $dia.val(String(f.dia_semana || 1));
        const $ini = $('<input class="hc-input" type="time" step="300" aria-label="Início">').val(f.hora_inicio || '08:00');
        const $fim = $('<input class="hc-input" type="time" step="300" aria-label="Fim">').val(f.hora_fim || '12:00');
        const $rm = $('<button type="button" class="hc-btn hc-btn-text hc-btn-danger" aria-label="Remover faixa">Remover</button>')
            .on('click', function () { $l.remove(); });
        $l.append($('<div class="hc-field"></div>').append($dia), $('<div class="hc-field"></div>').append($ini),
            $('<div class="hc-field"></div>').append($fim), $rm, $('<span class="hc-error w-100"></span>'));
        $faixas.append($l);
    }
    JSON.parse($('#dispInicial').text() || '[]').forEach(linha);
    if (!$faixas.children().length) { $('<p class="hc-empty" data-vazio></p>').text('Sem faixas: o médico não recebe agendamentos.').appendTo($faixas); }
    $('#addFaixa').on('click', function () { $faixas.find('[data-vazio]').remove(); linha({}); });
    $('#formDisp').on('submit', function (e) {
        e.preventDefault();
        $('#erroDisp').text('');
        $faixas.find('.hc-error').text('');
        const faixas = $faixas.find('.hc-disp-item').map(function () {
            const $c = $(this).find('select, input');
            return { dia_semana: $c.eq(0).val(), hora_inicio: $c.eq(1).val(), hora_fim: $c.eq(2).val() };
        }).get();
        ZTF.ajax.postJson('api/profissionais/<%= prof.get("id_profissional") %>/disponibilidade', { faixas: faixas })
            .then(function (res) { HC.aviso(res.message, true); })
            .catch(function (err) {
                HC.aviso(err.message || 'Não foi possível salvar.', false);
                Object.keys(err.details || {}).forEach(function (k) {
                    const i = parseInt(k.replace('faixa_', ''), 10);
                    $faixas.find('.hc-disp-item').eq(i).find('.hc-error').text(err.details[k]);
                });
            });
    });
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
