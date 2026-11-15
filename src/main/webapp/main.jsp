<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="true"%>
<%@ page import="utils.Csrf" %>
<%@ page import="java.util.*" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="utils.Router" %>
<%@ page import="utils.Url" %>
<%@ page import="utils.Sanitize" %>
<%
    if (!Router.checkJspAccess(request, response)) return;
    Map<String, Object> user = session != null ? (Map<String, Object>) session.getAttribute("user") : null;
    if (user == null) user = new HashMap<>();
    List<String> grupos = user.get("grupos") instanceof List ? (List<String>) user.get("grupos") : new ArrayList<>();
    String perfilReq = request.getParameter("perfil");

    // Roteador de Perfil: Despacha para o dashboard específico do perfil
    if (perfilReq != null && !perfilReq.isEmpty()) {
        if ("admin".equals(perfilReq) && grupos.contains("admin")) {
            request.getRequestDispatcher("/main_admin.jsp").forward(request, response);
            return;
        }
        if ("gerente".equals(perfilReq) && (grupos.contains("gerente") || grupos.contains("admin"))) {
            request.getRequestDispatcher("/main_gerente.jsp").forward(request, response);
            return;
        }
        if ("financeiro".equals(perfilReq) && (grupos.contains("financeiro") || grupos.contains("admin"))) {
            request.getRequestDispatcher("/main_financeiro.jsp").forward(request, response);
            return;
        }
        if ("suporte".equals(perfilReq) && (grupos.contains("suporte") || grupos.contains("admin"))) {
            request.getRequestDispatcher("/main_suporte.jsp").forward(request, response);
            return;
        }
    } else {
        if (grupos.contains("admin")) {
            request.getRequestDispatcher("/main_admin.jsp").forward(request, response);
            return;
        }
        if (grupos.contains("gerente")) {
            request.getRequestDispatcher("/main_gerente.jsp").forward(request, response);
            return;
        }
        if (grupos.contains("financeiro")) {
            request.getRequestDispatcher("/main_financeiro.jsp").forward(request, response);
            return;
        }
        if (grupos.contains("suporte")) {
            request.getRequestDispatcher("/main_suporte.jsp").forward(request, response);
            return;
        }
    }

    boolean isAdmin = grupos.contains("admin");
    String svgRaw = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"150\" height=\"150\" viewBox=\"0 0 150 150\"><rect width=\"150\" height=\"150\" fill=\"#e2e8f0\"/><circle cx=\"75\" cy=\"58\" r=\"28\" fill=\"#64748b\"/><path d=\"M25 135c0-28 22-45 50-45s50 17 50 45\" fill=\"#64748b\"/></svg>";
    String defaultAvatarSvg = "data:image/svg+xml;utf8," + URLEncoder.encode(svgRaw, "UTF-8").replace("+", "%20");
    String userFoto = user.get("foto") != null ? String.valueOf(user.get("foto")) : "";
    String fotoSrc = !userFoto.isEmpty() ? userFoto : defaultAvatarSvg;
    String userNome = user.get("nome") != null ? String.valueOf(user.get("nome")) : "Usuário";
    String userEmail = user.get("email") != null ? String.valueOf(user.get("email")) : "";
    String gruposBadge = String.join(", ", grupos);
    String baseHref = Url.base(request);
%>
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <%= Csrf.metaTag(request) %>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Painel do Usuário — ZTF_J2EE</title>
    <base href="<%= Sanitize.escape(baseHref) %>">
    <link rel="stylesheet" href="assets/css/bootstrap.min.css">
    <style>
        body { background-color: #f8fafc; font-family: system-ui, -apple-system, sans-serif; }
        .profile-img {
            width: 120px;
            height: 120px;
            object-fit: cover;
            border-radius: 50%;
            border: 4px solid #0d6efd;
            box-shadow: 0 4px 10px rgba(0,0,0,0.15);
        }
        .card-custom {
            border: none;
            border-radius: 14px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.06);
            background-color: #fff;
        }
    </style>
</head>
<body class="bg-light">

<!-- Navbar Logada -->
<nav class="navbar navbar-expand-lg navbar-dark bg-dark sticky-top shadow-sm py-2">
    <div class="container">
        <a class="navbar-brand fw-bold text-primary" href="dashboard">
            👤 ZTF_J2EE <span class="badge bg-secondary">PAINEL DO USUÁRIO</span>
        </a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navDashboard">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navDashboard">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                <li class="nav-item"><a class="nav-link active" href="dashboard?perfil=usuario">Meu Perfil</a></li>
                <li class="nav-item"><a class="nav-link" href="chamados">Abrir Chamado</a></li>
                <li class="nav-item"><a class="nav-link" href="manual.html" target="_blank">Manual (HTML)</a></li>
                <% if (isAdmin) { %>
                    <li class="nav-item">
                        <a class="nav-link text-warning fw-bold" href="admin">
                            🛡️ Gestão Administrativa
                        </a>
                    </li>
                <% } %>
            </ul>
            <div class="d-flex align-items-center gap-3">
                <span class="text-white small">
                    Olá, <strong><%= Sanitize.escape(userNome) %></strong>
                    <span class="badge bg-primary ms-1"><%= Sanitize.escape(gruposBadge) %></span>
                </span>
                <a href="logout" class="btn btn-outline-danger btn-sm">Sair</a>
            </div>
        </div>
    </div>
</nav>

<div class="container my-5">
    <% if (grupos.contains("admin")) { %>
        <div class="alert alert-danger py-2 d-flex justify-content-between align-items-center mb-4 rounded-3 shadow-sm">
            <span>🛡️ Você está visualizando seu <strong>Perfil Pessoal</strong> como Administrador.</span>
            <a href="dashboard" class="btn btn-danger btn-sm fw-bold">Voltar ao Painel Admin &rarr;</a>
        </div>
    <% } else if (grupos.contains("gerente")) { %>
        <div class="alert alert-primary py-2 d-flex justify-content-between align-items-center mb-4 rounded-3 shadow-sm">
            <span>👔 Você está visualizando seu <strong>Perfil Pessoal</strong> como Gerente.</span>
            <a href="dashboard" class="btn btn-primary btn-sm fw-bold">Voltar ao Painel da Gerência &rarr;</a>
        </div>
    <% } else if (grupos.contains("financeiro")) { %>
        <div class="alert alert-success py-2 d-flex justify-content-between align-items-center mb-4 rounded-3 shadow-sm">
            <span>💰 Você está visualizando seu <strong>Perfil Pessoal</strong> como Analista Financeiro.</span>
            <a href="dashboard" class="btn btn-success btn-sm fw-bold">Voltar ao Painel Financeiro &rarr;</a>
        </div>
    <% } else if (grupos.contains("suporte")) { %>
        <div class="alert alert-info py-2 d-flex justify-content-between align-items-center mb-4 rounded-3 shadow-sm">
            <span>🎧 Você está visualizando seu <strong>Perfil Pessoal</strong> como Suporte Técnico.</span>
            <a href="dashboard" class="btn btn-info text-dark btn-sm fw-bold">Voltar à Central de Suporte &rarr;</a>
        </div>
    <% } %>

    <div class="row g-4">
        <!-- Coluna 1: Edição de Perfil e Foto Base64 -->
        <div class="col-lg-5">
            <div class="card card-custom p-4">
                <div class="text-center mb-4">
                    <img id="imgPreview" src="<%= Sanitize.escape(fotoSrc) %>" class="profile-img mb-3" alt="Foto de Perfil">
                    <h4 class="fw-bold mb-1" id="labelNome"><%= Sanitize.escape(userNome) %></h4>
                    <p class="text-muted small mb-2"><%= Sanitize.escape(userEmail) %></p>
                </div>

                <div id="perfilAlert" class="alert d-none"></div>

                <form id="formPerfil" action="api/perfil/atualizar" method="POST">
                    <div class="mb-3">
                        <label class="form-label small fw-bold text-muted">E-mail (Inalterável)</label>
                        <input type="email" class="form-control bg-light" value="<%= Sanitize.escape(userEmail) %>" readonly disabled>
                        <small class="text-muted" style="font-size: 11px;">O e-mail é o identificador único e não pode ser alterado.</small>
                    </div>

                    <div class="mb-3">
                        <label for="perfil-nome" class="form-label small fw-bold">Nome Completo</label>
                        <input type="text" class="form-control" id="perfil-nome" name="nome" value="<%= Sanitize.escape(userNome) %>" required>
                    </div>

                    <div class="mb-3">
                        <label for="inputFoto" class="form-label small fw-bold">Alterar Foto de Perfil (Base64)</label>
                        <input type="file" class="form-control" id="inputFoto" accept="image/*">
                        <input type="hidden" id="foto_base64" name="foto_base64" value="">
                    </div>

                    <hr class="my-3">
                    <h6 class="fw-bold text-secondary mb-3">Definir Nova Senha</h6>
                    <div class="mb-3">
                        <label for="perfil-senha-atual" class="form-label small fw-bold">Senha Atual (obrigatória para trocar)</label>
                        <input type="password" class="form-control" id="perfil-senha-atual" name="senha_atual" autocomplete="current-password" placeholder="••••••••">
                    </div>
                    <div class="mb-3">
                        <label for="perfil-nova-senha" class="form-label small fw-bold">Nova Senha (deixe em branco para manter)</label>
                        <input type="password" class="form-control" id="perfil-nova-senha" name="nova_senha" placeholder="••••••••">
                    </div>
                    <div class="mb-3">
                        <label for="perfil-conf-senha" class="form-label small fw-bold">Confirmar Nova Senha</label>
                        <input type="password" class="form-control" id="perfil-conf-senha" name="confirmar_nova_senha" placeholder="••••••••">
                    </div>

                    <button type="submit" class="btn btn-primary w-100 py-2 fw-semibold" id="btnSalvarPerfil">Salvar Alterações</button>
                </form>
            </div>
        </div>

        <!-- Coluna 2: Uploads em Base64 com Controle de Acesso (ACL) -->
        <div class="col-lg-7">
            <!-- Card de Envio Rápido via Componente uploadB64 -->
            <div class="card card-custom p-4 mb-4 border-start border-primary border-4">
                <h5 class="fw-bold mb-1">⚡ Upload Automático Base64 (Componente <code>.uploadB64</code>)</h5>
                <p class="text-muted small mb-3">Selecione um arquivo: ele será convertido para Base64 no navegador e enviado automaticamente via AJAX.</p>

                <div class="mb-2">
                    <label class="form-label small fw-bold">Selecione o arquivo para envio automático:</label>
                    <input type="file" 
                           class="form-control uploadB64" 
                           data-target="#hiddenHashAuto" 
                           data-publico="0"
                           data-grupos='["admin"]'>
                    <input type="hidden" id="hiddenHashAuto" name="documento_hash">
                </div>
            </div>

            <!-- Card de Listagem de Arquivos -->
            <div class="card card-custom p-4">
                <div class="d-flex justify-content-between align-items-center mb-3">
                    <h5 class="fw-bold mb-0">📁 Arquivos Disponíveis (Protegidos por ACL)</h5>
                    <button class="btn btn-sm btn-outline-primary" id="btnRecarregarArquivos">Atualizar Lista</button>
                </div>
                <div class="table-responsive">
                    <table class="table table-hover align-middle">
                        <thead class="table-light">
                            <tr>
                                <th>Nome do Arquivo</th>
                                <th>Dono</th>
                                <th>Tipo / Tamanho</th>
                                <th>Acesso</th>
                                <th>Ação</th>
                            </tr>
                        </thead>
                        <tbody id="tabelaArquivosCorpo">
                            <tr>
                                <td colspan="5" class="text-center text-muted py-3">Carregando arquivos...</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>
</div>

<script src="assets/js/jquery.min.js"></script>
<script src="assets/js/bootstrap.bundle.min.js"></script>
<script src="assets/js/zait.ajax.js"></script>
<script src="assets/js/ztfBase64File.js"></script>

<script>
$(document).ready(function() {

    // 1. CONVERSÃO DA FOTO DE PERFIL EM BASE64 NO CLIENTE
    $('#inputFoto').on('change', function(e) {
        const file = e.target.files[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = function(event) {
            const base64String = event.target.result;
            $('#imgPreview').attr('src', base64String);
            $('#foto_base64').val(base64String);
        };
        reader.readAsDataURL(file);
    });

    // 2. SUBMISSÃO DO PERFIL VIA JQUERY AJAX
    $('#formPerfil').on('submit', function(e) {
        e.preventDefault();
        const $alert = $('#perfilAlert').addClass('d-none');
        const $btn = $('#btnSalvarPerfil').prop('disabled', true).text('Gravando...');

        ZTF.ajax.submitForm(this)
            .then(function(res) {
                $alert.removeClass('d-none alert-danger').addClass('alert-success').text(res.message);
                if (res.data?.nome) {
                    $('#labelNome').text(res.data.nome);
                }
                $('#perfil-senha-atual').val('');
                $('#perfil-nova-senha').val('');
                $('#perfil-conf-senha').val('');
            })
            .catch(function(err) {
                $alert.removeClass('d-none alert-success').addClass('alert-danger').text(err.message || 'Erro ao atualizar.');
            })
            .finally(function() {
                $btn.prop('disabled', false).text('Salvar Alterações');
            });
    });

    // 3. EVENTO DE SUCESSO DO COMPONENTE .uploadB64: RECARREGA A LISTA DE ARQUIVOS
    $(document).on('upload:success', 'input.uploadB64', function(e, data) {
        carregarArquivos();
    });

    // 4. CARREGAMENTO DA TABELA DE ARQUIVOS
    function carregarArquivos() {
        ZTF.ajax.get('api/arquivos/listar')
            .then(function(res) {
                const $tbody = $('#tabelaArquivosCorpo').empty();
                if (!res.data || res.data.length === 0) {
                    $tbody.html('<tr><td colspan="5" class="text-center text-muted py-3">Nenhum arquivo encontrado.</td></tr>');
                    return;
                }

                const esc = ZTF.escapeHtml;
                res.data.forEach(function(arq) {
                    const isPub = parseInt(arq.publico) === 1;
                    const badgeAcesso = isPub 
                        ? '<span class="badge bg-success">Público</span>' 
                        : '<span class="badge bg-secondary">Privado (ACL)</span>';

                    const tamanhoKB = (parseInt(arq.tamanho_bytes) / 1024).toFixed(1) + ' KB';

                    const row = `
                        <tr>
                            <td><strong>${esc(arq.nome_original)}</strong></td>
                            <td class="small">${esc(arq.nome_dono)}</td>
                            <td class="small text-muted">${esc(arq.mime_type)}<br><small>${tamanhoKB}</small></td>
                            <td>${badgeAcesso}</td>
                            <td>
                                <a href="arquivo/${encodeURIComponent(arq.hash)}" target="_blank" class="btn btn-sm btn-outline-primary">
                                    Baixar / Ver
                                </a>
                            </td>
                        </tr>
                    `;
                    $tbody.append(row);
                });
            })
            .catch(function() {
                $('#tabelaArquivosCorpo').html('<tr><td colspan="5" class="text-center text-danger py-3">Erro ao carregar lista de arquivos.</td></tr>');
            });
    }

    $('#btnRecarregarArquivos').on('click', carregarArquivos);
    carregarArquivos();
});
</script>

</body>
</html>
