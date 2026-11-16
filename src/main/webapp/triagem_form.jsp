<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> con = (Map<String, Object>) request.getAttribute("consulta");
if (con == null) { response.sendRedirect(utils.Url.to(request, "triagem")); return; }
@SuppressWarnings("unchecked")
Map<String, Object> tri = (Map<String, Object>) request.getAttribute("triagem");
@SuppressWarnings("unchecked")
List<Map<String, Object>> classes = (List<Map<String, Object>>) request.getAttribute("classificacoes");
@SuppressWarnings("unchecked")
List<Map<String, Object>> alergias = (List<Map<String, Object>>) request.getAttribute("alergias");
boolean podeRegistrar = Boolean.TRUE.equals(request.getAttribute("podeRegistrar"));
boolean podeRetificar = Boolean.TRUE.equals(request.getAttribute("podeRetificar"));
boolean editavel = podeRegistrar || podeRetificar;
Map<String, Object> val = tri != null ? tri : new HashMap<String, Object>();
String tituloPagina = "Triagem · " + con.get("paciente");
Object nascObj = con.get("data_nascimento");
java.time.LocalDate nasc = nascObj instanceof java.sql.Date ? ((java.sql.Date) nascObj).toLocalDate()
        : (nascObj instanceof java.time.LocalDate ? (java.time.LocalDate) nascObj : null);
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    private static String v(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (o instanceof java.math.BigDecimal) { o = ((java.math.BigDecimal) o).stripTrailingZeros().toPlainString(); }
        return o == null ? "" : utils.Sanitize.escapeHtml(String.valueOf(o));
    }
%>
<div class="hc-breadcrumb"><a href="triagem">Fila de triagem</a><span aria-hidden="true">/</span><%= Sanitize.escapeHtml(con.get("paciente")) %></div>
<div class="hc-title-row">
    <div>
        <h1><%= Sanitize.escapeHtml(con.get("paciente")) %></h1>
        <p class="hc-subtitle"><%= nasc != null ? utils.Validacao.idade(nasc, java.time.LocalDate.now()) + " anos · " : "" %><%= Sanitize.escapeHtml(Rotulos.sexo(con.get("sexo"))) %>
            · consulta com <%= Sanitize.escapeHtml(con.get("medico")) %> (<%= Sanitize.escapeHtml(con.get("especialidade")) %>)
            · chegou às <%= Rotulos.hora(con.get("chegada_em")) %></p>
    </div>
    <div class="hc-actions">
        <% if (podeRegistrar) { %>
        <form data-hc-form method="POST" action="api/triagem/<%= con.get("id_consulta") %>/devolver" class="d-inline">
            <button class="hc-btn" type="submit">Devolver à fila</button>
        </form>
        <% } %>
    </div>
</div>

<div class="hc-alert <%= alergias.isEmpty() ? "note" : "err" %> mb-4" role="<%= alergias.isEmpty() ? "status" : "alert" %>">
    <% if (alergias.isEmpty()) { %>
        <strong>Alergias:</strong> nenhuma registrada. Pergunte ao paciente e informe o médico se houver.
    <% } else { %>
        <strong>Alergias:</strong>
        <% for (int i = 0; i < alergias.size(); i++) { Map<String, Object> a = alergias.get(i); %>
            <%= i > 0 ? " · " : "" %><%= Sanitize.escapeHtml(a.get("substancia")) %><%= a.get("gravidade") != null ? " (" + Sanitize.escapeHtml(a.get("gravidade")) + ")" : "" %>
        <% } %>
    <% } %>
</div>

<% if (tri != null) { %>
<p class="hc-help mb-3">Triagem registrada por <%= Sanitize.escapeHtml(tri.get("enfermeiro")) %> às <%= Rotulos.hora(tri.get("registrado_em")) %>
    <%= ((Number) tri.get("versoes")).intValue() > 1 ? " · versão " + tri.get("versoes") + " (retificada)" : "" %>.
    <%= podeRetificar ? "Para corrigir, altere os campos e informe o motivo: a versão anterior fica guardada." : "O atendimento terminou; a triagem não pode mais ser retificada." %></p>
<% } %>

<form data-hc-form method="POST" novalidate
      action="<%= tri == null ? "api/triagem/" + con.get("id_consulta") : "api/triagens/" + tri.get("id_triagem") + "/retificar" %>">
<fieldset <%= editavel ? "" : "disabled" %>>
    <section class="hc-form-section" aria-labelledby="sSinais">
        <div><h2 id="sSinais">Sinais vitais</h2><p>Preencha o que foi medido. Valores fora da faixa plausível são recusados.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-2"><label for="pa_sistolica">PA sistólica <span class="hc-unit">mmHg</span></label>
                <input class="hc-input num" id="pa_sistolica" name="pa_sistolica" inputmode="numeric" value="<%= v(val, "pa_sistolica") %>" placeholder="120">
                <span class="hc-error" data-erro-de="pa_sistolica"></span></div>
            <div class="hc-field w-2"><label for="pa_diastolica">PA diastólica <span class="hc-unit">mmHg</span></label>
                <input class="hc-input num" id="pa_diastolica" name="pa_diastolica" inputmode="numeric" value="<%= v(val, "pa_diastolica") %>" placeholder="80">
                <span class="hc-error" data-erro-de="pa_diastolica"></span></div>
            <div class="hc-field w-2"><label for="frequencia_cardiaca">Freq. cardíaca <span class="hc-unit">bpm</span></label>
                <input class="hc-input num" id="frequencia_cardiaca" name="frequencia_cardiaca" inputmode="numeric" value="<%= v(val, "frequencia_cardiaca") %>">
                <span class="hc-error" data-erro-de="frequencia_cardiaca"></span></div>
            <div class="hc-field w-2"><label for="temperatura">Temperatura <span class="hc-unit">°C</span></label>
                <input class="hc-input num" id="temperatura" name="temperatura" inputmode="decimal" value="<%= v(val, "temperatura") %>" placeholder="36,5">
                <span class="hc-error" data-erro-de="temperatura"></span></div>
            <div class="hc-field w-2"><label for="saturacao_o2">Saturação O₂ <span class="hc-unit">%</span></label>
                <input class="hc-input num" id="saturacao_o2" name="saturacao_o2" inputmode="numeric" value="<%= v(val, "saturacao_o2") %>">
                <span class="hc-error" data-erro-de="saturacao_o2"></span></div>
            <div class="hc-field w-1"><label for="peso_kg">Peso <span class="hc-unit">kg</span></label>
                <input class="hc-input num" id="peso_kg" name="peso_kg" inputmode="decimal" value="<%= v(val, "peso_kg") %>">
                <span class="hc-error" data-erro-de="peso_kg"></span></div>
            <div class="hc-field w-1"><label for="altura_cm">Altura <span class="hc-unit">cm</span></label>
                <input class="hc-input num" id="altura_cm" name="altura_cm" inputmode="numeric" value="<%= v(val, "altura_cm") %>">
                <span class="hc-error" data-erro-de="altura_cm"></span></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sQueixa">
        <div><h2 id="sQueixa">Queixa</h2><p>Nas palavras do paciente. O médico vê este texto na fila de atendimento.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-6"><label for="queixa_principal">Queixa principal <span class="req">*</span></label>
                <input class="hc-input" id="queixa_principal" name="queixa_principal" maxlength="500" value="<%= v(val, "queixa_principal") %>" required>
                <span class="hc-error" data-erro-de="queixa_principal"></span></div>
            <div class="hc-field w-3"><label for="sintomas">Sintomas</label>
                <textarea class="hc-input" id="sintomas" name="sintomas" rows="3"><%= v(val, "sintomas") %></textarea></div>
            <div class="hc-field w-3"><label for="observacoes">Observações</label>
                <textarea class="hc-input" id="observacoes" name="observacoes" rows="3"><%= v(val, "observacoes") %></textarea></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sPrio">
        <div><h2 id="sPrio">Prioridade</h2><p>Define a ordem na fila do médico. Vermelho é atendido primeiro.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-6" role="radiogroup" aria-labelledby="sPrio">
                <div class="hc-choices">
                <% for (Map<String, Object> p : classes) {
                       String idCl = String.valueOf(p.get("id_classificacao"));
                       int alvo = ((Number) p.get("tempo_alvo_min")).intValue();
                       boolean marcado = idCl.equals(String.valueOf(val.get("id_classificacao"))); %>
                    <label class="hc-choice">
                        <input type="radio" name="id_classificacao" value="<%= idCl %>"<%= marcado ? " checked" : "" %>>
                        <span class="hc-choice-swatch" style="background: <%= Sanitize.escapeHtml(p.get("cor_hex")) %>"></span>
                        <span><span class="hc-cell-main"><%= Sanitize.escapeHtml(p.get("nome")) %></span>
                            <span class="hc-cell-sub"><%= alvo == 0 ? "atendimento imediato" : "até " + alvo + " min" %></span></span>
                    </label>
                <% } %>
                </div>
                <span class="hc-error" data-erro-de="id_classificacao"></span>
            </div>
        </div>
    </section>

    <% if (tri != null && podeRetificar) { %>
    <section class="hc-form-section" aria-labelledby="sMotivo">
        <div><h2 id="sMotivo">Retificação</h2><p>Obrigatório para corrigir uma triagem já registrada.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-6"><label for="motivo_retificacao">Motivo <span class="req">*</span></label>
                <input class="hc-input" id="motivo_retificacao" name="motivo_retificacao" maxlength="255" required>
                <span class="hc-error" data-erro-de="motivo_retificacao"></span></div>
        </div>
    </section>
    <% } %>
</fieldset>

    <span class="hc-error d-block mb-2" data-erro-de="geral"></span>
    <div class="hc-form-actions">
        <a class="hc-btn" href="triagem">Voltar à fila</a>
        <% if (podeRegistrar) { %>
            <button class="hc-btn hc-btn-primary" type="submit">Registrar e enviar ao médico</button>
        <% } else if (podeRetificar) { %>
            <button class="hc-btn hc-btn-primary" type="submit">Salvar retificação</button>
        <% } %>
    </div>
</form>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
