/**
 * Comportamentos comuns das telas de trabalho (sem regra de negocio: o servidor decide).
 * - formularios enviados por ZTF.ajax com os erros mostrados em cada campo;
 * - mascaras de CPF, telefone e CEP (jquery.mask, quando carregado);
 * - busca de paciente com sugestoes (formularios de agendamento);
 * - grade de horarios livres;
 * - acoes de linha (chegada, cancelar com motivo, realizado/ausente).
 * Todo texto vindo do servidor entra com .text() ou ZTF.escapeHtml.
 */
(function (window, $) {
    'use strict';
    if (!$ || !window.ZTF) { return; }
    const HC = {};
    const esc = ZTF.escapeHtml;

    /* ------------------------------------------------------------ avisos */
    HC.aviso = function (mensagem, ok) {
        let $a = $('#hcAviso');
        if (!$a.length) {
            $a = $('<div id="hcAviso" class="hc-alert" role="status" aria-live="polite"></div>');
            $('#conteudo').prepend($a);
        }
        $a.removeClass('ok err').addClass(ok ? 'ok' : 'err').text(mensagem).show();
        if (ok) { setTimeout(function () { $a.fadeOut(200); }, 4000); }
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    /* ------------------------------------------------------------ formularios */
    function limparErros($form) {
        $form.find('[aria-invalid="true"]').removeAttr('aria-invalid');
        $form.find('.hc-error').text('');
    }

    function mostrarErros($form, erros) {
        let primeiro = null;
        Object.keys(erros || {}).forEach(function (campo) {
            const $campo = $form.find('[name="' + campo + '"]');
            $campo.attr('aria-invalid', 'true');
            $form.find('[data-erro-de="' + campo + '"]').text(erros[campo]);
            if (!primeiro && $campo.length) { primeiro = $campo; }
        });
        if (primeiro) { primeiro.trigger('focus'); }
    }

    /** Envia um <form data-hc-form> pelo action/method; redireciona se o servidor mandar. */
    HC.ligarFormulario = function (form) {
        const $form = $(form);
        $form.on('submit', function (e) {
            e.preventDefault();
            limparErros($form);
            const $btn = $form.find('[type="submit"]').prop('disabled', true);
            ZTF.ajax.submitForm(this)
                .then(function (res) {
                    HC.aviso(res.message || 'Salvo.', true);
                    if (res.data && res.data.redirect) {
                        setTimeout(function () { window.location.href = res.data.redirect; }, 600);
                    } else {
                        $btn.prop('disabled', false);
                    }
                })
                .catch(function (err) {
                    HC.aviso(err.message || 'Não foi possível salvar.', false);
                    mostrarErros($form, err.details);
                    $btn.prop('disabled', false);
                });
        });
    };

    /* ------------------------------------------------------------ mascaras */
    HC.mascaras = function (ctx) {
        if (!$.fn.mask) { return; }
        const $c = $(ctx || document);
        $c.find('[data-mask="cpf"]').mask('000.000.000-00');
        $c.find('[data-mask="cep"]').mask('00000-000');
        const tel = function (val) { return val.replace(/\D/g, '').length === 11 ? '(00) 00000-0000' : '(00) 0000-00009'; };
        $c.find('[data-mask="telefone"]').mask(tel, { onKeyPress: function (val, e, field, options) { field.mask(tel.apply({}, arguments), options); } });
    };

    /* ------------------------------------------------------------ busca de paciente */
    HC.buscaPaciente = function (box) {
        const $box = $(box);
        const $input = $box.find('[data-busca]');
        const $lista = $box.find('[role="listbox"]');
        const $id = $box.find('input[name="id_paciente"]');
        const $escolhido = $box.find('[data-escolhido]');
        let timer = null;

        function escolher(p) {
            $id.val(p.id_paciente);
            $escolhido.html('<strong>' + esc(p.nome) + '</strong><span class="hc-cell-sub">CPF ' + esc(p.cpf) + '</span>').removeAttr('hidden');
            $input.val('').attr('aria-expanded', 'false');
            $lista.empty().attr('hidden', true);
            $box.trigger('paciente:escolhido', [p]);
        }

        $input.on('input', function () {
            clearTimeout(timer);
            const q = $input.val().trim();
            if (q.length < 2) { $lista.empty().attr('hidden', true); return; }
            timer = setTimeout(function () {
                ZTF.ajax.get('api/pacientes/busca', { q: q }).then(function (res) {
                    $lista.empty();
                    (res.data || []).forEach(function (p) {
                        $('<li role="option" tabindex="-1" class="hc-row" style="cursor:pointer"></li>')
                            .append($('<span class="grow"></span>').append($('<strong></strong>').text(p.nome))
                                .append($('<span class="hc-cell-sub"></span>').text('CPF ' + p.cpf)))
                            .on('click keydown', function (e) {
                                if (e.type === 'click' || e.key === 'Enter') { escolher(p); }
                            })
                            .appendTo($lista);
                    });
                    if (!(res.data || []).length) {
                        $lista.append($('<li class="hc-empty"></li>').text('Nenhum paciente ativo encontrado. Cadastre-o antes de agendar.'));
                    }
                    $lista.removeAttr('hidden');
                    $input.attr('aria-expanded', 'true');
                });
            }, 250);
        });
        $input.on('keydown', function (e) {
            if (e.key === 'ArrowDown') { e.preventDefault(); $lista.find('[role="option"]').first().trigger('focus'); }
        });
        $lista.on('keydown', '[role="option"]', function (e) {
            if (e.key === 'ArrowDown') { e.preventDefault(); $(this).next().trigger('focus'); }
            if (e.key === 'ArrowUp') { e.preventDefault(); ($(this).prev().length ? $(this).prev() : $input).trigger('focus'); }
        });
        const pre = $box.data('paciente');
        if (pre && pre.id_paciente) { escolher(pre); }
    };

    /* ------------------------------------------------------------ grade de horarios */
    HC.carregarHorarios = function ($grade, url, params, $hora) {
        $hora.val('');
        $grade.html('<span class="muted">Carregando horários…</span>');
        ZTF.ajax.get(url, params).then(function (res) {
            const lista = res.data || [];
            $grade.empty();
            if (!lista.length) {
                $grade.append($('<p class="hc-empty"></p>').text('Sem atendimento nesse dia. Escolha outra data.'));
                return;
            }
            const livres = lista.filter(function (h) { return h.livre; }).length;
            lista.forEach(function (h) {
                $('<button type="button" class="hc-slot"></button>')
                    .text(h.hora).attr('aria-pressed', 'false').prop('disabled', !h.livre)
                    .attr('aria-label', h.hora + (h.livre ? ' livre' : ' ocupado'))
                    .on('click', function () {
                        $grade.find('.hc-slot').attr('aria-pressed', 'false');
                        $(this).attr('aria-pressed', 'true');
                        $hora.val(h.hora).trigger('change');
                    })
                    .appendTo($grade);
            });
            $grade.append($('<p class="hc-help w-100 mb-0"></p>').text(livres + ' de ' + lista.length + ' horários livres'));
        }).catch(function (err) {
            $grade.empty().append($('<p class="hc-empty"></p>').text(err.message || 'Não foi possível carregar os horários.'));
        });
    };

    /* ------------------------------------------------------------ acoes de linha */
    HC.acao = function (url, dados, mensagemConfirmacao) {
        return ZTF.ajax.post(url, dados || {}).then(function (res) {
            HC.aviso(res.message, true);
            setTimeout(function () { window.location.reload(); }, 700);
        }).catch(function (err) { HC.aviso(err.message || 'Não foi possível concluir.', false); });
    };

    $(document).on('click', '[data-hc-acao]', function () {
        const $b = $(this);
        const url = $b.data('hc-acao');
        if ($b.data('hc-motivo')) {
            const $m = $('#hcModalMotivo');
            $m.find('[data-alvo]').text($b.data('hc-alvo') || '');
            const $aj = $m.find('[data-ajuda]');
            if (!$aj.data('padrao')) { $aj.data('padrao', $aj.text()); }
            $aj.text($b.data('hc-ajuda') || $aj.data('padrao'));
            $m.find('textarea').val('');
            $m.data('url', url);
            bootstrap.Modal.getOrCreateInstance($m[0]).show();
            return;
        }
        $b.prop('disabled', true);
        HC.acao(url, $b.data('hc-dados') || {});
    });

    $(document).on('submit', '#hcModalMotivo form', function (e) {
        e.preventDefault();
        const $m = $('#hcModalMotivo');
        const motivo = $m.find('textarea').val().trim();
        if (motivo.length < 3) { $m.find('.hc-error').text('Informe o motivo.'); return; }
        bootstrap.Modal.getInstance($m[0]).hide();
        HC.acao($m.data('url'), { motivo: motivo });
    });

    $(function () {
        HC.mascaras();
        $('[data-hc-form]').each(function () { HC.ligarFormulario(this); });
        $('[data-hc-busca-paciente]').each(function () { HC.buscaPaciente(this); });
    });

    window.HC = HC;
})(window, window.jQuery);
