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

/**
 * ZTF_J2EE - ExamScheduleController
 *
 * Exam schedule: the day's exams by location, booking with the free slots of the exam's
 * location, cancellation and recording done/no-show. Permissions in routes.json
 * (exames.agenda.view to read, exames.agendar to change).
 */
@WebServlet(name = "ExamScheduleController", urlPatterns = {"/api/exams", "/api/exams/*"})
public class ExamScheduleController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /api/exams?date=YYYY-MM-DD */
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

    /** GET /api/exams/options?patient=ID - data for the booking form. */
    public void options(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("types", new ExamSchedule().activeTypes());
        data.put("patient", AppointmentController.patientFromParameter(request));
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    /** GET /api/exams/slots?type=ID&date=YYYY-MM-DD */
    public void slots(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int type = Sanitize.intVal(RequestHelper.getParam(request, "type", "0"), 0);
        LocalDate day = Validation.date(RequestHelper.getParam(request, "date", ""));
        if (type <= 0 || day == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o exame e a data.");
            return;
        }
        jsonSuccess(response, AppointmentController.slotsJson(new ExamSchedule().slots(type, day)), null);
    }

    /** POST /api/exams { id_paciente, id_tipo_exame, date, time } */
    public void book(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        int patient = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int type = Sanitize.intVal(RequestHelper.getParam(request, "id_tipo_exame", "0"), 0);
        LocalDateTime when = AppointmentController.dateTime(request);
        if (patient <= 0 || type <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Preencha paciente, exame, data e horário.");
            return;
        }
        Appointments.Result r = new ExamSchedule().book(patient, type, when, null, userId);
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

    /** POST /api/exams/{id}/cancel { reason } */
    public void cancel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Appointments.Result r = new ExamSchedule().cancel(PatientController.routeId(request),
                RequestHelper.getParam(request, "reason", ""), RequestHelper.getSessionUserId(request));
        respond(response, r, "Exame cancelado.");
    }

    /** POST /api/exams/{id}/status { status: realizado|ausente } */
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
}
