<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*, utils.Url, utils.Sanitize" %>
<%
Object rawErr = request.getAttribute("errorDetails");
Map<?, ?> errorInfo = (rawErr instanceof Map<?, ?>) ? (Map<?, ?>) rawErr : Collections.emptyMap();
Object rawDet = errorInfo.get("details");
Map<?, ?> details = (rawDet instanceof Map<?, ?>) ? (Map<?, ?>) rawDet : Collections.emptyMap();

String host = Sanitize.escapeHtml(details.get("host") != null ? details.get("host") : "localhost");
String port = Sanitize.escapeHtml(details.get("port") != null ? details.get("port") : "3306");
String user = Sanitize.escapeHtml(details.get("user") != null ? details.get("user") : "root");
String dbname = Sanitize.escapeHtml(details.get("dbname") != null ? details.get("dbname") : "hospital");
Object msgFallback = errorInfo.get("message") != null ? errorInfo.get("message") : "Falha inesperada na conexão com o banco de dados.";
String jdbcError = Sanitize.escapeHtml(details.get("jdbc_error") != null ? details.get("jdbc_error") : msgFallback);
String baseHref = Url.base(request);
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ZTF_J2EE - Erro no Banco de Dados</title>
    <base href="<%= Sanitize.escapeHtml(baseHref) %>">
    <link rel="stylesheet" href="assets/css/bootstrap.min.css">
    <style>
        body {
            background-color: #f8f9fa;
            font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .error-card {
            max-width: 720px;
            width: 100%;
            background: #ffffff;
            border-radius: 12px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
            border: 1px solid #e9ecef;
            overflow: hidden;
        }
        .error-header {
            background: linear-gradient(135deg, #dc3545 0%, #b02a37 100%);
            color: #ffffff;
            padding: 24px 30px;
        }
        .code-box {
            background-color: #212529;
            color: #f8f9fa;
            border-radius: 6px;
            padding: 12px 16px;
            font-family: monospace;
            font-size: 0.9rem;
            word-break: break-all;
        }
    </style>
</head>
<body>

<div class="error-card">
    <div class="error-header">
        <h4 class="mb-1">Falha de Conexão com o Banco de Dados</h4>
        <small class="opacity-75">ZTF_J2EE &bull; Auto-Provisioning &amp; Diagnostic Shield</small>
    </div>
    
    <div class="p-4">
        <div class="alert alert-danger d-flex align-items-center mb-4" role="alert">
            <div>
                <strong>Atenção:</strong> <%= Sanitize.escapeHtml(msgFallback) %>
            </div>
        </div>

        <h6 class="text-uppercase text-muted fw-bold mb-3" style="font-size: 0.8rem; letter-spacing: 0.5px;">Parâmetros Verificados</h6>
        <div class="table-responsive mb-4">
            <table class="table table-bordered table-sm mb-0">
                <thead class="table-light">
                    <tr>
                        <th>Host</th>
                        <th>Porta</th>
                        <th>Usuário</th>
                        <th>Banco de Dados</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><code><%= host %></code></td>
                        <td><code><%= port %></code></td>
                        <td><code><%= user %></code></td>
                        <td><code><%= dbname %></code></td>
                    </tr>
                </tbody>
            </table>
        </div>

        <h6 class="text-uppercase text-muted fw-bold mb-2" style="font-size: 0.8rem; letter-spacing: 0.5px;">Mensagem Técnica do Servidor</h6>
        <div class="code-box mb-4">
            <%= jdbcError %>
        </div>

        <div class="card bg-light border-0 p-3 mb-4">
            <h6 class="fw-bold mb-2 text-dark">Como solucionar:</h6>
            <ol class="mb-0 text-muted small ps-3">
                <li>Certifique-se de que o serviço do MySQL/MariaDB está rodando no servidor (ex: <code>sudo systemctl start mysql</code>).</li>
                <li>Verifique se as credenciais no arquivo <code>WEB-INF/etc/config.json</code> (chave <code>database.connections.mysql</code>) estão corretas.</li>
                <li>Garanta que o usuário <code><%= user %></code> tenha privilégios de conexão e de criação (<code>CREATE DATABASE</code> / <code>CREATE TABLE</code>).</li>
                <li>Assim que a conexão estiver acessível, o ZTF_J2EE criará automaticamente o banco <code><%= dbname %></code> e provisionará toda a estrutura de tabelas e dados iniciais!</li>
            </ol>
        </div>

        <div class="d-flex justify-content-between align-items-center">
            <a href="manual.html" target="_blank" class="text-muted small text-decoration-none">📘 Manual do Framework</a>
            <button onclick="window.location.reload();" class="btn btn-primary px-4">
                Tentar Reconectar Agora
            </button>
        </div>
    </div>
</div>

<script src="assets/js/jquery.min.js"></script>
<script src="assets/js/bootstrap.bundle.min.js"></script>
</body>
</html>
