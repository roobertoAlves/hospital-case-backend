package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Appointments;
import models.Notifications;
import models.Professional;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validation;

@WebServlet(name = "AppointmentController", urlPatterns = {"/api/appointments", "/api/appointments/*"})
public class AppointmentController extends Controller {

    private static final long serialVersionUID = 1L;

    public void day(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LocalDate day = dateParam(request);
        Integer ownDoctorId = ownScheduleDoctorId(request);
        Appointments schedule = new Appointments();
        List<Map<String, Object>> appointments = new ArrayList<>();
        for (Map<String, Object> c : schedule.ofDay(day, ownDoctorId)) {
            appointments.add(Appointments.withTime(c));
        }
        List<Map<String, Object>> doctors = schedule.doctorsOnDay(day);
        if (ownDoctorId != null) {
            doctors.removeIf(m -> ((Number) m.get("id_profissional")).intValue() != ownDoctorId);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day);
        data.put("appointments", appointments);
        data.put("doctors", doctors);
        data.put("own_schedule", ownDoctorId != null);
        jsonSuccess(response, data, null);
    }

    public void options(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("specialties", new Appointments().activeSpecialties());
        data.put("patient", patientFromParameter(request));
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    public void doctors(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int specialty = Sanitize.intVal(RequestHelper.getParam(request, "specialty", "0"), 0);
        jsonSuccess(response, new Appointments().specialtyDoctors(specialty), null);
    }

    public void slots(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int doctor = Sanitize.intVal(RequestHelper.getParam(request, "doctor", "0"), 0);
        LocalDate day = Validation.date(RequestHelper.getParam(request, "date", ""));
        if (doctor <= 0 || day == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o médico e a data.");
            return;
        }
        jsonSuccess(response, slotsJson(new Appointments().slots(doctor, day)), null);
    }

    public void book(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        int patient = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int specialty = Sanitize.intVal(RequestHelper.getParam(request, "id_especialidade", "0"), 0);
        int doctor = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        LocalDateTime when = dateTime(request);
        if (patient <= 0 || specialty <= 0 || doctor <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Preencha paciente, especialidade, médico, data e horário.");
            return;
        }
        Appointments.Result r = new Appointments().book(patient, doctor, specialty, when, userId);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_SCHEDULE", r.error());
            return;
        }
        Notifications.scheduledAppointment(new Appointments().noticeSummary(r.id()), r.id(), userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_consulta", r.id());
        data.put("date", when.toLocalDate());
        jsonSuccess(response, data, "Consulta agendada.");
    }

    public void checkIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Appointments.Result r = new Appointments().recordArrival(PatientController.routeId(request),
                RequestHelper.getSessionUserId(request));
        respond(response, r, "Chegada registrada. O paciente entrou na fila de triagem.");
    }

    public void cancel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        int userId = RequestHelper.getSessionUserId(request);
        Appointments.Result r = new Appointments().cancel(id, RequestHelper.getParam(request, "reason", ""), userId);
        if (r.ok()) {
            Notifications.cancelledAppointment(new Appointments().noticeSummary(id), id, userId);
        }
        respond(response, r, "Consulta cancelada.");
    }

    public void rescheduleOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> c = new Appointments().forReschedule(PatientController.routeId(request));
        if (c == null) {
            notFound(response, "Consulta não encontrada.");
            return;
        }
        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("id_paciente", c.get("id_paciente"));
        patient.put("nome", c.get("paciente"));
        patient.put("cpf", c.get("cpf"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("specialties", new Appointments().activeSpecialties());
        data.put("original", Appointments.withTime(c));
        data.put("patient", patient);
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    public void reschedule(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        int userId = RequestHelper.getSessionUserId(request);
        int doctor = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        LocalDateTime when = dateTime(request);
        if (doctor <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o médico, a nova data e o horário.");
            return;
        }
        Appointments.Result r = new Appointments().reschedule(id, doctor, when,
                RequestHelper.getParam(request, "reason", ""), userId);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_SCHEDULE", r.error());
            return;
        }
        Notifications.rescheduledAppointment(new Appointments().noticeSummary(r.id()), r.id(), userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_consulta", r.id());
        data.put("date", when.toLocalDate());
        jsonSuccess(response, data, "Consulta remarcada. O paciente recebe o aviso com o novo horário.");
    }

    private static Integer ownScheduleDoctorId(HttpServletRequest request) {
        Map<String, Object> user = RequestHelper.getSessionUser(request);
        Object perms = user != null ? user.get("permissoes") : null;
        boolean all = perms instanceof List<?> && ((List<?>) perms).contains("agenda.view");
        if (all) {
            return null;
        }
        Map<String, Object> professional = new Professional().byUser(RequestHelper.getSessionUserId(request));
        return professional != null ? ((Number) professional.get("id_profissional")).intValue() : -1;
    }

    static LocalDate dateParam(HttpServletRequest request) {
        LocalDate d = Validation.date(RequestHelper.getParam(request, "date", ""));
        return d != null ? d : Appointments.now().toLocalDate();
    }

    static LocalDateTime dateTime(HttpServletRequest request) {
        LocalDate d = Validation.date(RequestHelper.getParam(request, "date", ""));
        String t = RequestHelper.getParam(request, "time", "");
        if (d == null || !t.matches("^\\d{2}:\\d{2}$")) {
            return null;
        }
        try {
            return d.atTime(LocalTime.parse(t));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    static List<Map<String, Object>> slotsJson(List<Appointments.Slot> slots) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Appointments.Slot s : slots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", s.time().toString());
            m.put("free", s.free());
            out.add(m);
        }
        return out;
    }

    static Map<String, Object> patientFromParameter(HttpServletRequest request) {
        int id = Sanitize.intVal(RequestHelper.getParam(request, "patient", "0"), 0);
        if (id <= 0) {
            return null;
        }
        Map<String, Object> p = new models.Patient().byId(id);
        return p != null && ((Number) p.get("ativo")).intValue() == 1 ? p : null;
    }

    private void respond(HttpServletResponse response, Appointments.Result r, String message) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, null, message);
        } else {
            jsonError(response, 409, "ERR_SCHEDULE", r.error());
        }
    }
}
