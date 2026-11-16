<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="utils.Csrf" %>
<%@ page import="utils.Router, utils.Sanitize" %>
<%
if (!Router.checkJspAccess(request, response)) return;
String tituloPagina = "Usuários e permissões";
%>
<%@ include file="/WEB-INF/views/layout/topo.jspf" %>
<%--
  Governanca RBAC: usuarios (grupos, situacao, desbloqueio) e matriz grupo x permissao.
  Os dados chegam por api/admin/* (JSON) e todo texto entra no DOM com .text() ou ZTF.escapeHtml.
  O backend confere grupo admin em cada rota; esconder um botao aqui nao protege nada.
--%>
<div class="hc-title-row">
    <div>
        <h1>Usuários e permissões</h1>
        <p class="hc-subtitle" id="resumoUsuarios">Carregando…</p>
    </div>
    <div class="hc-actions">
        <a class="hc-btn" href="profissionais"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-id-card"/></svg>Profissionais</a>
        <a class="hc-btn" href="auditoria"><svg class="ic" aria-hidden="true"><use href="assets/icons/icons.svg#i-file-text"/></svg>Auditoria</a>
    </div>
</div>

<div class="hc-tabs" role="tablist" aria-label="Administração de acesso">
    <button class="hc-tab" type="button" role="tab" id="abaUsuarios" aria-controls="painelUsuarios" aria-selected="true">Usuários</button>
    <button class="hc-tab" type="button" role="tab" id="abaPermissoes" aria-controls="painelPermissoes" aria-selected="false" tabindex="-1">Permissões por grupo</button>
</div>

<section id="painelUsuarios" role="tabpanel" aria-labelledby="abaUsuarios">
    <div class="hc-toolbar">
        <div class="d-flex gap-2 align-items-center flex-wrap">
            <label class="visually-hidden-hc" for="filtroUsuarios">Filtrar usuários</label>
            <input id="filtroUsuarios" class="hc-input" style="width:280px;height:36px" type="search" placeholder="Nome, e-mail ou grupo">
            <label class="visually-hidden-hc" for="filtroSituacao">Situação</label>
            <select id="filtroSituacao" class="hc-select" style="width:170px;height:36px">
                <option value="">Todas as situações</option>
                <option value="ativo">Ativos</option>
                <option value="inativo">Inativos</option>
                <option value="bloqueado">Bloqueados</option>
            </select>
        </div>
        <span class="hc-count" id="contagemUsuarios"></span>
    </div>
    <p class="hc-help">Contas de profissionais são criadas no <a href="profissionais">cadastro de profissionais</a>. O bloqueio é automático após várias senhas erradas seguidas; desbloquear fica registrado na auditoria.</p>
    <table class="hc-table">
        <thead><tr><th>Usuário</th><th>Grupos</th><th class="hide-sm">Último acesso</th><th style="width:110px">Situação</th><th style="width:250px"><span class="visually-hidden-hc">Ações</span></th></tr></thead>
        <tbody id="tabelaUsuarios"><tr><td colspan="5" class="hc-empty">Carregando usuários…</td></tr></tbody>
    </table>
</section>

<section id="painelPermissoes" role="tabpanel" aria-labelledby="abaPermissoes" hidden>
    <div class="hc-toolbar">
        <p class="hc-help mb-0">Marque o que cada grupo pode fazer. Telas clínicas exigem também o grupo clínico (o administrador não entra nelas, mesmo marcando aqui). Alterações valem no próximo clique de cada usuário.</p>
        <button class="hc-btn hc-btn-primary" type="button" id="salvarMatriz" disabled>Salvar alterações</button>
    </div>
    <div class="hc-matrix" id="matriz"><p class="hc-empty">Carregando permissões…</p></div>
</section>

<div class="modal fade" id="modalGrupos" tabindex="-1" aria-labelledby="modalGruposTitulo" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <form class="modal-content hc-dropdown" style="padding:20px" id="formGrupos">
            <h2 class="hc-section-title" id="modalGruposTitulo">Grupos de <span data-nome></span></h2>
            <p class="hc-help">Os grupos definem o painel inicial e as permissões. Sem grupo, o usuário volta para o grupo "usuario".</p>
            <div class="hc-choices mb-3" id="listaGrupos" style="grid-template-columns:1fr 1fr"></div>
            <div class="hc-actions" style="justify-content:flex-end">
                <button type="button" class="hc-btn" data-bs-dismiss="modal">Voltar</button>
                <button type="submit" class="hc-btn hc-btn-primary">Salvar grupos</button>
            </div>
        </form>
    </div>
</div>

<script>
document.addEventListener("DOMContentLoaded", function () {
    const esc = ZTF.escapeHtml;
    let usuarios = [], grupos = [], permissoes = [], mapa = {}, alterados = {};
    const idLogado = <%= utils.RequestHelper.getSessionUserId(request) %>;

    /* ---------------- abas */
    $('[role=tab]').on('click keydown', function (e) {
        if (e.type === 'keydown' && e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') { return; }
        const $alvo = e.type === 'keydown' ? $('[role=tab]').not(this) : $(this);
        $('[role=tab]').attr({ 'aria-selected': 'false', tabindex: '-1' });
        $alvo.attr({ 'aria-selected': 'true', tabindex: '0' }).trigger('focus');
        $('[role=tabpanel]').attr('hidden', true);
        $('#' + $alvo.attr('aria-controls')).removeAttr('hidden');
    });

    /* ---------------- usuarios */
    function situacao(u) {
        if (parseInt(u.bloqueado, 10) === 1) { return 'bloqueado'; }
        return parseInt(u.status, 10) === 1 ? 'ativo' : 'inativo';
    }
    function pilula(s) {
        const m = { ativo: ['positive', 'Ativo'], inativo: ['neutral', 'Inativo'], bloqueado: ['negative', 'Bloqueado'] }[s];
        return '<span class="hc-pill ' + m[0] + '">' + m[1] + '</span>';
    }
    function data(v) {
        if (!v) { return '<span class="muted">nunca</span>'; }
        const d = String(v).replace('T', ' ').substring(0, 16).split(' ');
        return esc(d[0].split('-').reverse().join('/') + (d[1] ? ' ' + d[1] : ''));
    }
    function desenharUsuarios() {
        const termo = $('#filtroUsuarios').val().trim().toLowerCase();
        const sit = $('#filtroSituacao').val();
        const $tb = $('#tabelaUsuarios').empty();
        const lista = usuarios.filter(function (u) {
            const texto = [u.nome, u.email, u.grupos, u.profissional].join(' ').toLowerCase();
            return (!termo || texto.indexOf(termo) >= 0) && (!sit || situacao(u) === sit);
        });
        $('#contagemUsuarios').text(lista.length + ' de ' + usuarios.length);
        if (!lista.length) { $tb.append('<tr><td colspan="5" class="hc-empty">Nenhum usuário com esse filtro.</td></tr>'); return; }
        lista.forEach(function (u) {
            const s = situacao(u);
            const tags = String(u.grupos || '').split(',').filter(Boolean)
                .map(function (g) { return '<span class="hc-pill accent">' + esc(g.trim()) + '</span>'; }).join('');
            const proprio = parseInt(u.id_usuario, 10) === idLogado;
            let acoes = '<button type="button" class="hc-btn hc-btn-sm" data-grupos="' + esc(u.id_usuario) + '">Grupos</button>';
            if (s === 'bloqueado') {
                acoes += ' <button type="button" class="hc-btn hc-btn-sm" data-desbloquear="' + esc(u.id_usuario) + '">Desbloquear</button>';
            }
            if (!proprio) {
                acoes += parseInt(u.status, 10) === 1
                    ? ' <button type="button" class="hc-btn hc-btn-sm hc-btn-text hc-btn-danger" data-status="0" data-id="' + esc(u.id_usuario) + '">Inativar</button>'
                    : ' <button type="button" class="hc-btn hc-btn-sm hc-btn-text" data-status="1" data-id="' + esc(u.id_usuario) + '">Reativar</button>';
            }
            const prof = u.id_profissional
                ? '<span class="hc-cell-sub">Profissional: <a href="profissionais/' + esc(u.id_profissional) + '">' + esc(u.profissional) + '</a></span>' : '';
            $tb.append('<tr><td><div class="d-flex align-items-center gap-2"><span class="hc-avatar" aria-hidden="true">' + esc(iniciais(u.nome)) + '</span>'
                + '<span><span class="hc-cell-main">' + esc(u.nome) + (proprio ? ' <span class="muted">(você)</span>' : '') + '</span>'
                + '<span class="hc-cell-sub">' + esc(u.email) + '</span>' + prof + '</span></div></td>'
                + '<td><div class="hc-tags">' + (tags || '<span class="muted">sem grupo</span>') + '</div></td>'
                + '<td class="hide-sm num">' + data(u.ultimo_login_em) + '</td>'
                + '<td>' + pilula(s) + '</td><td class="num">' + acoes + '</td></tr>');
        });
    }
    function iniciais(nome) {
        const p = String(nome || '?').replace(/\(.*?\)/g, '').trim().split(/\s+/);
        return ((p[0] || '?')[0] + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase();
    }
    function carregarUsuarios() {
        return ZTF.ajax.get('api/admin/usuarios').then(function (res) {
            usuarios = res.data || [];
            const ativos = usuarios.filter(function (u) { return situacao(u) === 'ativo'; }).length;
            const bloq = usuarios.filter(function (u) { return situacao(u) === 'bloqueado'; }).length;
            $('#resumoUsuarios').text(usuarios.length + ' usuários · ' + ativos + ' ativos' + (bloq ? ' · ' + bloq + ' bloqueados' : ''));
            desenharUsuarios();
        }).catch(function (err) {
            $('#tabelaUsuarios').html('<tr><td colspan="5" class="hc-empty"></td></tr>').find('td').text(err.message || 'Não foi possível carregar os usuários.');
        });
    }
    $('#filtroUsuarios, #filtroSituacao').on('input change', desenharUsuarios);

    function postar(url, dados) {
        return ZTF.ajax.post(url, dados).then(function (res) { HC.aviso(res.message, true); return carregarUsuarios(); })
            .catch(function (err) { HC.aviso(err.message || 'Não foi possível concluir.', false); });
    }
    $(document).on('click', '[data-status]', function () {
        postar('api/admin/usuario/status', { id_usuario: $(this).data('id'), status: $(this).data('status') });
    });
    $(document).on('click', '[data-desbloquear]', function () {
        postar('api/admin/usuario/desbloquear', { id_usuario: $(this).data('desbloquear') });
    });

    /* ---------------- grupos do usuario */
    $(document).on('click', '[data-grupos]', function () {
        const id = parseInt($(this).data('grupos'), 10);
        const u = usuarios.find(function (x) { return parseInt(x.id_usuario, 10) === id; });
        const atuais = String(u.ids_grupos || '').split(',').map(Number);
        const $m = $('#modalGrupos').data('id', id);
        $m.find('[data-nome]').text(u.nome);
        const $l = $('#listaGrupos').empty();
        grupos.forEach(function (g) {
            const $lab = $('<label class="hc-choice"></label>');
            $('<input type="checkbox">').val(g.id_grupo).prop('checked', atuais.indexOf(parseInt(g.id_grupo, 10)) >= 0).appendTo($lab);
            $lab.append('<span class="hc-check" aria-hidden="true"></span>');
            $('<span></span>').append($('<strong class="d-block"></strong>').text(g.nome))
                .append($('<span class="hc-cell-sub"></span>').text(g.descricao || '')).appendTo($lab);
            $l.append($lab);
        });
        bootstrap.Modal.getOrCreateInstance($m[0]).show();
    });
    $('#formGrupos').on('submit', function (e) {
        e.preventDefault();
        const $m = $('#modalGrupos');
        const ids = $('#listaGrupos input:checked').map(function () { return parseInt(this.value, 10); }).get();
        bootstrap.Modal.getInstance($m[0]).hide();
        postar('api/admin/usuario/grupos', { id_usuario: $m.data('id'), grupos: JSON.stringify(ids) });
    });

    /* ---------------- matriz de permissoes */
    function desenharMatriz() {
        const modulos = {};
        permissoes.forEach(function (p) {
            const mod = String(p.codigo).split('.')[0];
            (modulos[mod] = modulos[mod] || []).push(p);
        });
        let h = '<table><thead><tr><th>Permissão</th>';
        grupos.forEach(function (g) { h += '<th class="c">' + esc(g.nome) + '</th>'; });
        h += '</tr></thead><tbody>';
        Object.keys(modulos).sort().forEach(function (mod) {
            h += '<tr class="hc-matrix-mod"><td colspan="' + (grupos.length + 1) + '">' + esc(mod) + '</td></tr>';
            modulos[mod].forEach(function (p) {
                h += '<tr><td><span class="hc-code">' + esc(p.codigo) + '</span><span class="hc-cell-sub">' + esc(p.descricao || '') + '</span></td>';
                grupos.forEach(function (g) {
                    const on = (mapa[g.id_grupo] || []).indexOf(parseInt(p.id_permissao, 10)) >= 0;
                    h += '<td class="c"><input type="checkbox" data-g="' + esc(g.id_grupo) + '" value="' + esc(p.id_permissao) + '"'
                        + (on ? ' checked' : '') + ' aria-label="' + esc(g.nome + ': ' + p.codigo) + '"></td>';
                });
                h += '</tr>';
            });
        });
        $('#matriz').html(h + '</tbody></table>');
    }
    function carregarMatriz() {
        return ZTF.ajax.get('api/admin/grupos').then(function (res) {
            grupos = res.data.grupos || [];
            permissoes = res.data.permissoes || [];
            mapa = res.data.grupo_permissoes || {};
            alterados = {};
            $('#salvarMatriz').prop('disabled', true).text('Salvar alterações');
            desenharMatriz();
        });
    }
    $('#matriz').on('change', 'input', function () {
        alterados[$(this).data('g')] = true;
        const n = Object.keys(alterados).length;
        $('#salvarMatriz').prop('disabled', false).text('Salvar alterações (' + n + (n === 1 ? ' grupo)' : ' grupos)'));
    });
    $('#salvarMatriz').on('click', function () {
        const $b = $(this).prop('disabled', true);
        const envios = Object.keys(alterados).map(function (g) {
            const ids = $('#matriz input[data-g="' + g + '"]:checked').map(function () { return parseInt(this.value, 10); }).get();
            return ZTF.ajax.post('api/admin/grupo/permissoes', { id_grupo: g, permissoes: JSON.stringify(ids) });
        });
        Promise.all(envios).then(function () { HC.aviso('Permissões atualizadas.', true); return carregarMatriz(); })
            .catch(function (err) { HC.aviso(err.message || 'Não foi possível salvar.', false); $b.prop('disabled', false); });
    });

    carregarMatriz().then(carregarUsuarios);
});
</script>
<%@ include file="/WEB-INF/views/layout/rodape.jspf" %>
