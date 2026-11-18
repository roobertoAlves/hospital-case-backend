package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Notifications;
import models.Professional;
import models.Specialty;
import utils.RequestHelper;

@WebServlet(name = "ProfessionalController", urlPatterns = {"/api/professionals", "/api/professionals/*",
        "/api/specialties", "/api/specialties/*"})
public class ProfessionalController extends Controller {

    private static final long serialVersionUID = 1L;

    public void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String q = RequestHelper.getParam(request, "q", "").trim();
        String type = RequestHelper.getParam(request, "type", "");
        boolean inactive = "1".equals(RequestHelper.getParam(request, "inactive", ""));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("q", q);
        data.put("type", Professional.TYPES.contains(type) ? type : "");
        data.put("inactive", inactive);
        data.put("professionals", new Professional().list(q, type, inactive));
        jsonSuccess(response, data, null);
    }

    public void options(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> p = new LinkedHashMap<>();
        String type = RequestHelper.getParam(request, "type", "medico");
        p.put("tipo", Professional.TYPES.contains(type) ? type : "medico");
        p.put("duracao_consulta_min", 30);
        p.put("specialty_ids", new ArrayList<Integer>());
        jsonSuccess(response, formData(p, null), null);
    }

    public void show(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        Map<String, Object> p = id > 0 ? new Professional().byId(id) : null;
        if (p == null) {
            notFound(response, "Profissional não encontrado.");
            return;
        }
        Object userId = p.get("id_usuario");
        jsonSuccess(response, formData(p, userId instanceof Number ? ((Number) userId).intValue() : null), null);
    }

    public void create(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int loggedInId = RequestHelper.getSessionUserId(request);
        Map<String, Object> payload = getPayload(request);
        Professional.Result r = new Professional().create(payload, loggedInId);
        if (!r.ok()) {
            PatientController.respondErrors(response, r.errors());
            return;
        }
        String message = "Profissional cadastrado.";
        if (r.temporaryPassword() != null) {
            String type = String.valueOf(payload.get("tipo"));
            Notifications.createdAccess(r.accessEmail(), String.valueOf(payload.get("nome")),
                    roleLabel(Professional.typeGroup(type)), r.temporaryPassword(), loggedInId);
            message = "Profissional cadastrado. A senha temporária foi enviada para " + r.accessEmail() + ".";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_profissional", r.id());
        jsonSuccess(response, data, message);
    }

    public void update(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        Professional.Result r = new Professional().update(id, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            if (r.errors().containsKey("_")) {
                notFound(response, r.errors().get("_"));
                return;
            }
            PatientController.respondErrors(response, r.errors());
            return;
        }
        jsonSuccess(response, null, "Cadastro atualizado.");
    }

    public void status(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        if (id <= 0 || new Professional().byId(id) == null) {
            notFound(response, "Profissional não encontrado.");
            return;
        }
        boolean active = "1".equals(RequestHelper.getParam(request, "active", "0"));
        String blocker = new Professional().setActive(id, active, RequestHelper.getSessionUserId(request));
        if (blocker != null) {
            jsonError(response, 409, "ERR_FUTURE_APPOINTMENTS", blocker);
            return;
        }
        audit(request, active ? "profissional.reativado" : "profissional.inativado", "profissionais", id, null, null);
        jsonSuccess(response, null, active ? "Profissional reativado." : "Profissional inativado.");
    }

    public void availability(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        Object raw = getPayload(request).get("ranges");
        List<Map<String, Object>> ranges = new ArrayList<>();
        if (raw instanceof List<?>) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map<?, ?>) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                        f.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    ranges.add(f);
                }
            }
        }
        Map<String, String> errors = new Professional().saveAvailability(id, ranges, RequestHelper.getSessionUserId(request));
        if (errors.containsKey("_")) {
            jsonError(response, 400, "ERR_VALIDATION", errors.get("_"));
            return;
        }
        if (!errors.isEmpty()) {
            PatientController.respondErrors(response, errors);
            return;
        }
        jsonSuccess(response, null, ranges.isEmpty() ? "Disponibilidade removida: o médico não recebe novos agendamentos."
                : "Disponibilidade salva.");
    }

    public void specialties(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("specialties", new Specialty().list());
        jsonSuccess(response, data, null);
    }

    public void saveSpecialty(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        if (id > 0 && new Specialty().byId(id) == null) {
            notFound(response, "Especialidade não encontrada.");
            return;
        }
        long[] generated = new long[1];
        Map<String, String> errors = new Specialty().save(id, getPayload(request), RequestHelper.getSessionUserId(request), generated);
        if (!errors.isEmpty()) {
            PatientController.respondErrors(response, errors);
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_especialidade", id > 0 ? id : generated[0]);
        jsonSuccess(response, data, id > 0 ? "Especialidade atualizada." : "Especialidade cadastrada.");
    }

    public void specialtyStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        if (id <= 0 || new Specialty().byId(id) == null) {
            notFound(response, "Especialidade não encontrada.");
            return;
        }
        boolean active = "1".equals(RequestHelper.getParam(request, "active", "0"));
        String blocker = new Specialty().setActive(id, active);
        if (blocker != null) {
            jsonError(response, 409, "ERR_FUTURE_APPOINTMENTS", blocker);
            return;
        }
        audit(request, active ? "especialidade.reativada" : "especialidade.inativada", "especialidades", id, null, null);
        jsonSuccess(response, null, active ? "Especialidade reativada." : "Especialidade inativada.");
    }

    private static Map<String, Object> formData(Map<String, Object> professional, Integer currentUserId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("professional", professional);
        data.put("specialties", new Specialty().active());
        data.put("users", new Professional().usersWithoutLink(currentUserId));
        return data;
    }

    static String roleLabel(String group) {
        switch (group) {
            case "medico":
                return "Médico";
            case "enfermeiro":
                return "Enfermagem";
            default:
                return "Recepção";
        }
    }
}
