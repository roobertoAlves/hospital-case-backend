<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<%@ page import="java.util.*" %>
<%@ page import="utils.Router" %>
<%@ page import="utils.Url" %>
<%@ page import="utils.Sanitize" %>
<%
    if (!Router.checkJspAccess(request, response)) return;
    Map<String, Object> user = session != null ? (Map<String, Object>) session.getAttribute("user") : null;
    if (user == null) user = new HashMap<>();
    String userNome = user.get("nome") != null ? String.valueOf(user.get("nome")) : "Financeiro";
    List<String> grupos = user.get("grupos") instanceof List ? (List<String>) user.get("grupos") : new ArrayList<>();
    String baseHref = Url.base(request);
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Painel Financeiro — ZTF_J2EE</title>
    <base href="<%= Sanitize.escape(baseHref) %>">
    <link rel="stylesheet" href="assets/css/bootstrap.min.css">
    <style>
        body { background-color: #f8fafc; font-family: system-ui, -apple-system, sans-serif; }
        .hero-fin { background: linear-gradient(135deg, #065f46 0%, #047857 100%); color: #fff; padding: 35px 0; border-bottom: 4px solid #10b981; }
        .stat-card { border: none; border-radius: 12px; background: #fff; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.06); padding: 22px; }
    </style>
</head>
<body>

<nav class="navbar navbar-expand-lg navbar-dark bg-dark sticky-top py-2 shadow-sm">
    <div class="container">
        <a class="navbar-brand fw-bold text-success" href="dashboard">
            💰 ZTF_J2EE <span class="badge bg-success text-white">FINANCEIRO</span>
        </a>
        <div class="navbar-nav ms-auto d-flex align-items-center gap-3">
            <span class="text-white small">Analista: <strong><%= Sanitize.escape(userNome) %></strong></span>
            <a href="dashboard?perfil=usuario" class="btn btn-outline-light btn-sm">Meu Perfil</a>
            <a href="logout" class="btn btn-outline-danger btn-sm">Sair</a>
        </div>
    </div>
</nav>

<header class="hero-fin">
    <div class="container">
        <span class="badge bg-success mb-2 px-3 py-1 text-uppercase fw-bold">Nível 4 &bull; Controladoria &amp; Finanças</span>
        <h2 class="fw-bold mb-1">Painel do Departamento Financeiro</h2>
        <p class="text-light opacity-75 mb-0">Gestão de fluxo de caixa, conciliação de lançamentos e custódia de documentos fiscais em Base64.</p>

        <% if (grupos.contains("admin")) { %>
            <div class="d-inline-flex align-items-center gap-2 mt-4 bg-white text-dark small px-3 py-1 rounded-pill">
                <strong class="text-muted text-uppercase" style="font-size: 0.75rem;">Visão do Perfil:</strong>
                <a href="dashboard" class="badge bg-danger text-decoration-none">Admin</a>
                <a href="dashboard?perfil=gerente" class="badge bg-primary text-decoration-none">Gerente</a>
                <a href="dashboard?perfil=financeiro" class="badge bg-success text-decoration-none">Financeiro (Ativo)</a>
                <a href="dashboard?perfil=suporte" class="badge bg-info text-dark text-decoration-none">Suporte</a>
                <a href="dashboard?perfil=usuario" class="badge bg-secondary text-decoration-none">Usuário Padrão</a>
            </div>
        <% } %>
    </div>
</header>

<div class="container my-5">
    <div class="row g-4 mb-5">
        <div class="col-md-3">
            <div class="stat-card border-start border-success border-4">
                <div class="text-muted small text-uppercase fw-bold">Receitas do Mês</div>
                <div class="fs-2 fw-bold text-success mt-1">R$ 84.500,00</div>
                <small class="text-muted">&bull; Faturamento consolidado</small>
            </div>
        </div>
        <div class="col-md-3">
            <div class="stat-card border-start border-danger border-4">
                <div class="text-muted small text-uppercase fw-bold">Despesas Operacionais</div>
                <div class="fs-2 fw-bold text-danger mt-1">R$ 32.120,00</div>
                <small class="text-muted">&bull; Contas liquidadas</small>
            </div>
        </div>
        <div class="col-md-3">
            <div class="stat-card border-start border-primary border-4">
                <div class="text-muted small text-uppercase fw-bold">Saldo Disponível</div>
                <div class="fs-2 fw-bold text-primary mt-1">R$ 52.380,00</div>
                <small class="text-success">&bull; Fluxo superavitário</small>
            </div>
        </div>
        <div class="col-md-3">
            <div class="stat-card border-start border-warning border-4">
                <div class="text-muted small text-uppercase fw-bold">Notas Fiscais em Base64</div>
                <div class="fs-2 fw-bold text-dark mt-1">142 Docs</div>
                <small class="text-muted">&bull; Storage com ACL Financeiro</small>
            </div>
        </div>
    </div>

    <!-- Tabela de Lançamentos Recentes -->
    <div class="card p-4 border-0 shadow-sm rounded-4 bg-white">
        <h5 class="fw-bold mb-3">📑 Demonstrativo de Lançamentos Recentes</h5>
        <div class="table-responsive">
            <table class="table table-hover align-middle mb-0">
                <thead class="table-light">
                    <tr>
                        <th># ID</th>
                        <th>Descrição da Operação</th>
                        <th>Categoria</th>
                        <th>Valor</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><strong>#101</strong></td>
                        <td>Licenciamento ZTF_J2EE Enterprise</td>
                        <td>Receita de Software</td>
                        <td class="text-success fw-bold">+ R$ 12.500,00</td>
                        <td><span class="badge bg-success">Liquidado</span></td>
                    </tr>
                    <tr>
                        <td><strong>#102</strong></td>
                        <td>Servidores Cloud &amp; Hospedagem</td>
                        <td>Infraestrutura</td>
                        <td class="text-danger fw-bold">- R$ 1.850,00</td>
                        <td><span class="badge bg-success">Pago</span></td>
                    </tr>
                    <tr>
                        <td><strong>#103</strong></td>
                        <td>Serviços de Consultoria e Treinamento</td>
                        <td>Serviços</td>
                        <td class="text-success fw-bold">+ R$ 6.200,00</td>
                        <td><span class="badge bg-warning text-dark">A Conciliar</span></td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script src="assets/js/jquery.min.js"></script>
<script src="assets/js/bootstrap.bundle.min.js"></script>
</body>
</html>
