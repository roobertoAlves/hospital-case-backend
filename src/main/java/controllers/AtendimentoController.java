package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Atendimento;
import models.Receita;
import models.SolicitacaoExame;
import models.Triagem;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - AtendimentoController
 *
 * Fila do medico, prontuario (rascunho, versoes, finalizacao), evolucoes, alergias, receitas
 * e pedidos de exame. As rotas sao do grupo "medico" (strictGroup) e cada acao confere o
 * vinculo: a consulta tem de ser do medico ligado ao usuario da sessao (sem IDOR). Uma consulta
 * de outro medico responde 404, para nao revelar que existe.
 */
@WebServlet(name = "AtendimentoController",
        urlPatterns = {"/atendimento", "/atendimento/*", "/api/atendimento", "/api/atendimento/*", "/api/receitas/*", "/receitas/*",
                "/api/solicitacoes-exame/*"})
public class AtendimentoController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /atendimento */
    public void fila(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int idMedico = idMedico(request);
        request.setAttribute("fila", idMedico > 0 ? new Atendimento().filaDoMedico(idMedico) : List.of());
        request.setAttribute("semCadastro", idMedico <= 0);
        renderView(request, response, "atendimento.jsp");
    }

    /** GET /atendimento/{id}  (id da consulta) */
    public void tela(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int idConsulta = PacienteController.idDaRota(request);
        Atendimento a = new Atendimento();
        Map<String, Object> c = idConsulta > 0 ? a.consultaDoMedico(idConsulta, idMedico(request)) : null;
        if (c == null) {
            naoEncontrado(request, response, "Consulta não encontrada.");
            return;
        }
        String status = String.valueOf(c.get("status"));
        if (!"em_atendimento".equals(status) && !"realizada".equals(status) && !"aguardando_atendimento".equals(status)) {
            redirect(request, response, "atendimento");
            return;
        }
        int idPaciente = ((Number) c.get("id_paciente")).intValue();
        request.setAttribute("consulta", c);
        request.setAttribute("triagem", new Triagem().atual(idConsulta));
        request.setAttribute("alergias", a.alergias(idPaciente));
        request.setAttribute("evolucoes", a.evolucoes(idConsulta));
        request.setAttribute("versoes", a.quantidadeDeVersoes(idConsulta));
        request.setAttribute("receitas", new Receita().daConsulta(idConsulta));
        request.setAttribute("exames", new SolicitacaoExame().daConsulta(idConsulta));
        request.setAttribute("anteriores", a.prontuariosDoPaciente(idPaciente).stream()
                .filter(p -> ((Number) p.get("id_consulta")).intValue() != idConsulta).limit(5).toList());
        auditar(request, "prontuario.leitura", "consultas", idConsulta, idPaciente, null);
        renderView(request, response, "atendimento_form.jsp");
    }

    /** POST /api/atendimento/{id}/iniciar */
    public void iniciar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        int idConsulta = PacienteController.idDaRota(request);
        Atendimento.Resultado r = new Atendimento().iniciar(idConsulta, idMedico, RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 409, "ERR_CONFLICT", r.erro());
            return;
        }
        jsonSuccess(response, redirect("atendimento/" + idConsulta), "Atendimento iniciado.");
    }

    /** POST /api/atendimento/{id}/prontuario  (salva o rascunho) */
    public void salvar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        Atendimento.Resultado r = new Atendimento().salvarRascunho(PacienteController.idDaRota(request), idMedico,
                getPayload(request), RequestHelper.getSessionUserId(request));
        responder(response, r, null, "Rascunho salvo.");
    }

    /** POST /api/atendimento/{id}/finalizar */
    public void finalizar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        int idConsulta = PacienteController.idDaRota(request);
        Atendimento.Resultado r = new Atendimento().finalizar(idConsulta, idMedico, getPayload(request),
                RequestHelper.getSessionUserId(request));
        responder(response, r, redirect("atendimento/" + idConsulta), "Atendimento finalizado.");
    }

    /** POST /api/atendimento/{id}/evolucoes  { tipo: evolucao|adendo, texto } */
    public void evolucao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        Map<String, Object> d = getPayload(request);
        Atendimento.Resultado r = new Atendimento().registrarEvolucao(PacienteController.idDaRota(request), idMedico,
                Sanitize.string(d.get("tipo")), Sanitize.string(d.get("texto")), RequestHelper.getSessionUserId(request));
        responder(response, r, redirect("atendimento/" + PacienteController.idDaRota(request)), "Registro incluído.");
    }

    /** POST /api/atendimento/{id}/alergias  { substancia, reacao, gravidade } */
    public void alergia(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        int idConsulta = PacienteController.idDaRota(request);
        Map<String, Object> c = new Atendimento().consultaDoMedico(idConsulta, idMedico);
        if (c == null || !"em_atendimento".equals(c.get("status"))) {
            jsonError(response, 409, "ERR_CONFLICT", "Registre alergias durante o atendimento.");
            return;
        }
        Map<String, Object> d = getPayload(request);
        Atendimento.Resultado r = new Atendimento().registrarAlergia(((Number) c.get("id_paciente")).intValue(),
                Sanitize.string(d.get("substancia")), Sanitize.string(d.get("reacao")), Sanitize.string(d.get("gravidade")),
                RequestHelper.getSessionUserId(request));
        responder(response, r, redirect("atendimento/" + idConsulta), "Alergia registrada.");
    }

    /** POST /api/atendimento/{id}/receitas  JSON { orientacoes_gerais, itens: [...] } */
    public void emitirReceita(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        Map<String, Object> d = getPayload(request);
        Object itens = d.get("itens");
        Receita.Resultado r = new Receita().emitir(PacienteController.idDaRota(request), idMedico,
                Sanitize.string(d.get("orientacoes_gerais")), itens instanceof List ? (List<?>) itens : null,
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            String geral = r.erros().getOrDefault("geral", r.erros().values().iterator().next());
            JsonResponse.error(response, 422, "ERR_VALIDATION", geral, r.erros());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_receita", r.id());
        jsonSuccess(response, data, "Receita emitida.");
    }

    /** POST /api/receitas/{id}/cancelar  { motivo } */
    public void cancelarReceita(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        Receita.Resultado r = new Receita().cancelar(PacienteController.idDaRota(request), idMedico,
                Sanitize.string(getPayload(request).get("motivo")), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.erros().values().iterator().next());
            return;
        }
        jsonSuccess(response, null, "Receita cancelada.");
    }

    /** GET /receitas/{id}  (pagina de impressao) */
    public void imprimirReceita(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int idReceita = PacienteController.idDaRota(request);
        Map<String, Object> r = idReceita > 0 ? new Receita().paraImpressao(idReceita, idMedico(request)) : null;
        if (r == null) {
            naoEncontrado(request, response, "Receita não encontrada.");
            return;
        }
        auditar(request, "receita.impressa", "receitas", idReceita, ((Number) r.get("id_paciente")).intValue(), null);
        request.setAttribute("receita", r);
        renderView(request, response, "receita_imprimir.jsp");
    }

    /** POST /api/atendimento/{id}/exames  { nome_exame, codigo, justificativa, observacoes } */
    public void solicitarExame(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        int idConsulta = PacienteController.idDaRota(request);
        SolicitacaoExame.Resultado r = new SolicitacaoExame().solicitar(idConsulta, idMedico, getPayload(request),
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.erro());
            return;
        }
        jsonSuccess(response, redirect("atendimento/" + idConsulta), "Exame solicitado.");
    }

    /** POST /api/solicitacoes-exame/{id}/cancelar  { motivo } */
    public void cancelarExame(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idMedico = exigirMedico(request, response);
        if (idMedico <= 0) {
            return;
        }
        SolicitacaoExame.Resultado r = new SolicitacaoExame().cancelar(PacienteController.idDaRota(request), idMedico,
                Sanitize.string(getPayload(request).get("motivo")), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.erro());
            return;
        }
        jsonSuccess(response, null, "Solicitação cancelada.");
    }

    // ------------------------------------------------------------------ auxiliares

    private static int idMedico(HttpServletRequest request) {
        return TriagemController.idProfissional(request, "medico");
    }

    private int exigirMedico(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = idMedico(request);
        if (id <= 0) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Seu usuário não está ligado a um cadastro de médico.");
        }
        return id;
    }

    private static Map<String, Object> redirect(String destino) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redirect", destino);
        return data;
    }

    private void responder(HttpServletResponse response, Atendimento.Resultado r, Map<String, Object> data, String msg)
            throws IOException {
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.erro());
            return;
        }
        jsonSuccess(response, data, msg);
    }
}
