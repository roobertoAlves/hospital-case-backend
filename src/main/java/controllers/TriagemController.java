package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Atendimento;
import models.Profissional;
import models.Triagem;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - TriagemController
 *
 * Fila de triagem e registro da triagem pela enfermagem (RF-TRI-01..05). As rotas sao do
 * grupo "enfermeiro" com strictGroup (o administrador nao tem acesso clinico - decisao P1)
 * e exigem triagem.fila / triagem.registrar. O enfermeiro e o profissional ligado ao usuario.
 */
@WebServlet(name = "TriagemController", urlPatterns = {"/triagem", "/triagem/*", "/api/triagem", "/api/triagem/*", "/api/triagens/*"})
public class TriagemController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /triagem */
    public void fila(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Triagem t = new Triagem();
        request.setAttribute("fila", t.fila());
        int idEnfermeiro = idEnfermeiro(request);
        request.setAttribute("recentes", idEnfermeiro > 0 ? t.recentesDoEnfermeiro(idEnfermeiro) : java.util.List.of());
        request.setAttribute("classificacoes", t.classificacoes());
        renderView(request, response, "triagem.jsp");
    }

    /** GET /triagem/{id}  (id da consulta) */
    public void formulario(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int idConsulta = PacienteController.idDaRota(request);
        Triagem t = new Triagem();
        Map<String, Object> consulta = idConsulta > 0 ? t.consulta(idConsulta) : null;
        if (consulta == null) {
            naoEncontrado(request, response, "Consulta não encontrada.");
            return;
        }
        String status = String.valueOf(consulta.get("status"));
        Map<String, Object> atual = t.atual(idConsulta);
        // So abre o formulario quem iniciou a triagem (status em_triagem) ou, para retificar,
        // enquanto o paciente ainda nao saiu do atendimento.
        boolean podeRegistrar = "em_triagem".equals(status) && atual == null;
        boolean podeRetificar = atual != null && ("aguardando_atendimento".equals(status) || "em_atendimento".equals(status));
        if (!podeRegistrar && !podeRetificar && atual == null) {
            redirect(request, response, "triagem");
            return;
        }
        int idPaciente = ((Number) consulta.get("id_paciente")).intValue();
        request.setAttribute("consulta", consulta);
        request.setAttribute("triagem", atual);
        request.setAttribute("podeRegistrar", podeRegistrar);
        request.setAttribute("podeRetificar", podeRetificar);
        request.setAttribute("classificacoes", t.classificacoes());
        request.setAttribute("alergias", new Atendimento().alergias(idPaciente));
        auditar(request, "triagem.tela_aberta", "consultas", idConsulta, idPaciente, null);
        renderView(request, response, "triagem_form.jsp");
    }

    /** POST /api/triagem/{id}/iniciar */
    public void iniciar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idConsulta = PacienteController.idDaRota(request);
        if (!new Triagem().iniciar(idConsulta, RequestHelper.getSessionUserId(request))) {
            jsonError(response, 409, "ERR_CONFLICT", "Outro enfermeiro já iniciou esta triagem ou o paciente saiu da fila.");
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redirect", "triagem/" + idConsulta);
        jsonSuccess(response, data, "Triagem iniciada.");
    }

    /** POST /api/triagem/{id}/devolver */
    public void devolver(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idConsulta = PacienteController.idDaRota(request);
        if (!new Triagem().devolver(idConsulta, RequestHelper.getSessionUserId(request))) {
            jsonError(response, 409, "ERR_CONFLICT", "A triagem não está em andamento.");
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redirect", "triagem");
        jsonSuccess(response, data, "Paciente devolvido à fila.");
    }

    /** POST /api/triagem/{id}  (registra a triagem da consulta) */
    public void registrar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idEnfermeiro = idEnfermeiro(request);
        if (idEnfermeiro <= 0) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Seu usuário não está ligado a um cadastro de enfermeiro.");
            return;
        }
        int idConsulta = PacienteController.idDaRota(request);
        Triagem.Resultado r = new Triagem().registrar(idConsulta, idEnfermeiro, getPayload(request),
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", primeiraOuPadrao(r.erros()), r.erros());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_triagem", r.id());
        data.put("redirect", "triagem");
        jsonSuccess(response, data, "Triagem registrada. O paciente entrou na fila do médico.");
    }

    /** POST /api/triagens/{id}/retificar  (id da triagem que sera corrigida) */
    public void retificar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idEnfermeiro = idEnfermeiro(request);
        if (idEnfermeiro <= 0) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Seu usuário não está ligado a um cadastro de enfermeiro.");
            return;
        }
        int idTriagem = PacienteController.idDaRota(request);
        Map<String, Object> dados = getPayload(request);
        Triagem.Resultado r = new Triagem().retificar(idTriagem, idEnfermeiro, dados,
                Sanitize.string(dados.get("motivo_retificacao")), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", primeiraOuPadrao(r.erros()), r.erros());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_triagem", r.id());
        data.put("redirect", "triagem");
        jsonSuccess(response, data, "Triagem retificada. A versão anterior continua no histórico.");
    }

    // ------------------------------------------------------------------ auxiliares

    static int idProfissional(HttpServletRequest request, String tipo) {
        int idUsuario = RequestHelper.getSessionUserId(request);
        Map<String, Object> p = idUsuario > 0 ? new Profissional().porUsuario(idUsuario) : null;
        // porUsuario so devolve profissional ativo
        if (p == null || !tipo.equals(p.get("tipo"))) {
            return 0;
        }
        return ((Number) p.get("id_profissional")).intValue();
    }

    private static int idEnfermeiro(HttpServletRequest request) {
        return idProfissional(request, "enfermeiro");
    }

    private static String primeiraOuPadrao(Map<String, String> erros) {
        String geral = erros.get("geral");
        return geral != null ? geral : "Revise os campos destacados.";
    }
}
