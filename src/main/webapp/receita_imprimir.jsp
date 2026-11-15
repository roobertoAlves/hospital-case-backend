<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*, utils.Router, utils.Sanitize, utils.Rotulos" %>
<%
if (!Router.checkJspAccess(request, response)) return;
@SuppressWarnings("unchecked")
Map<String, Object> r = (Map<String, Object>) request.getAttribute("receita");
if (r == null) { response.sendRedirect(utils.Url.to(request, "atendimento")); return; }
boolean cancelada = "cancelada".equals(r.get("status"));
Object nascObj = r.get("data_nascimento");
java.time.LocalDate nasc = nascObj instanceof java.sql.Date ? ((java.sql.Date) nascObj).toLocalDate()
        : (nascObj instanceof java.time.LocalDate ? (java.time.LocalDate) nascObj : null);
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <base href="<%= Sanitize.escapeHtml(utils.Url.base(request)) %>">
    <title>Receita nº <%= r.get("id_receita") %></title>
    <style>
        @font-face { font-family: "IBM Plex Sans"; font-weight: 400; src: url("assets/fonts/ibm-plex-sans-latin-400-normal.woff2") format("woff2"); }
        @font-face { font-family: "IBM Plex Sans"; font-weight: 600; src: url("assets/fonts/ibm-plex-sans-latin-600-normal.woff2") format("woff2"); }
        :root { --ink: #131416; --muted: #55595c; --rule: #d9dde0; --accent: #1b6c80; }
        * { box-sizing: border-box; }
        body { margin: 0; background: #eef3f4; color: var(--ink); font: 15px/1.5 "IBM Plex Sans", system-ui, sans-serif; }
        .folha { max-width: 760px; margin: 24px auto; background: #fff; padding: 48px 56px; border-radius: 12px; }
        header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid var(--ink); padding-bottom: 16px; }
        header h1 { margin: 0; font-size: 22px; letter-spacing: -.01em; }
        header p { margin: 2px 0 0; color: var(--muted); font-size: 13px; }
        .num { font-variant-numeric: tabular-nums; text-align: right; font-size: 13px; color: var(--muted); }
        .pac { display: grid; grid-template-columns: 1fr auto; gap: 4px 24px; margin: 20px 0 24px; }
        .pac dt { font-size: 12px; color: var(--muted); }
        .pac dd { margin: 0 0 6px; font-weight: 500; }
        ol { padding-left: 22px; margin: 0; }
        li { padding: 10px 0; border-bottom: 1px solid var(--rule); }
        li strong { font-size: 16px; }
        li .uso { display: block; color: var(--muted); }
        .orient { margin-top: 20px; white-space: pre-wrap; }
        .assin { margin-top: 72px; width: 320px; border-top: 1px solid var(--ink); padding-top: 6px; text-align: center; font-size: 13px; }
        .cancelada { margin: 16px 0 0; padding: 10px 14px; border-radius: 8px; background: #ffedea; color: #831c1e; font-weight: 500; }
        .barra { max-width: 760px; margin: 16px auto 0; display: flex; justify-content: flex-end; gap: 8px; }
        .barra button, .barra a { font: inherit; padding: 8px 14px; border-radius: 6px; border: 1px solid #c9cdd1; background: #fff; color: var(--ink); text-decoration: none; cursor: pointer; }
        .barra button { background: var(--ink); color: #fff; border-color: var(--ink); }
        @media print { body { background: #fff; } .barra { display: none; } .folha { margin: 0; padding: 0; border-radius: 0; max-width: none; } }
        @media (max-width: 640px) { .folha { padding: 24px 18px; margin: 0; border-radius: 0; } .assin { width: 100%; } }
    </style>
</head>
<body>
<div class="barra">
    <a href="atendimento/<%= r.get("id_consulta") %>">Voltar ao atendimento</a>
    <button type="button" onclick="window.print()">Imprimir</button>
</div>
<main class="folha">
    <header>
        <div>
            <h1>Receituário</h1>
            <p>Hospital Case Manager · documento de demonstração</p>
        </div>
        <div class="num">Receita nº <%= r.get("id_receita") %><br><%= Rotulos.dataHora(r.get("emitida_em")) %></div>
    </header>
    <% if (cancelada) { %><p class="cancelada">Receita cancelada<%= r.get("motivo_cancelamento") != null ? ": " + Sanitize.escapeHtml(r.get("motivo_cancelamento")) : "" %>. Sem validade.</p><% } %>
    <dl class="pac">
        <div><dt>Paciente</dt><dd><%= Sanitize.escapeHtml(r.get("paciente")) %></dd></div>
        <div><dt>Nascimento</dt><dd><%= nasc != null ? Rotulos.data(nasc) + " (" + utils.Validacao.idade(nasc, java.time.LocalDate.now()) + " anos)" : "—" %></dd></div>
    </dl>
    <ol>
    <% for (Object o : (List<?>) r.get("itens")) { @SuppressWarnings("unchecked") Map<String, Object> i = (Map<String, Object>) o; %>
        <li><strong><%= Sanitize.escapeHtml(i.get("medicamento")) %></strong> <%= Sanitize.escapeHtml(i.get("dosagem")) %>
            <span class="uso"><%= i.get("via") != null ? "Uso " + Sanitize.escapeHtml(i.get("via")) + " · " : "" %><%= Sanitize.escapeHtml(i.get("frequencia")) %> · por <%= Sanitize.escapeHtml(i.get("duracao")) %>
            <%= i.get("orientacoes") != null ? " · " + Sanitize.escapeHtml(i.get("orientacoes")) : "" %></span></li>
    <% } %>
    </ol>
    <% if (r.get("orientacoes_gerais") != null) { %><div class="orient"><strong>Orientações:</strong> <%= Sanitize.escapeHtml(r.get("orientacoes_gerais")) %></div><% } %>
    <div class="assin"><%= Sanitize.escapeHtml(r.get("medico")) %><br>CRM <%= Sanitize.escapeHtml(r.get("crm")) %>/<%= Sanitize.escapeHtml(r.get("crm_uf")) %></div>
</main>
</body>
</html>
