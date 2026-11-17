package controllers;

import database.Sql;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Appointments;
import models.ExamSchedule;
import models.AuditLogQuery;
import models.Notifications;
import models.MedicalOrder;
import models.PatientPortal;
import models.Prescription;
import models.Vaccine;
import utils.JsonResponse;
import utils.RateLimiter;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "PatientPortalController", urlPatterns = {"/api/public/*", "/api/portal/*"})
public class PatientPortalController extends Controller {

    private static final long serialVersionUID = 1L;
    private static final RateLimiter REGISTRATION_LIMIT = new RateLimiter(5, 15 * 60 * 1000L);
    private static final RateLimiter CODE_LIMIT = new RateLimiter(10, 15 * 60 * 1000L);

    public void catalog(HttpServletRequest request, HttpServletResponse response) throws IOException {
        jsonSuccess(response, new PatientPortal().catalog(), null);
    }

    public void registration(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!REGISTRATION_LIMIT.tryAcquire(request.getRemoteAddr())) {
            jsonError(response, 429, "ERR_TOO_MANY_REQUESTS", "Muitas tentativas. Aguarde alguns minutos.");
            return;
        }
        PatientPortal.Registration c = new PatientPortal().register(getPayload(request));
        if (!c.result().ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", c.result().error(), c.result().errors());
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (c.code() != null) {
            Notifications.linkCode(c.recordEmail(), c.name(), c.code(), (int) c.result().id());
            data.put("link_pending", true);
            data.put("record_email", AuditLogQuery.maskedEmail(c.recordEmail()));
            jsonSuccess(response, data, "Conta criada. Seu CPF já tinha cadastro: enviamos um código para o e-mail da ficha.");
            return;
        }
        data.put("link_pending", false);
        jsonSuccess(response, data, "Conta criada. Entre com o seu e-mail e senha.");
    }

    public void summary(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        Integer patientId = PatientPortal.userPatient(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("linked", patientId != null);
        if (patientId == null) {
            Map<String, Object> v = Sql.one("SELECT p.email FROM vinculos_portal v JOIN pacientes p ON p.id_paciente = v.id_paciente "
                    + "WHERE v.id_usuario = ? ORDER BY v.id_vinculo DESC LIMIT 1", userId);
            data.put("record_email", v == null ? null : AuditLogQuery.maskedEmail(v.get("email")));
            jsonSuccess(response, data, null);
            return;
        }
        data.putAll(new PatientPortal().summary(userId, patientId));
        jsonSuccess(response, data, null);
    }

    public void link(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        if (!CODE_LIMIT.tryAcquire("u" + userId)) {
            jsonError(response, 429, "ERR_TOO_MANY_REQUESTS", "Muitas tentativas. Aguarde alguns minutos.");
            return;
        }
        respond(response, new PatientPortal().link(userId, RequestHelper.getParam(request, "code", "")), "Pronto! Sua conta está ligada à sua ficha.");
    }

    public void resend(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        String[] r = new PatientPortal().resendCode(userId);
        if (r == null) {
            jsonError(response, 409, "ERR_NO_PENDING_LINK", "Não há vínculo pendente para esta conta.");
            return;
        }
        if (r[0] == null) {
            jsonError(response, 429, "ERR_TOO_MANY_REQUESTS", "Você já pediu vários códigos. Tente de novo em uma hora.");
            return;
        }
        Notifications.linkCode(r[1], String.valueOf(RequestHelper.getSessionUser(request).get("nome")), r[0], userId);
        jsonSuccess(response, Map.of("record_email", AuditLogQuery.maskedEmail(r[1])), "Enviamos um novo código.");
    }

    public void appointments(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("appointments", new PatientPortal().appointments(patientId));
        data.put("specialties", new Appointments().activeSpecialties());
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    public void bookAppointment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        int userId = RequestHelper.getSessionUserId(request);
        LocalDateTime when = AppointmentController.dateTime(request);
        int specialty = Sanitize.intVal(RequestHelper.getParam(request, "id_especialidade", "0"), 0);
        int doctor = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        if (specialty <= 0 || doctor <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha especialidade, médico, data e horário.");
            return;
        }
        PatientPortal.Result r = new PatientPortal().bookAppointment(patientId, doctor, specialty, when, userId);
        if (r.ok()) {
            Notifications.scheduledAppointment(new Appointments().noticeSummary(r.id()), r.id(), userId);
        }
        respond(response, r, "Consulta marcada. Você recebe a confirmação por e-mail.");
    }

    public void confirmAppointment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        respond(response, new PatientPortal().confirmAppointment(PatientController.routeId(request), patientId, RequestHelper.getSessionUserId(request)),
                "Presença confirmada.");
    }

    public void cancelAppointment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        int id = PatientController.routeId(request);
        int userId = RequestHelper.getSessionUserId(request);
        if (!PatientPortal.patientAppointment(id, patientId) || Sql.one("SELECT 1 FROM consultas WHERE id_consulta = ? AND status = 'agendada'", id) == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Consulta não encontrada ou já em andamento.");
            return;
        }
        Appointments.Result r = new Appointments().cancel(id, RequestHelper.getParam(request, "reason", ""), userId);
        if (r.ok()) {
            Notifications.cancelledAppointment(new Appointments().noticeSummary(id), id, userId);
        }
        respondSchedule(response, r, "Consulta cancelada.");
    }

    public void rescheduleAppointment(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        int id = PatientController.routeId(request);
        int userId = RequestHelper.getSessionUserId(request);
        LocalDateTime when = AppointmentController.dateTime(request);
        int doctor = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        if (!PatientPortal.patientAppointment(id, patientId)) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Consulta não encontrada.");
            return;
        }
        if (doctor <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o médico, a nova data e o horário.");
            return;
        }
        Appointments.Result r = new Appointments().reschedule(id, doctor, when, RequestHelper.getParam(request, "reason", "pelo portal"), userId);
        if (r.ok()) {
            Sql.execute("UPDATE consultas SET origem = 'portal' WHERE id_consulta = ?", r.id());
            Notifications.rescheduledAppointment(new Appointments().noticeSummary(r.id()), r.id(), userId);
        }
        respondSchedule(response, r, "Consulta remarcada.");
    }

    public void exams(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        PatientPortal p = new PatientPortal();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exams", p.exams(patientId));
        data.put("exam_requests", p.openRequests(patientId));
        data.put("medical_orders", new MedicalOrder().ofPatient(patientId));
        data.put("types", Sql.list("SELECT t.id_tipo_exame, t.nome, t.categoria, t.duracao_min, t.preparo, t.exige_pedido, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 AND l.ativo = 1 ORDER BY t.categoria, t.nome"));
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    public void bookExam(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        int userId = RequestHelper.getSessionUserId(request);
        int type = Sanitize.intVal(RequestHelper.getParam(request, "id_tipo_exame", "0"), 0);
        int requestId = Sanitize.intVal(RequestHelper.getParam(request, "id_solicitacao", "0"), 0);
        int orderId = Sanitize.intVal(RequestHelper.getParam(request, "id_pedido", "0"), 0);
        LocalDateTime when = AppointmentController.dateTime(request);
        if (type <= 0 || when == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o exame, a data e o horário.");
            return;
        }
        PatientPortal.Result r = new PatientPortal().bookExam(patientId, type, when, requestId > 0 ? requestId : null, orderId > 0 ? orderId : null, userId);
        if (r.ok()) {
            Notifications.scheduledExam(new ExamSchedule().noticeSummary(r.id()), userId);
        }
        respond(response, r, orderId > 0 ? "Exame marcado. A recepção vai conferir o pedido anexado." : "Exame marcado.");
    }

    public void cancelExam(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        int id = PatientController.routeId(request);
        if (!PatientPortal.patientExam(id, patientId)) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Exame não encontrado.");
            return;
        }
        respondSchedule(response, new ExamSchedule().cancel(id, RequestHelper.getParam(request, "reason", ""),
                RequestHelper.getSessionUserId(request)), "Exame cancelado.");
    }

    public void sendOrder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        PatientPortal.Result r = new MedicalOrder().send(patientId, getPayload(request), RequestHelper.getSessionUserId(request));
        if (!r.ok()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", r.error(), r.errors());
            return;
        }
        jsonSuccess(response, Map.of("id_pedido", r.id()), "Pedido anexado. A recepção confere em seguida.");
    }

    public void orderFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        sendFile(response, new MedicalOrder().file(PatientController.routeId(request), patientId));
    }

    static void sendFile(HttpServletResponse response, MedicalOrder.StoredFile a) throws IOException {
        if (a == null) {
            JsonResponse.error(response, 404, "ERR_NOT_FOUND", "Arquivo não encontrado.", null);
            return;
        }
        response.setContentType(a.mime());
        response.setHeader("Content-Disposition", "attachment; filename=\"pedido\"; filename*=UTF-8''"
                + java.net.URLEncoder.encode(a.name(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'");
        response.setHeader("Cache-Control", "private, no-store");
        response.setContentLengthLong(a.file().length());
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(a.file().toPath(), out);
        }
    }

    public void vaccines(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        Vaccine v = new Vaccine();
        Map<String, Object> data = new LinkedHashMap<>(v.card(patientId));
        data.put("catalog", v.catalog());
        data.put("today", Appointments.now().toLocalDate());
        jsonSuccess(response, data, null);
    }

    public void bookVaccine(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        int vaccine = Sanitize.intVal(RequestHelper.getParam(request, "id_vacina", "0"), 0);
        respondSchedule(response, new Vaccine().book(patientId, vaccine, AppointmentController.dateTime(request), RequestHelper.getSessionUserId(request), "portal"),
                "Vacina agendada. Leve um documento com foto.");
    }

    public void cancelVaccine(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        respondSchedule(response, new Vaccine().cancel(PatientController.routeId(request), RequestHelper.getParam(request, "reason", ""),
                RequestHelper.getSessionUserId(request), patientId), "Agendamento cancelado.");
    }

    public void prescriptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        audit(request, "portal.receitas_visualizadas", "pacientes", patientId, patientId, null);
        jsonSuccess(response, Map.of("prescriptions", new Prescription().ofPatient(patientId)), null);
    }

    public void prescription(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = patient(request, response);
        if (patientId == null) {
            return;
        }
        Map<String, Object> r = new Prescription().forPatientPrint(PatientController.routeId(request), patientId);
        if (r == null) {
            jsonError(response, 404, "ERR_NOT_FOUND", "Receita não encontrada.");
            return;
        }
        audit(request, "portal.receita_visualizada", "receitas", r.get("id_receita"), patientId, null);
        jsonSuccess(response, Map.of("prescription", r), null);
    }

    public void linkInReception(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, new PatientPortal().linkInReception(PatientController.routeId(request), RequestHelper.getParam(request, "email", ""),
                RequestHelper.getSessionUserId(request)), "Conta do portal ligada à ficha.");
    }

    private Integer patient(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer patientId = PatientPortal.userPatient(RequestHelper.getSessionUserId(request));
        if (patientId == null) {
            jsonError(response, 409, "ERR_NO_PATIENT_RECORD", "Sua conta ainda não está ligada a uma ficha. Confirme o código enviado por e-mail ou procure a recepção.");
        }
        return patientId;
    }

    private void respond(HttpServletResponse response, PatientPortal.Result r, String ok) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, Map.of("id", r.id()), ok);
        } else if (!r.errors().isEmpty()) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", r.error(), r.errors());
        } else {
            jsonError(response, 409, "ERR_PORTAL", r.error());
        }
    }

    private void respondSchedule(HttpServletResponse response, Appointments.Result r, String ok) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, Map.of("id", r.id()), ok);
        } else {
            jsonError(response, 409, "ERR_SCHEDULE", r.error());
        }
    }
}
