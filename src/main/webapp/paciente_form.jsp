<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Validacao" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> pac = (Map<String, Object>) request.getAttribute("paciente");
if (pac == null) { pac = new HashMap<String, Object>(); }
boolean edicao = pac.get("id_paciente") != null;
String tituloPagina = edicao ? "Editar paciente" : "Cadastrar paciente";
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%!
    private static String v(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? "" : utils.Sanitize.escapeHtml(String.valueOf(o));
    }
    private static String sel(Map<String, Object> m, String k, String valor) {
        return valor.equals(String.valueOf(m.get(k))) ? " selected" : "";
    }
%>
<div class="hc-breadcrumb"><a href="pacientes">Pacientes</a><span aria-hidden="true">/</span>
    <%= edicao ? "<a href=\"pacientes/" + pac.get("id_paciente") + "\">" + Sanitize.escapeHtml(pac.get("nome")) + "</a><span aria-hidden=\"true\">/</span> Editar" : "Novo" %></div>
<div class="hc-title-row">
    <div>
        <h1><%= edicao ? "Editar cadastro" : "Cadastrar paciente" %></h1>
        <p class="hc-subtitle">Campos com <span class="text-danger">*</span> são obrigatórios. O CPF não pode se repetir.</p>
    </div>
</div>

<form data-hc-form method="POST" action="<%= edicao ? "api/pacientes/" + pac.get("id_paciente") : "api/pacientes" %>" novalidate>
    <section class="hc-form-section" aria-labelledby="sIdent">
        <div><h2 id="sIdent">Identificação</h2><p>Como o paciente aparece na agenda, nas filas e nos documentos.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-4"><label for="nome">Nome completo <span class="req">*</span></label>
                <input class="hc-input" id="nome" name="nome" value="<%= v(pac, "nome") %>" maxlength="150" autocomplete="off" required>
                <span class="hc-error" data-erro-de="nome"></span></div>
            <div class="hc-field w-2"><label for="cpf">CPF <span class="req">*</span></label>
                <input class="hc-input num" id="cpf" name="cpf" data-mask="cpf" inputmode="numeric" value="<%= pac.get("cpf") != null ? Validacao.formatarCpf(String.valueOf(pac.get("cpf"))) : "" %>" required>
                <span class="hc-error" data-erro-de="cpf"></span></div>
            <div class="hc-field w-2"><label for="data_nascimento">Data de nascimento <span class="req">*</span></label>
                <input class="hc-input" type="date" id="data_nascimento" name="data_nascimento" value="<%= v(pac, "data_nascimento") %>" required>
                <span class="hc-error" data-erro-de="data_nascimento"></span></div>
            <div class="hc-field w-2"><label for="sexo">Sexo</label>
                <select class="hc-select" id="sexo" name="sexo">
                    <option value="">Não informar</option>
                    <option value="feminino"<%= sel(pac, "sexo", "feminino") %>>Feminino</option>
                    <option value="masculino"<%= sel(pac, "sexo", "masculino") %>>Masculino</option>
                    <option value="outro"<%= sel(pac, "sexo", "outro") %>>Outro</option>
                    <option value="nao_informado"<%= sel(pac, "sexo", "nao_informado") %>>Prefere não informar</option>
                </select>
                <span class="hc-help">Só quando necessário ao atendimento.</span>
                <span class="hc-error" data-erro-de="sexo"></span></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sContato">
        <div><h2 id="sContato">Contato</h2><p>Usado para confirmar, remarcar e avisar sobre consultas e exames.</p></div>
        <div class="hc-fields">
            <div class="hc-field"><label for="telefone">Telefone</label>
                <input class="hc-input num" id="telefone" name="telefone" data-mask="telefone" inputmode="tel" value="<%= v(pac, "telefone") %>">
                <span class="hc-error" data-erro-de="telefone"></span></div>
            <div class="hc-field"><label for="email">E-mail</label>
                <input class="hc-input" type="email" id="email" name="email" value="<%= v(pac, "email") %>" maxlength="150">
                <span class="hc-help">Recebe os avisos de agendamento, sem dados clínicos.</span>
                <span class="hc-error" data-erro-de="email"></span></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sEnd">
        <div><h2 id="sEnd">Endereço</h2><p>Opcional.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-2"><label for="cep">CEP</label>
                <input class="hc-input num" id="cep" name="cep" data-mask="cep" inputmode="numeric" value="<%= v(pac, "cep") %>">
                <span class="hc-error" data-erro-de="cep"></span></div>
            <div class="hc-field w-4"><label for="logradouro">Logradouro</label>
                <input class="hc-input" id="logradouro" name="logradouro" value="<%= v(pac, "logradouro") %>" maxlength="150"></div>
            <div class="hc-field w-1"><label for="numero">Número</label>
                <input class="hc-input" id="numero" name="numero" value="<%= v(pac, "numero") %>" maxlength="10"></div>
            <div class="hc-field w-2"><label for="complemento">Complemento</label>
                <input class="hc-input" id="complemento" name="complemento" value="<%= v(pac, "complemento") %>" maxlength="60"></div>
            <div class="hc-field w-2"><label for="bairro">Bairro</label>
                <input class="hc-input" id="bairro" name="bairro" value="<%= v(pac, "bairro") %>" maxlength="80"></div>
            <div class="hc-field w-1"><label for="uf">UF</label>
                <input class="hc-input" id="uf" name="uf" value="<%= v(pac, "uf") %>" maxlength="2" style="text-transform:uppercase">
                <span class="hc-error" data-erro-de="uf"></span></div>
            <div class="hc-field w-6"><label for="cidade">Cidade</label>
                <input class="hc-input" id="cidade" name="cidade" value="<%= v(pac, "cidade") %>" maxlength="80"></div>
        </div>
    </section>

    <section class="hc-form-section" aria-labelledby="sEmerg">
        <div><h2 id="sEmerg">Contato de emergência</h2><p>Quem avisar se algo acontecer durante o atendimento.</p></div>
        <div class="hc-fields">
            <div class="hc-field w-2"><label for="contato_emergencia_nome">Nome</label>
                <input class="hc-input" id="contato_emergencia_nome" name="contato_emergencia_nome" value="<%= v(pac, "contato_emergencia_nome") %>" maxlength="150"></div>
            <div class="hc-field w-2"><label for="contato_emergencia_parentesco">Parentesco</label>
                <input class="hc-input" id="contato_emergencia_parentesco" name="contato_emergencia_parentesco" value="<%= v(pac, "contato_emergencia_parentesco") %>" maxlength="40"></div>
            <div class="hc-field w-2"><label for="contato_emergencia_telefone">Telefone</label>
                <input class="hc-input num" id="contato_emergencia_telefone" name="contato_emergencia_telefone" data-mask="telefone" inputmode="tel" value="<%= v(pac, "contato_emergencia_telefone") %>">
                <span class="hc-error" data-erro-de="contato_emergencia_telefone"></span></div>
        </div>
    </section>

    <div class="hc-form-actions">
        <a class="hc-btn" href="<%= edicao ? "pacientes/" + pac.get("id_paciente") : "pacientes" %>">Cancelar</a>
        <button class="hc-btn hc-btn-primary" type="submit"><%= edicao ? "Salvar alterações" : "Cadastrar paciente" %></button>
    </div>
</form>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
