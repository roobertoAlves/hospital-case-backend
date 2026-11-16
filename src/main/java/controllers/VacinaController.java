package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Agenda;
import models.Profissional;
import models.Vacina;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validacao;

/**
 * ZTF_J2EE - VacinaController
 *
 * Sala de vacinacao: agenda do dia (recepcao e enfermagem), agendamento pela recepcao,
 * aplicacao e falta (so enfermagem), horarios livres (equipe e portal) e cartao do paciente.
 */
@WebServlet(name = "VacinaController", urlPatterns = {"/api/vacinas", "/api/vacinas/*"})
public class VacinaController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /api/vacinas?data= */
    public void dia(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate dia = AgendaController.dataParam(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dia", dia);
        data.put("agendamentos", new Vacina().agendaDoDia(dia));
        data.put("vacinas", new Vacina().catalogo());
        jsonSuccess(response, data, null);
    }

    /** GET /api/vacinas/horarios?data= */
    public void horarios(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate dia = Validacao.data(RequestHelper.getParam(request, "data", ""));
        if (dia == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha a data.");
            return;
        }
        jsonSuccess(response, AgendaController.horariosJson(new Vacina().horarios(dia)), null);
    }

    /** POST /api/vacinas/agendamentos { id_paciente, id_vacina, data, hora } (recepcao) */
    public void agendar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int pac = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int vac = Sanitize.intVal(RequestHelper.getParam(request, "id_vacina", "0"), 0);
        Agenda.Resultado r = new Vacina().agendar(pac, vac, AgendaController.dataHora(request), RequestHelper.getSessionUserId(request), "recepcao");
        responder(response, r, "Vacina agendada.");
    }

    /** POST /api/vacinas/agendamentos/{id}/aplicar { lote, fabricante, observacoes } (enfermagem) */
    public void aplicar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        Map<String, Object> prof = new Profissional().porUsuario(idUsuario);
        if (prof == null || !"enfermeiro".equals(prof.get("tipo"))) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Só profissionais de enfermagem ativos registram a aplicação.");
            return;
        }
        Map<String, String> erros = new Vacina().aplicar(PacienteController.idDaRota(request), getPayload(request),
                ((Number) prof.get("id_profissional")).intValue(), idUsuario);
        if (erros.containsKey("_")) {
            jsonError(response, 409, "ERR_VACINA", erros.get("_"));
        } else if (!erros.isEmpty()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", "Revise os campos destacados.", erros);
        } else {
            jsonSuccess(response, null, "Aplicação registrada no cartão de vacinação.");
        }
    }

    /** POST /api/vacinas/agendamentos/{id}/ausente */
    public void ausente(HttpServletRequest request, HttpServletResponse response) throws IOException {
        responder(response, new Vacina().ausente(PacienteController.idDaRota(request), RequestHelper.getSessionUserId(request)), "Falta registrada.");
    }

    /** POST /api/vacinas/agendamentos/{id}/cancelar { motivo } */
    public void cancelar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        responder(response, new Vacina().cancelar(PacienteController.idDaRota(request), RequestHelper.getParam(request, "motivo", ""),
                RequestHelper.getSessionUserId(request), null), "Agendamento cancelado.");
    }

    /** GET /api/pacientes/{id}/vacinas (cartao de vacinacao para a equipe) */
    public void cartao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int pac = PacienteController.idDaRota(request);
        auditar(request, "vacina.cartao_visualizado", "pacientes", pac, pac, null);
        jsonSuccess(response, new Vacina().cartao(pac), null);
    }

    private void responder(HttpServletResponse response, Agenda.Resultado r, String ok) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, Map.of("id", r.id()), ok);
        } else {
            jsonError(response, 409, "ERR_VACINA", r.erro());
        }
    }
}
