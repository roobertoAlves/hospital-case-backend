package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Appointments;
import models.Professional;
import models.Vaccine;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validation;

/**
 * ZTF_J2EE - VacinaController
 *
 * Sala de vacinacao: agenda do dia (recepcao e enfermagem), agendamento pela recepcao,
 * aplicacao e falta (so enfermagem), horarios livres (equipe e portal) e cartao do paciente.
 */
@WebServlet(name = "VaccineController", urlPatterns = {"/api/vaccines", "/api/vaccines/*"})
public class VaccineController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /api/vacinas?data= */
    public void day(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate day = AppointmentController.dateParam(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day);
        data.put("appointments", new Vaccine().daySchedule(day));
        data.put("vaccines", new Vaccine().catalog());
        jsonSuccess(response, data, null);
    }

    /** GET /api/vacinas/horarios?data= */
    public void slots(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate day = Validation.date(RequestHelper.getParam(request, "date", ""));
        if (day == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha a data.");
            return;
        }
        jsonSuccess(response, AppointmentController.slotsJson(new Vaccine().slots(day)), null);
    }

    /** POST /api/vacinas/agendamentos { id_paciente, id_vacina, data, hora } (recepcao) */
    public void book(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int pac = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int vac = Sanitize.intVal(RequestHelper.getParam(request, "id_vacina", "0"), 0);
        Appointments.Result r = new Vaccine().book(pac, vac, AppointmentController.dateTime(request), RequestHelper.getSessionUserId(request), "recepcao");
        respond(response, r, "Vacina agendada.");
    }

    /** POST /api/vacinas/agendamentos/{id}/aplicar { lote, fabricante, observacoes } (enfermagem) */
    public void administer(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        Map<String, Object> prof = new Professional().byUser(userId);
        if (prof == null || !"enfermeiro".equals(prof.get("tipo"))) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Só profissionais de enfermagem ativos registram a aplicação.");
            return;
        }
        Map<String, String> errors = new Vaccine().apply(PatientController.routeId(request), getPayload(request),
                ((Number) prof.get("id_profissional")).intValue(), userId);
        if (errors.containsKey("_")) {
            jsonError(response, 409, "ERR_VACINA", errors.get("_"));
        } else if (!errors.isEmpty()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", "Revise os campos destacados.", errors);
        } else {
            jsonSuccess(response, null, "Aplicação registrada no cartão de vacinação.");
        }
    }

    /** POST /api/vacinas/agendamentos/{id}/ausente */
    public void markNoShow(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, new Vaccine().absent(PatientController.routeId(request), RequestHelper.getSessionUserId(request)), "Falta registrada.");
    }

    /** POST /api/vacinas/agendamentos/{id}/cancelar { motivo } */
    public void cancel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, new Vaccine().cancel(PatientController.routeId(request), RequestHelper.getParam(request, "reason", ""),
                RequestHelper.getSessionUserId(request), null), "Agendamento cancelado.");
    }

    /** GET /api/pacientes/{id}/vacinas (cartao de vacinacao para a equipe) */
    public void card(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int pac = PatientController.routeId(request);
        audit(request, "vacina.cartao_visualizado", "pacientes", pac, pac, null);
        jsonSuccess(response, new Vaccine().card(pac), null);
    }

    private void respond(HttpServletResponse response, Appointments.Result r, String ok) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, Map.of("id", r.id()), ok);
        } else {
            jsonError(response, 409, "ERR_VACINA", r.error());
        }
    }
}
