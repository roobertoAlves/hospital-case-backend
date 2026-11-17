package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Encounter;
import models.Professional;
import models.Triage;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "TriageController", urlPatterns = {"/api/triage", "/api/triage/*", "/api/triages/*"})
public class TriageController extends Controller {

    private static final long serialVersionUID = 1L;

    public void queue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Triage t = new Triage();
        int nurseId = nurseId(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("queue", t.queue());
        data.put("recent", nurseId > 0 ? t.nurseRecent(nurseId) : List.of());
        data.put("classifications", t.classifications());
        jsonSuccess(response, data, null);
    }

    public void form(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int appointmentId = PatientController.routeId(request);
        Triage t = new Triage();
        Map<String, Object> appointment = appointmentId > 0 ? t.appointment(appointmentId) : null;
        if (appointment == null) {
            notFound(response, "Consulta não encontrada.");
            return;
        }
        String status = String.valueOf(appointment.get("status"));
        Map<String, Object> current = t.current(appointmentId);
        boolean canRecord = "em_triagem".equals(status) && current == null;
        boolean canAmend = current != null && ("aguardando_atendimento".equals(status) || "em_atendimento".equals(status));
        if (!canRecord && !canAmend && current == null) {
            jsonError(response, 409, "ERR_STEP", "Esta triagem não está disponível nesta etapa.");
            return;
        }
        int patientId = ((Number) appointment.get("id_paciente")).intValue();
        audit(request, "triagem.tela_aberta", "consultas", appointmentId, patientId, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appointment", appointment);
        data.put("triage", current);
        data.put("can_record", canRecord);
        data.put("can_amend", canAmend);
        data.put("classifications", t.classifications());
        data.put("allergies", new Encounter().allergies(patientId));
        jsonSuccess(response, data, null);
    }

    public void start(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int appointmentId = PatientController.routeId(request);
        if (!new Triage().start(appointmentId, RequestHelper.getSessionUserId(request))) {
            jsonError(response, 409, "ERR_CONFLICT", "Outro enfermeiro já iniciou esta triagem ou o paciente saiu da fila.");
            return;
        }
        jsonSuccess(response, null, "Triagem iniciada.");
    }

    public void sendBack(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int appointmentId = PatientController.routeId(request);
        if (!new Triage().sendBack(appointmentId, RequestHelper.getSessionUserId(request))) {
            jsonError(response, 409, "ERR_CONFLICT", "A triagem não está em andamento.");
            return;
        }
        jsonSuccess(response, null, "Paciente devolvido à fila.");
    }

    public void record(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int nurseId = nurseId(request);
        if (nurseId <= 0) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Seu usuário não está ligado a um cadastro de enfermeiro.");
            return;
        }
        int appointmentId = PatientController.routeId(request);
        Triage.Result r = new Triage().record(appointmentId, nurseId, getPayload(request),
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", firstOrDefault(r.errors()), r.errors());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_triagem", r.id());
        jsonSuccess(response, data, "Triagem registrada. O paciente entrou na fila do médico.");
    }

    public void amend(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int nurseId = nurseId(request);
        if (nurseId <= 0) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Seu usuário não está ligado a um cadastro de enfermeiro.");
            return;
        }
        int triageId = PatientController.routeId(request);
        Map<String, Object> payload = getPayload(request);
        Triage.Result r = new Triage().amend(triageId, nurseId, payload,
                Sanitize.string(payload.get("motivo_retificacao")), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", firstOrDefault(r.errors()), r.errors());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_triagem", r.id());
        jsonSuccess(response, data, "Triagem retificada. A versão anterior continua no histórico.");
    }

    static int professionalId(HttpServletRequest request, String type) {
        int userId = RequestHelper.getSessionUserId(request);
        Map<String, Object> p = userId > 0 ? new Professional().byUser(userId) : null;
        if (p == null || !type.equals(p.get("tipo"))) {
            return 0;
        }
        return ((Number) p.get("id_profissional")).intValue();
    }

    private static int nurseId(HttpServletRequest request) {
        return professionalId(request, "enfermeiro");
    }

    static String firstOrDefault(Map<String, String> errors) {
        String general = errors.get("general");
        return general != null ? general : "Revise os campos destacados.";
    }
}
