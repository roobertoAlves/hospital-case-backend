<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> c = (Map<String, Object>) request.getAttribute("consulta");
if (c == null) { response.sendRedirect(utils.Url.to(request, "atendimento")); return; }
@SuppressWarnings("unchecked") Map<String, Object> tri = (Map<String, Object>) request.getAttribute("triagem");
@SuppressWarnings("unchecked") List<Map<String, Object>> alergias = (List<Map<String, Object>>) request.getAttribute("alergias");
@SuppressWarnings("unchecked") List<Map<String, Object>> evolucoes = (List<Map<String, Object>>) request.getAttribute("evolucoes");
@SuppressWarnings("unchecked") List<Map<String, Object>> receitas = (List<Map<String, Object>>) request.getAttribute("receitas");
@SuppressWarnings("unchecked") List<Map<String, Object>> exames = (List<Map<String, Object>>) request.getAttribute("exames");
@SuppressWarnings("unchecked") List<Map<String, Object>> anteriores = (List<Map<String, Object>>) request.getAttribute("anteriores");
int versoes = request.getAttribute("versoes") instanceof Integer ? (Integer) request.getAttribute("versoes") : 0;
String status = String.valueOf(c.get("status"));
boolean aguardando = "aguardando_atendimento".equals(status);
boolean emAtendimento = "em_atendimento".equals(status);
boolean finalizado = "finalizado".equals(c.get("status_prontuario"));
boolean rascunho = emAtendimento && "rascunho".equals(c.get("status_prontuario"));
boolean podePrescrever = emAtendimento || "realizada".equals(status);
Object idC = c.get("id_consulta");
String tituloPagina = "Atendimento · " + c.get("paciente");
Object nascObj = c.get("data_nascimento");
java.time.LocalDate nasc = nascObj instanceof java.sql.Date ? ((java.sql.Date) nascObj).toLocalDate()
        : (nascObj instanceof java.time.LocalDate ? (java.time.LocalDate) nascObj : null);
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    private static String v(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k);
        if (o instanceof java.math.BigDecimal) { o = ((java.math.BigDecimal) o).stripTrailingZeros().toPlainString(); }
        return o == null ? "" : utils.Sanitize.escapeHtml(String.valueOf(o));
    }
    private static String sinal(Map<String, Object> m, String k, String unidade) {
        String s = v(m, k);
        return s.isEmpty() ? "—" : s + " " + unidade;
    }
    private static String campo(String nome, String rotulo, String ajuda, Map<String, Object> m, boolean editavel, int linhas) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"hc-field w-6\"><label for=\"").append(nome).append("\">").append(rotulo)
          .append(nome.equals("conduta") && editavel ? " <span class=\"req\">*</span>" : "").append("</label>");
        if (editavel) {
            sb.append("<textarea class=\"hc-input\" id=\"").append(nome).append("\" name=\"").append(nome)
              .append("\" rows=\"").append(linhas).append("\">").append(v(m, nome)).append("</textarea>");
            if (ajuda != null) { sb.append("<span class=\"hc-help\">").append(ajuda).append("</span>"); }
        } else {
            String val = v(m, nome);
            sb.append("<div class=\"hc-note-block\" id=\"").append(nome).append("\">").append(val.isEmpty() ? "<span class=\"muted\">—</span>" : val).append("</div>");
        }
        return sb.append("</div>").toString();
    }
%>
<div class="hc-breadcrumb"><a href="atendimento">Fila de atendimento</a><span aria-hidden="true">/</span><%= Sanitize.escapeHtml(c.get("paciente")) %></div>
<div class="hc-title-row">
    <div>
        <h1><%= Sanitize.escapeHtml(c.get("paciente")) %></h1>
        <p class="hc-subtitle"><%= nasc != null ? utils.Validacao.idade(nasc, java.time.LocalDate.now()) + " anos · " : "" %><%= Sanitize.escapeHtml(Rotulos.sexo(c.get("sexo"))) %>
            · <%= Sanitize.escapeHtml(c.get("especialidade")) %> · consulta das <%= Rotulos.hora(c.get("data_hora")) %>
            · <%= Rotulos.pilulaConsulta(status) %></p>
    </div>
    <div class="hc-actions">
        <a class="hc-btn" href="pacientes/<%= c.get("id_paciente") %>/historico"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-file-text"/></svg>Histórico</a>
        <% if (aguardando) { %>
        <form data-hc-form method="POST" action="api/atendimento/<%= idC %>/iniciar" class="d-inline">
            <button class="hc-btn hc-btn-primary" type="submit">Iniciar atendimento</button>
        </form>
        <% } %>
    </div>
</div>

<div class="hc-alert <%= alergias.isEmpty() ? "note" : "err" %>" role="<%= alergias.isEmpty() ? "status" : "alert" %>">
    <% if (alergias.isEmpty()) { %>
        <strong>Alergias:</strong> nenhuma registrada.
    <% } else { %>
        <strong>Alergias:</strong>
        <% for (int i = 0; i < alergias.size(); i++) { Map<String, Object> a = alergias.get(i); %>
            <%= i > 0 ? " · " : "" %><%= Sanitize.escapeHtml(a.get("substancia")) %><%= a.get("gravidade") != null ? " (" + Sanitize.escapeHtml(a.get("gravidade")) + ")" : "" %><%= a.get("reacao") != null ? ": " + Sanitize.escapeHtml(a.get("reacao")) : "" %>
        <% } %>
    <% } %>
</div>

<div class="hc-grid">
    <%-- ------------------------------------------------------------ prontuario --%>
    <section class="hc-panel span-8" aria-labelledby="tPront">
        <div class="hc-panel-head"><h2 id="tPront">Prontuário</h2>
            <span class="meta"><%= finalizado ? "finalizado em " + Rotulos.dataHora(c.get("finalizado_em")) + " · não pode ser alterado"
                    : (c.get("id_prontuario") != null ? "rascunho · versão " + c.get("versao") + (versoes > 0 ? " · " + versoes + " anteriores guardadas" : "") : "inicie o atendimento para registrar") %></span></div>

        <% if (c.get("id_prontuario") == null) { %>
            <p class="hc-empty">O prontuário é aberto quando você inicia o atendimento. A queixa da triagem já vem preenchida.</p>
        <% } else { %>
        <form id="formProntuario" data-hc-form method="POST" action="api/atendimento/<%= idC %>/prontuario" novalidate>
            <div class="hc-fields">
                <%= campo("queixa_principal", "Queixa principal", null, c, rascunho, 2) %>
                <%= campo("historia_doenca_atual", "História da doença atual", null, c, rascunho, 4) %>
                <%= campo("historico_relevante", "Antecedentes relevantes", "Doenças prévias, cirurgias, medicamentos em uso.", c, rascunho, 3) %>
                <%= campo("observacoes", "Exame físico e observações", null, c, rascunho, 3) %>
                <%= campo("hipoteses_diagnosticas", "Hipóteses diagnósticas", null, c, rascunho, 2) %>
                <%= campo("conduta", "Conduta", "Obrigatória para finalizar.", c, rascunho, 3) %>
            </div>
            <% if (rascunho) { %>
            <div class="hc-form-actions">
                <button class="hc-btn" type="submit">Salvar rascunho</button>
                <button class="hc-btn hc-btn-primary" type="button" id="btnFinalizar">Finalizar atendimento</button>
            </div>
            <% } %>
        </form>
        <% } %>

        <% if (c.get("id_prontuario") != null) { %>
        <div class="hc-panel-head mt-4"><h2 id="tEvol">Evoluções e adendos</h2><span class="meta">só inclusão · não podem ser editados</span></div>
        <% if (evolucoes.isEmpty()) { %>
            <p class="hc-help">Nenhum registro.</p>
        <% } else { %>
            <div>
            <% for (Map<String, Object> e : evolucoes) { %>
                <div class="hc-entry"><div class="hc-entry-meta"><%= "adendo".equals(e.get("tipo")) ? "Adendo" : "Evolução" %> · <%= Sanitize.escapeHtml(e.get("autor")) %> · <%= Rotulos.dataHora(e.get("registrado_em")) %></div>
                    <div class="hc-note-block"><%= Sanitize.escapeHtml(e.get("texto")) %></div></div>
            <% } %>
            </div>
        <% } %>
        <% if (emAtendimento || finalizado) { %>
        <form data-hc-form method="POST" action="api/atendimento/<%= idC %>/evolucoes" class="mt-3" novalidate>
            <input type="hidden" name="tipo" value="<%= finalizado ? "adendo" : "evolucao" %>">
            <div class="hc-field w-6"><label for="texto"><%= finalizado ? "Novo adendo" : "Nova evolução" %></label>
                <textarea class="hc-input" id="texto" name="texto" rows="2"></textarea></div>
            <div class="hc-form-actions"><button class="hc-btn" type="submit"><%= finalizado ? "Incluir adendo" : "Incluir evolução" %></button></div>
        </form>
        <% } %>
        <% } %>
    </section>

    <%-- ------------------------------------------------------------ coluna lateral --%>
    <div class="span-4">
        <section class="hc-panel mb-3" aria-labelledby="tTri">
            <div class="hc-panel-head"><h2 id="tTri">Triagem</h2>
                <% if (tri != null) { %><span class="hc-priority"><span class="hc-dot" style="background: <%= Sanitize.escapeHtml(tri.get("cor_hex")) %>"></span><%= Sanitize.escapeHtml(tri.get("prioridade")) %></span><% } %></div>
            <% if (tri == null) { %>
                <p class="hc-empty">Sem triagem registrada.</p>
            <% } else { %>
            <dl class="hc-vitals">
                <dt>Pressão</dt><dd><%= tri.get("pa_sistolica") != null ? v(tri, "pa_sistolica") + "/" + v(tri, "pa_diastolica") + " mmHg" : "—" %></dd>
                <dt>Freq. cardíaca</dt><dd><%= sinal(tri, "frequencia_cardiaca", "bpm") %></dd>
                <dt>Temperatura</dt><dd><%= sinal(tri, "temperatura", "°C") %></dd>
                <dt>Saturação O₂</dt><dd><%= sinal(tri, "saturacao_o2", "%") %></dd>
                <dt>Peso</dt><dd><%= sinal(tri, "peso_kg", "kg") %></dd>
                <dt>Altura</dt><dd><%= sinal(tri, "altura_cm", "cm") %></dd>
            </dl>
            <p class="mt-3 mb-1"><strong>Queixa:</strong> <%= Sanitize.escapeHtml(tri.get("queixa_principal")) %></p>
            <% if (tri.get("sintomas") != null) { %><p class="mb-1 hc-note-block"><strong>Sintomas:</strong> <%= Sanitize.escapeHtml(tri.get("sintomas")) %></p><% } %>
            <p class="hc-help mb-0"><%= Sanitize.escapeHtml(tri.get("enfermeiro")) %> · <%= Rotulos.hora(tri.get("registrado_em")) %><%= ((Number) tri.get("versoes")).intValue() > 1 ? " · retificada" : "" %></p>
            <% } %>
        </section>

        <% if (emAtendimento) { %>
        <section class="hc-panel mb-3" aria-labelledby="tAlerg">
            <div class="hc-panel-head"><h2 id="tAlerg">Registrar alergia</h2></div>
            <form data-hc-form method="POST" action="api/atendimento/<%= idC %>/alergias" novalidate>
                <div class="hc-fields">
                    <div class="hc-field w-6"><label for="substancia">Substância</label><input class="hc-input" id="substancia" name="substancia" maxlength="150"></div>
                    <div class="hc-field w-6"><label for="reacao">Reação</label><input class="hc-input" id="reacao" name="reacao" maxlength="255"></div>
                    <div class="hc-field w-6"><label for="gravidade">Gravidade</label>
                        <select class="hc-select" id="gravidade" name="gravidade"><option value="">Não informada</option><option value="leve">Leve</option><option value="moderada">Moderada</option><option value="grave">Grave</option></select></div>
                </div>
                <div class="hc-form-actions"><button class="hc-btn" type="submit">Registrar alergia</button></div>
            </form>
        </section>
        <% } %>

        <section class="hc-panel" aria-labelledby="tAnt">
            <div class="hc-panel-head"><h2 id="tAnt">Atendimentos anteriores</h2></div>
            <% if (anteriores.isEmpty()) { %>
                <p class="hc-help mb-0">Primeiro atendimento registrado deste paciente.</p>
            <% } else { %>
            <% for (Map<String, Object> p : anteriores) { %>
                <div class="hc-entry"><div class="hc-entry-meta"><%= Rotulos.data(p.get("data_hora")) %> · <%= Sanitize.escapeHtml(p.get("medico")) %> · <%= Sanitize.escapeHtml(p.get("especialidade")) %></div>
                    <div><%= Rotulos.ou(p.get("hipoteses_diagnosticas")) %></div></div>
            <% } %>
            <% } %>
        </section>
    </div>

    <%-- ------------------------------------------------------------ receitas --%>
    <section class="hc-panel span-7" aria-labelledby="tRec">
        <div class="hc-panel-head"><h2 id="tRec">Receitas</h2><span class="meta">emitida não se edita; cancele e emita outra</span></div>
        <% for (Map<String, Object> r : receitas) { boolean cancelada = "cancelada".equals(r.get("status")); %>
            <div class="hc-entry">
                <div class="hc-entry-meta d-flex justify-content-between gap-2 flex-wrap">
                    <span>Receita nº <%= r.get("id_receita") %> · <%= Rotulos.dataHora(r.get("emitida_em")) %><%= cancelada ? " · <strong>cancelada</strong>" : "" %></span>
                    <% if (!cancelada) { %><span>
                        <a class="hc-btn hc-btn-sm" href="receitas/<%= r.get("id_receita") %>" target="_blank" rel="noopener">Imprimir</a>
                        <button type="button" class="hc-btn hc-btn-sm hc-btn-text hc-btn-danger" data-hc-acao="api/receitas/<%= r.get("id_receita") %>/cancelar"
                                data-hc-motivo="1" data-hc-alvo="a receita nº <%= r.get("id_receita") %>" data-hc-ajuda="A receita cancelada continua no histórico, marcada como cancelada.">Cancelar</button></span><% } %>
                </div>
                <ol class="mb-0 ps-3<%= cancelada ? " text-decoration-line-through muted" : "" %>">
                <% for (Object o : (List<?>) r.get("itens")) { @SuppressWarnings("unchecked") Map<String, Object> i = (Map<String, Object>) o; %>
                    <li><strong><%= Sanitize.escapeHtml(i.get("medicamento")) %></strong> <%= Sanitize.escapeHtml(i.get("dosagem")) %>
                        <%= i.get("via") != null ? "· " + Sanitize.escapeHtml(i.get("via")) : "" %> · <%= Sanitize.escapeHtml(i.get("frequencia")) %> · <%= Sanitize.escapeHtml(i.get("duracao")) %></li>
                <% } %>
                </ol>
            </div>
        <% } %>
        <% if (podePrescrever) { %>
        <form id="formReceita" class="mt-3" novalidate>
            <h3 class="hc-section-title">Nova receita</h3>
            <div id="rxItens"></div>
            <button type="button" class="hc-btn hc-btn-sm hc-btn-text" id="rxMais"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-plus"/></svg>Outro medicamento</button>
            <div class="hc-field w-6 mt-2"><label for="orientacoes_gerais">Orientações gerais</label>
                <textarea class="hc-input" id="orientacoes_gerais" name="orientacoes_gerais" rows="2"></textarea></div>
            <span class="hc-error d-block" data-erro-de="itens"></span>
            <div class="hc-form-actions"><button class="hc-btn hc-btn-primary" type="submit">Emitir receita</button></div>
        </form>
        <% } else if (receitas.isEmpty()) { %>
            <p class="hc-help mb-0">Receitas são emitidas durante o atendimento.</p>
        <% } %>
    </section>

    <%-- ------------------------------------------------------------ exames --%>
    <section class="hc-panel span-5" aria-labelledby="tExa">
        <div class="hc-panel-head"><h2 id="tExa">Exames solicitados</h2><span class="meta">a recepção agenda</span></div>
        <% for (Map<String, Object> e : exames) { boolean cancelado = "cancelado".equals(e.get("status")); %>
            <div class="hc-row">
                <span class="grow"><span class="hc-cell-main<%= cancelado ? " text-decoration-line-through" : "" %>"><%= Sanitize.escapeHtml(e.get("nome_exame")) %></span>
                    <span class="hc-cell-sub"><%= Rotulos.ou(e.get("justificativa")) %><%= cancelado ? " · cancelado" : "" %></span></span>
                <% if (!cancelado) { %>
                <button type="button" class="hc-btn hc-btn-sm hc-btn-text hc-btn-danger" data-hc-acao="api/solicitacoes-exame/<%= e.get("id_exame") %>/cancelar"
                        data-hc-motivo="1" data-hc-alvo="o pedido de <%= Sanitize.escapeHtml(e.get("nome_exame")) %>" data-hc-ajuda="O pedido cancelado continua no histórico.">Cancelar</button>
                <% } %>
            </div>
        <% } %>
        <% if (podePrescrever) { %>
        <form data-hc-form method="POST" action="api/atendimento/<%= idC %>/exames" class="mt-3" novalidate>
            <div class="hc-fields">
                <div class="hc-field w-4"><label for="nome_exame">Exame</label><input class="hc-input" id="nome_exame" name="nome_exame" maxlength="200" list="examesComuns"></div>
                <div class="hc-field w-2"><label for="codigo">Código</label><input class="hc-input" id="codigo" name="codigo" maxlength="20" placeholder="TUSS"></div>
                <div class="hc-field w-6"><label for="justificativa">Justificativa</label><input class="hc-input" id="justificativa" name="justificativa" maxlength="500"></div>
            </div>
            <datalist id="examesComuns"><option value="Hemograma completo"><option value="Glicemia de jejum"><option value="Hemoglobina glicada">
                <option value="Colesterol total e frações"><option value="Creatinina"><option value="TSH"><option value="Urina tipo 1">
                <option value="Raio-X de tórax"><option value="Eletrocardiograma"><option value="Ultrassonografia de abdome total"></datalist>
            <div class="hc-form-actions"><button class="hc-btn" type="submit">Solicitar exame</button></div>
        </form>
        <% } else if (exames.isEmpty()) { %>
            <p class="hc-help mb-0">Nenhum exame solicitado.</p>
        <% } %>
    </section>
</div>

<template id="tplRxItem">
    <div class="hc-rx-item">
        <div class="hc-field"><label>Medicamento</label><input class="hc-input" data-k="medicamento" maxlength="200"></div>
        <div class="hc-field"><label>Dosagem</label><input class="hc-input" data-k="dosagem" maxlength="100" placeholder="500 mg"></div>
        <div class="hc-field"><label>Via</label><input class="hc-input" data-k="via" maxlength="50" placeholder="oral"></div>
        <div class="hc-field"><label>Frequência</label><input class="hc-input" data-k="frequencia" maxlength="100" placeholder="8 em 8 horas"></div>
        <div class="hc-field"><label>Duração</label><input class="hc-input" data-k="duracao" maxlength="100" placeholder="7 dias"></div>
        <button type="button" class="hc-btn hc-btn-sm hc-btn-text" data-rx-remover aria-label="Remover medicamento"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-x"/></svg></button>
    </div>
</template>

<script>
document.addEventListener('DOMContentLoaded', function () {
    var idConsulta = '<%= idC %>';
    var $fin = $('#btnFinalizar');
    $fin.on('click', function () {
        if (!window.confirm('Finalizar o atendimento? Depois disso o prontuário não pode ser alterado, só complementado com adendos.')) { return; }
        $fin.prop('disabled', true);
        ZTF.ajax.submitForm('#formProntuario', 'api/atendimento/' + idConsulta + '/finalizar')
            .then(function (res) { HC.aviso(res.message, true); setTimeout(function () { window.location.href = res.data.redirect; }, 600); })
            .catch(function (err) { HC.aviso(err.message || 'Não foi possível finalizar.', false); $fin.prop('disabled', false); });
    });

    var $itens = $('#rxItens');
    if (!$itens.length) { return; }
    function novoItem() {
        $itens.append(document.getElementById('tplRxItem').content.cloneNode(true));
        $itens.find('.hc-rx-item').last().find('input').first().trigger('focus');
    }
    novoItem();
    $('#rxMais').on('click', novoItem);
    $itens.on('click', '[data-rx-remover]', function () {
        if ($itens.children().length > 1) { $(this).closest('.hc-rx-item').remove(); }
    });
    $('#formReceita').on('submit', function (e) {
        e.preventDefault();
        var itens = [];
        $itens.children().each(function () {
            var item = {};
            $(this).find('[data-k]').each(function () { item[$(this).data('k')] = $(this).val().trim(); });
            if (item.medicamento || item.dosagem) { itens.push(item); }
        });
        var $btn = $(this).find('[type="submit"]').prop('disabled', true);
        $('[data-erro-de="itens"]').text('');
        ZTF.ajax.postJson('api/atendimento/' + idConsulta + '/receitas', { orientacoes_gerais: $('#orientacoes_gerais').val(), itens: itens })
            .then(function (res) {
                HC.aviso(res.message, true);
                window.open('receitas/' + res.data.id_receita, '_blank', 'noopener');
                setTimeout(function () { window.location.reload(); }, 700);
            })
            .catch(function (err) {
                $('[data-erro-de="itens"]').text((err.details && err.details.itens) || err.message || '');
                HC.aviso(err.message || 'Não foi possível emitir.', false);
                $btn.prop('disabled', false);
            });
    });
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
