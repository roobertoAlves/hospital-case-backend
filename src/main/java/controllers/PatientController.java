package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Patient;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "PatientController", urlPatterns = {"/api/patients", "/api/patients/*"})
public class PatientController extends Controller {

    private static final long serialVersionUID = 1L;
    private static final int PER_PAGE = 20;

    public void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = RequestHelper.getParam(request, "q", "").trim();
        boolean inactive = "1".equals(RequestHelper.getParam(request, "inactive", ""));
        int page = Sanitize.intVal(RequestHelper.getParam(request, "page", "1"), 1);
        Patient.Page result = new Patient().search(q, inactive, page, PER_PAGE);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("q", q);
        data.put("inactive", inactive);
        data.put("items", result.items());
        data.put("total", result.total());
        data.put("page", result.page());
        data.put("total_pages", result.totalPages());
        jsonSuccess(response, data, null);
    }

    public void show(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = routeId(request);
        Map<String, Object> p = id > 0 ? new Patient().byId(id) : null;
        if (p == null) {
            notFound(response, "Paciente não encontrado.");
            return;
        }
        audit(request, "paciente.ficha_visualizada", "pacientes", id, id, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patient", p);
        data.put("history", new Patient().administrativeHistory(id));
        data.put("allergies", new models.Encounter().allergies(id));
        data.put("vaccines", new models.Vaccine().card(id).get("administered"));
        PatientHistoryController.AccessLevel level = PatientHistoryController.level(RequestHelper.getSessionUserGroups(request),
                TriageController.professionalId(request, "medico"), TriageController.professionalId(request, "enfermeiro"), id);
        boolean clinical = level == PatientHistoryController.AccessLevel.DOCTOR || level == PatientHistoryController.AccessLevel.NURSING;
        List<Map<String, Object>> triages = clinical ? new models.Triage().ofPatient(id) : List.of();
        data.put("clinical_access", clinical);
        data.put("last_triage", triages.isEmpty() ? null : triages.get(0));
        data.put("background", clinical ? new models.PatientBackground().of(id) : null);
        jsonSuccess(response, data, null);
    }

    public void create(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Patient.Result r = new Patient().create(getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            respondErrors(response, r.errors());
            return;
        }
        audit(request, "paciente.criado", "pacientes", r.id(), (int) r.id(), null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_paciente", r.id());
        jsonSuccess(response, data, "Paciente cadastrado.");
    }

    public void update(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = routeId(request);
        if (id <= 0 || new Patient().byId(id) == null) {
            notFound(response, "Paciente não encontrado.");
            return;
        }
        Patient.Result r = new Patient().update(id, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            respondErrors(response, r.errors());
            return;
        }
        audit(request, "paciente.atualizado", "pacientes", id, id, null);
        jsonSuccess(response, null, "Cadastro atualizado.");
    }

    public void status(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = routeId(request);
        if (id <= 0 || new Patient().byId(id) == null) {
            notFound(response, "Paciente não encontrado.");
            return;
        }
        boolean active = "1".equals(RequestHelper.getParam(request, "active", "0"));
        new Patient().setActive(id, active, RequestHelper.getSessionUserId(request));
        audit(request, active ? "paciente.reativado" : "paciente.inativado", "pacientes", id, id, null);
        jsonSuccess(response, null, active ? "Paciente reativado." : "Paciente inativado.");
    }

    public void search(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = RequestHelper.getParam(request, "q", "").trim();
        if (q.length() < 2) {
            jsonSuccess(response, List.of(), "Digite ao menos 2 caracteres.");
            return;
        }
        List<Map<String, Object>> items = new Patient().search(q, false, 1, 8).items();
        for (Map<String, Object> i : items) {
            i.put("cpf", utils.Validation.maskedCpf(String.valueOf(i.get("cpf"))));
        }
        jsonSuccess(response, items, null);
    }

    static int routeId(HttpServletRequest request) {
        return Sanitize.intVal(RequestHelper.getRouteParams(request).get("id"), 0);
    }

    static void respondErrors(HttpServletResponse response, Map<String, String> errors) throws IOException {
        JsonResponse.error(response, 422, "ERR_VALIDATION", "Revise os campos destacados.", errors);
    }
}
