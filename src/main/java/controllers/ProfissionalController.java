package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Especialidade;
import models.Notificacao;
import models.Profissional;
import utils.JsonResponse;
import utils.RequestHelper;

/**
 * ZTF_J2EE - ProfissionalController
 *
 * Cadastro de profissionais e de especialidades (Fase 5, item 3). Permissoes no routes.json:
 * profissionais.manage, especialidades.manage e agenda.disponibilidade.manage. A validacao
 * fica nos models; aqui, so a montagem da resposta, a auditoria de situacao e o aviso de acesso.
 */
@WebServlet(name = "ProfissionalController", urlPatterns = {"/profissionais", "/profissionais/*", "/api/profissionais",
        "/api/profissionais/*", "/especialidades", "/api/especialidades", "/api/especialidades/*"})
public class ProfissionalController extends Controller {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------ profissionais

    /** GET /profissionais?q=&tipo=&inativos=1 */
    public void lista(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String q = RequestHelper.getParam(request, "q", "").trim();
        String tipo = RequestHelper.getParam(request, "tipo", "");
        boolean inativos = "1".equals(RequestHelper.getParam(request, "inativos", ""));
        request.setAttribute("q", q);
        request.setAttribute("tipo", Profissional.TIPOS.contains(tipo) ? tipo : "");
        request.setAttribute("inativos", inativos);
        request.setAttribute("profissionais", new Profissional().listar(q, tipo, inativos));
        renderView(request, response, "profissionais.jsp");
    }

    /** GET /profissionais/novo?tipo= */
    public void novo(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, Object> p = new LinkedHashMap<>();
        String tipo = RequestHelper.getParam(request, "tipo", "medico");
        p.put("tipo", Profissional.TIPOS.contains(tipo) ? tipo : "medico");
        p.put("duracao_consulta_min", 30);
        p.put("ids_especialidades", new ArrayList<Integer>());
        prepararFormulario(request, p, null);
        renderView(request, response, "profissional_form.jsp");
    }

    /** GET /profissionais/{id} */
    public void editar(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int id = PacienteController.idDaRota(request);
        Map<String, Object> p = id > 0 ? new Profissional().porId(id) : null;
        if (p == null) {
            naoEncontrado(request, response, "Profissional não encontrado.");
            return;
        }
        Object idUsuario = p.get("id_usuario");
        prepararFormulario(request, p, idUsuario instanceof Number ? ((Number) idUsuario).intValue() : null);
        renderView(request, response, "profissional_form.jsp");
    }

    /** POST /api/profissionais */
    public void criar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idLogado = RequestHelper.getSessionUserId(request);
        Profissional.Resultado r = new Profissional().criar(getPayload(request), idLogado);
        if (!r.ok()) {
            erros(response, r.erros());
            return;
        }
        String mensagem = "Profissional cadastrado.";
        if (r.senhaTemporaria() != null) {
            String tipo = String.valueOf(getPayload(request).get("tipo"));
            Notificacao.acessoCriado(r.emailAcesso(), String.valueOf(getPayload(request).get("nome")),
                    rotuloPerfil(Profissional.grupoDoTipo(tipo)), r.senhaTemporaria(), idLogado);
            mensagem = "Profissional cadastrado. A senha temporária foi enviada para " + r.emailAcesso() + ".";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_profissional", r.id());
        data.put("redirect", "profissionais/" + r.id());
        jsonSuccess(response, data, mensagem);
    }

    /** POST /api/profissionais/{id} */
    public void atualizar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        Profissional.Resultado r = new Profissional().atualizar(id, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            if (r.erros().containsKey("_")) {
                jsonError(response, 404, "ERR_NOT_FOUND", r.erros().get("_"));
                return;
            }
            erros(response, r.erros());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("redirect", "profissionais/" + id);
        jsonSuccess(response, data, "Cadastro atualizado.");
    }

    /** POST /api/profissionais/{id}/situacao { ativo: 0|1 } */
    public void situacao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        if (id <= 0 || new Profissional().porId(id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Profissional não encontrado.");
            return;
        }
        boolean ativo = "1".equals(RequestHelper.getParam(request, "ativo", "0"));
        String bloqueio = new Profissional().definirAtivo(id, ativo, RequestHelper.getSessionUserId(request));
        if (bloqueio != null) {
            jsonError(response, 409, "ERR_CONSULTAS_FUTURAS", bloqueio);
            return;
        }
        auditar(request, ativo ? "profissional.reativado" : "profissional.inativado", "profissionais", id, null, null);
        jsonSuccess(response, null, ativo ? "Profissional reativado." : "Profissional inativado.");
    }

    /** POST /api/profissionais/{id}/disponibilidade  JSON { faixas: [{dia_semana, hora_inicio, hora_fim}] } */
    public void disponibilidade(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        Object raw = getPayload(request).get("faixas");
        List<Map<String, Object>> faixas = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map<?, ?>) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                        f.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    faixas.add(f);
                }
            }
        }
        Map<String, String> erros = new Profissional().salvarDisponibilidade(id, faixas, RequestHelper.getSessionUserId(request));
        if (erros.containsKey("_")) {
            jsonError(response, 400, "ERR_VALIDATION", erros.get("_"));
            return;
        }
        if (!erros.isEmpty()) {
            erros(response, erros);
            return;
        }
        jsonSuccess(response, null, faixas.isEmpty() ? "Disponibilidade removida: o médico não recebe novos agendamentos."
                : "Disponibilidade salva.");
    }

    // ------------------------------------------------------------------ especialidades

    /** GET /especialidades */
    public void especialidades(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("especialidades", new Especialidade().listar());
        renderView(request, response, "especialidades.jsp");
    }

    /** POST /api/especialidades e /api/especialidades/{id} { nome, descricao } */
    public void salvarEspecialidade(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        if (id > 0 && new Especialidade().porId(id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Especialidade não encontrada.");
            return;
        }
        long[] gerado = new long[1];
        Map<String, String> erros = new Especialidade().salvar(id, getPayload(request), RequestHelper.getSessionUserId(request), gerado);
        if (!erros.isEmpty()) {
            erros(response, erros);
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_especialidade", gerado[0]);
        data.put("redirect", "especialidades");
        jsonSuccess(response, data, id > 0 ? "Especialidade atualizada." : "Especialidade cadastrada.");
    }

    /** POST /api/especialidades/{id}/situacao { ativo: 0|1 } */
    public void situacaoEspecialidade(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        if (id <= 0 || new Especialidade().porId(id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Especialidade não encontrada.");
            return;
        }
        boolean ativo = "1".equals(RequestHelper.getParam(request, "ativo", "0"));
        String bloqueio = new Especialidade().definirAtivo(id, ativo);
        if (bloqueio != null) {
            jsonError(response, 409, "ERR_CONSULTAS_FUTURAS", bloqueio);
            return;
        }
        auditar(request, ativo ? "especialidade.reativada" : "especialidade.inativada", "especialidades", id, null, null);
        jsonSuccess(response, null, ativo ? "Especialidade reativada." : "Especialidade inativada.");
    }

    // ------------------------------------------------------------------ auxiliares

    private void prepararFormulario(HttpServletRequest request, Map<String, Object> p, Integer idUsuarioAtual) {
        request.setAttribute("profissional", p);
        request.setAttribute("especialidades", new Especialidade().ativas());
        request.setAttribute("usuarios", new Profissional().usuariosSemVinculo(idUsuarioAtual));
    }

    static String rotuloPerfil(String grupo) {
        switch (grupo) {
            case "medico":
                return "Médico";
            case "enfermeiro":
                return "Enfermagem";
            default:
                return "Recepção";
        }
    }

    private void erros(HttpServletResponse response, Map<String, String> erros) throws IOException {
        JsonResponse.error(response, 422, "ERR_VALIDATION", "Revise os campos destacados.", erros);
    }
}
