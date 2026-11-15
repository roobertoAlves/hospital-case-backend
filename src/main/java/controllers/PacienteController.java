package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Paciente;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - PacienteController
 *
 * Cadastro de pacientes (Fase 5): lista com busca, ficha, cadastro e edicao. As permissoes
 * de cada rota estao no routes.json (pacientes.view / create / edit); aqui ficam so a
 * validacao (no model) e a auditoria. Nenhuma tela de paciente mostra dado clinico.
 */
@WebServlet(name = "PacienteController", urlPatterns = {"/pacientes", "/pacientes/*", "/api/pacientes", "/api/pacientes/*"})
public class PacienteController extends Controller {

    private static final long serialVersionUID = 1L;
    private static final int POR_PAGINA = 20;

    /** GET /pacientes?q=&pagina=&inativos=1 */
    public void lista(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String q = RequestHelper.getParam(request, "q", "").trim();
        boolean inativos = "1".equals(RequestHelper.getParam(request, "inativos", ""));
        int pagina = Sanitize.intVal(RequestHelper.getParam(request, "pagina", "1"), 1);
        Paciente.Pagina resultado = new Paciente().buscar(q, inativos, pagina, POR_PAGINA);
        request.setAttribute("q", q);
        request.setAttribute("inativos", inativos);
        request.setAttribute("resultado", resultado);
        renderView(request, response, "pacientes.jsp");
    }

    /** GET /pacientes/novo */
    public void novo(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("paciente", new LinkedHashMap<String, Object>());
        renderView(request, response, "paciente_form.jsp");
    }

    /** GET /pacientes/{id}/editar */
    public void editar(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, Object> p = carregar(request, response);
        if (p == null) {
            return;
        }
        request.setAttribute("paciente", p);
        renderView(request, response, "paciente_form.jsp");
    }

    /** GET /pacientes/{id} */
    public void ficha(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, Object> p = carregar(request, response);
        if (p == null) {
            return;
        }
        int id = ((Number) p.get("id_paciente")).intValue();
        request.setAttribute("paciente", p);
        request.setAttribute("historico", new Paciente().historicoAdministrativo(id));
        auditar(request, "paciente.ficha_visualizada", "pacientes", id, id, null);
        renderView(request, response, "paciente_ficha.jsp");
    }

    /** POST /api/pacientes */
    public void criar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Paciente.Resultado r = new Paciente().criar(getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            responderErros(response, r.erros());
            return;
        }
        auditar(request, "paciente.criado", "pacientes", r.id(), (int) r.id(), null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_paciente", r.id());
        data.put("redirect", "pacientes/" + r.id());
        jsonSuccess(response, data, "Paciente cadastrado.");
    }

    /** POST /api/pacientes/{id} */
    public void atualizar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = idDaRota(request);
        if (id <= 0 || new Paciente().porId(id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Paciente não encontrado.");
            return;
        }
        Paciente.Resultado r = new Paciente().atualizar(id, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            responderErros(response, r.erros());
            return;
        }
        auditar(request, "paciente.atualizado", "pacientes", id, id, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redirect", "pacientes/" + id);
        jsonSuccess(response, data, "Cadastro atualizado.");
    }

    /** POST /api/pacientes/{id}/situacao  { ativo: 0|1 } */
    public void situacao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = idDaRota(request);
        if (id <= 0 || new Paciente().porId(id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Paciente não encontrado.");
            return;
        }
        boolean ativo = "1".equals(RequestHelper.getParam(request, "ativo", "0"));
        new Paciente().definirAtivo(id, ativo, RequestHelper.getSessionUserId(request));
        auditar(request, ativo ? "paciente.reativado" : "paciente.inativado", "pacientes", id, id, null);
        jsonSuccess(response, null, ativo ? "Paciente reativado." : "Paciente inativado.");
    }

    /** GET /api/pacientes/busca?q= (para os formularios de agendamento) */
    public void busca(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = RequestHelper.getParam(request, "q", "").trim();
        if (q.length() < 2) {
            jsonSuccess(response, List.of(), "Digite ao menos 2 caracteres.");
            return;
        }
        List<Map<String, Object>> itens = new Paciente().buscar(q, false, 1, 8).itens();
        for (Map<String, Object> i : itens) {
            i.put("cpf", utils.Validacao.cpfMascarado(String.valueOf(i.get("cpf"))));
        }
        jsonSuccess(response, itens, null);
    }

    // ------------------------------------------------------------------ auxiliares

    private Map<String, Object> carregar(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int id = idDaRota(request);
        Map<String, Object> p = id > 0 ? new Paciente().porId(id) : null;
        if (p == null) {
            response.setStatus(404);
            request.getRequestDispatcher("/errors/404.jsp").forward(request, response);
        }
        return p;
    }

    static int idDaRota(HttpServletRequest request) {
        return Sanitize.intVal(RequestHelper.getRouteParams(request).get("id"), 0);
    }

    private void responderErros(HttpServletResponse response, Map<String, String> erros) throws IOException {
        JsonResponse.error(response, 422, "ERR_VALIDATION", "Revise os campos destacados.", erros);
    }
}
