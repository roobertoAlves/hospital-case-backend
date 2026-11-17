package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Encounter;
import models.ExamRequest;
import models.Prescription;
import models.Triage;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "EncounterController",
        urlPatterns = {"/api/encounters", "/api/encounters/*", "/api/prescriptions/*", "/api/exam-requests/*"})
public class EncounterController extends Controller {

    private static final long serialVersionUID = 1L;

    public void queue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = doctorId(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("queue", doctorId > 0 ? new Encounter().doctorQueue(doctorId) : List.of());
        data.put("no_professional_record", doctorId <= 0);
        jsonSuccess(response, data, null);
    }

    public void show(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int appointmentId = PatientController.routeId(request);
        Encounter e = new Encounter();
        Map<String, Object> c = appointmentId > 0 ? e.doctorAppointment(appointmentId, doctorId(request)) : null;
        if (c == null) {
            notFound(response, "Consulta não encontrada.");
            return;
        }
        String status = String.valueOf(c.get("status"));
        if (!"em_atendimento".equals(status) && !"realizada".equals(status) && !"aguardando_atendimento".equals(status)) {
            jsonError(response, 409, "ERR_STEP", "Este atendimento não está disponível nesta etapa.");
            return;
        }
        int patientId = ((Number) c.get("id_paciente")).intValue();
        audit(request, "prontuario.leitura", "consultas", appointmentId, patientId, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appointment", c);
        data.put("triage", new Triage().current(appointmentId));
        data.put("allergies", e.allergies(patientId));
        data.put("background", new models.PatientBackground().of(patientId));
        data.put("progress_notes", e.progressNotes(appointmentId));
        data.put("versions", e.versionCount(appointmentId));
        data.put("prescriptions", new Prescription().ofAppointment(appointmentId));
        data.put("exam_requests", new ExamRequest().ofAppointment(appointmentId));
        data.put("previous_records", e.patientMedicalRecords(patientId).stream()
                .filter(p -> ((Number) p.get("id_consulta")).intValue() != appointmentId).limit(5).toList());
        jsonSuccess(response, data, null);
    }

    public void start(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Encounter.Result r = new Encounter().start(PatientController.routeId(request), doctorId,
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 409, "ERR_CONFLICT", r.error());
            return;
        }
        jsonSuccess(response, null, "Atendimento iniciado.");
    }

    public void saveDraft(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Encounter.Result r = new Encounter().saveDraft(PatientController.routeId(request), doctorId,
                getPayload(request), RequestHelper.getSessionUserId(request));
        respond(response, r, "Rascunho salvo.");
    }

    public void finish(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Encounter.Result r = new Encounter().finish(PatientController.routeId(request), doctorId, getPayload(request),
                RequestHelper.getSessionUserId(request));
        respond(response, r, "Atendimento finalizado.");
    }

    public void progressNote(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Map<String, Object> d = getPayload(request);
        Encounter.Result r = new Encounter().recordProgressNote(PatientController.routeId(request), doctorId,
                Sanitize.string(d.get("tipo")), Sanitize.string(d.get("texto")), RequestHelper.getSessionUserId(request));
        respond(response, r, "Registro incluído.");
    }

    public void saveBackground(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Map<String, Object> c = new Encounter().doctorAppointment(PatientController.routeId(request), doctorId);
        if (c == null || !("em_atendimento".equals(c.get("status")) || "realizada".equals(c.get("status")))) {
            jsonError(response, 409, "ERR_CONFLICT", "Atualize os antecedentes durante ou depois do atendimento.");
            return;
        }
        Map<String, String> errors = new models.PatientBackground().save(((Number) c.get("id_paciente")).intValue(),
                getPayload(request), RequestHelper.getSessionUserId(request));
        if (!errors.isEmpty()) {
            PatientController.respondErrors(response, errors);
            return;
        }
        jsonSuccess(response, null, "Antecedentes atualizados.");
    }

    public void allergy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        int appointmentId = PatientController.routeId(request);
        Map<String, Object> c = new Encounter().doctorAppointment(appointmentId, doctorId);
        if (c == null || !"em_atendimento".equals(c.get("status"))) {
            jsonError(response, 409, "ERR_CONFLICT", "Registre alergias durante o atendimento.");
            return;
        }
        Map<String, Object> d = getPayload(request);
        Encounter.Result r = new Encounter().recordAllergy(((Number) c.get("id_paciente")).intValue(),
                Sanitize.string(d.get("substancia")), Sanitize.string(d.get("reacao")), Sanitize.string(d.get("gravidade")),
                RequestHelper.getSessionUserId(request));
        respond(response, r, "Alergia registrada.");
    }

    public void issuePrescription(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Map<String, Object> d = getPayload(request);
        Object items = d.get("items");
        Prescription.Result r = new Prescription().issue(PatientController.routeId(request), doctorId,
                Sanitize.string(d.get("orientacoes_gerais")), items instanceof List ? (List<?>) items : null,
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            String general = r.errors().getOrDefault("general", r.errors().values().iterator().next());
            JsonResponse.error(response, 422, "ERR_VALIDATION", general, r.errors());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_receita", r.id());
        jsonSuccess(response, data, "Receita emitida.");
    }

    public void cancelPrescription(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        Prescription.Result r = new Prescription().cancel(PatientController.routeId(request), doctorId,
                Sanitize.string(getPayload(request).get("reason")), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.errors().values().iterator().next());
            return;
        }
        jsonSuccess(response, null, "Receita cancelada.");
    }

    public void printPrescription(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int prescriptionId = PatientController.routeId(request);
        Map<String, Object> r = prescriptionId > 0 ? new Prescription().forPrint(prescriptionId, doctorId(request)) : null;
        if (r == null) {
            notFound(response, "Receita não encontrada.");
            return;
        }
        audit(request, "receita.impressa", "receitas", prescriptionId, ((Number) r.get("id_paciente")).intValue(), null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("prescription", r);
        jsonSuccess(response, data, null);
    }

    public void requestExam(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        ExamRequest.Result r = new ExamRequest().request(PatientController.routeId(request), doctorId, getPayload(request),
                RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.error());
            return;
        }
        jsonSuccess(response, null, "Exame solicitado.");
    }

    public void cancelExamRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctorId = requireDoctor(request, response);
        if (doctorId <= 0) {
            return;
        }
        ExamRequest.Result r = new ExamRequest().cancel(PatientController.routeId(request), doctorId,
                Sanitize.string(getPayload(request).get("reason")), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.error());
            return;
        }
        jsonSuccess(response, null, "Solicitação cancelada.");
    }

    private static int doctorId(HttpServletRequest request) {
        return TriageController.professionalId(request, "medico");
    }

    private int requireDoctor(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = doctorId(request);
        if (id <= 0) {
            jsonError(response, 403, "ERR_FORBIDDEN", "Seu usuário não está ligado a um cadastro de médico.");
        }
        return id;
    }

    private void respond(HttpServletResponse response, Encounter.Result r, String msg) throws IOException {
        if (!r.ok()) {
            jsonError(response, 422, "ERR_VALIDATION", r.error());
            return;
        }
        jsonSuccess(response, null, msg);
    }
}
