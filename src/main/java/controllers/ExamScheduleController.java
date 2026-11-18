package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Appointments;
import models.ExamSchedule;
import models.Notifications;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validation;

@WebServlet(name = "ExamScheduleController", urlPatterns = {"/api/exams", "/api/exams/*", "/api/exam-catalog", "/api/exam-catalog/*"})
public class ExamScheduleController extends Controller {

    private static final long serialVersionUID = 1L;

    public void day(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate day = AppointmentController.dateParam(request);
        ExamSchedule schedule = new ExamSchedule();
        List<Map<String, Object>> exams = new ArrayList<>();
        for (Map<String, Object> x : schedule.ofDay(day)) {
            exams.add(Appointments.withTime(x));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day);
        data.put("exams", exams);
        data.put("locations", schedule.activeLocations());
        jsonSuccess(response, data, null);
    }

    public void options(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("types", new ExamSchedule().activeTypes());
        Object patient = AppointmentController.patientFromParameter(request);
        data.put("patient", patient);
        int patientId = Sanitize.intVal(RequestHelper.getParam(request, "patient", "0"), 0);
        data.put("open_requests", patientId > 0 ? new models.PatientPortal().openRequests(patientId) : java.util.List.of());
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    public void slots(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int type = Sanitize.intVal(RequestHelper.getParam(request, "type", "0"), 0);
        LocalDate day = Validation.date(RequestHelper.getParam(request, "date", ""));
        if (type <= 0 || day == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o exame e a data.");
            return;
        }
        jsonSuccess(response, AppointmentController.slotsJson(new ExamSchedule().slots(type, day)), null);
    }

    public void book(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        int patient = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int type = Sanitize.intVal(RequestHelper.getParam(request, "id_tipo_exame", "0"), 0);
        LocalDateTime when = AppointmentController.dateTime(request);
        if (patient <= 0 || type <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Preencha paciente, exame, data e horário.");
            return;
        }
        int requestId = Sanitize.intVal(RequestHelper.getParam(request, "id_solicitacao", "0"), 0);
        Appointments.Result r = new ExamSchedule().book(patient, type, when, requestId > 0 ? requestId : null, userId);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_SCHEDULE", r.error());
            return;
        }
        Notifications.scheduledExam(new ExamSchedule().noticeSummary(r.id()), userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_exame_agendado", r.id());
        data.put("date", when.toLocalDate());
        jsonSuccess(response, data, "Exame agendado.");
    }

    public void cancel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Appointments.Result r = new ExamSchedule().cancel(PatientController.routeId(request),
                RequestHelper.getParam(request, "reason", ""), RequestHelper.getSessionUserId(request));
        respond(response, r, "Exame cancelado.");
    }

    public void status(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String s = RequestHelper.getParam(request, "status", "");
        Appointments.Result r = new ExamSchedule().recordStatus(PatientController.routeId(request), s,
                RequestHelper.getSessionUserId(request));
        respond(response, r, "realizado".equals(s) ? "Exame marcado como realizado." : "Ausência registrada.");
    }

    private void respond(HttpServletResponse response, Appointments.Result r, String message) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, null, message);
        } else {
            jsonError(response, 409, "ERR_SCHEDULE", r.error());
        }
    }

    public void catalogList(HttpServletRequest request, HttpServletResponse response) throws IOException {
        models.ExamCatalog c = new models.ExamCatalog();
        Map<String, Object> data = new LinkedHashMap<>(c.options());
        data.put("items", c.all());
        jsonSuccess(response, data, null);
    }

    public void catalogCreate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        catalogSave(request, response, 0);
    }

    public void catalogUpdate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        if (id <= 0 || !new models.ExamCatalog().exists(id)) {
            notFound(response, "Exame não encontrado no catálogo.");
            return;
        }
        catalogSave(request, response, id);
    }

    public void catalogStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        if (id <= 0 || !new models.ExamCatalog().exists(id)) {
            notFound(response, "Exame não encontrado no catálogo.");
            return;
        }
        boolean active = "1".equals(RequestHelper.getParam(request, "active", "0"));
        new models.ExamCatalog().setActive(id, active, RequestHelper.getSessionUserId(request));
        jsonSuccess(response, null, active ? "Exame reativado no catálogo." : "Exame desativado: não aparece mais para agendar ou solicitar.");
    }

    private void catalogSave(HttpServletRequest request, HttpServletResponse response, int id) throws IOException {
        models.ExamCatalog.Result r = new models.ExamCatalog().save(id, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            PatientController.respondErrors(response, r.errors());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_tipo_exame", r.id());
        jsonSuccess(response, data, id > 0 ? "Exame atualizado." : "Exame cadastrado no catálogo.");
    }
}
