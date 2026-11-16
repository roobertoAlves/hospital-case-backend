package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import database.Sql;
import models.Appointments;
import models.Dashboard;
import models.Professional;
import utils.RequestHelper;

/**
 * ZTF_J2EE - DashboardController
 *
 * GET /api/dashboard      - hospital home screen; each block is only built for whoever holds the
 *                           matching permission.
 * GET /api/dashboard/team - team overview from the framework example (manager group).
 */
@WebServlet(name = "DashboardController", urlPatterns = {"/api/dashboard", "/api/dashboard/*"}, loadOnStartup = 1)
public class DashboardController extends Controller {

    private static final long serialVersionUID = 1L;

    public void overview(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> user = RequestHelper.getSessionUser(request);
        List<String> perms = new ArrayList<>();
        if (user != null && user.get("permissoes") instanceof List<?>) {
            for (Object p : (List<?>) user.get("permissoes")) {
                perms.add(String.valueOf(p));
            }
        }
        LocalDate today = Appointments.now().toLocalDate();
        Dashboard dashboard = new Dashboard();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("today", today);

        if (perms.contains("consultas.chegada")) {
            d.put("reception", true);
            d.put("day_appointments", dashboard.dayAppointments(today));
            d.put("arrived", dashboard.arrived(today));
            d.put("awaiting_triage", dashboard.awaitingTriage(today));
            d.put("day_exams", dashboard.dayExams(today));
            d.put("cancelled", dashboard.dayCancelled(today));
            d.put("next_arrivals", withTime(dashboard.nextArrivals(today, 6)));
        }
        if (perms.contains("agenda.view")) {
            Appointments schedule = new Appointments();
            d.put("timeline_doctors", schedule.doctorsOnDay(today));
            d.put("timeline_appointments", withTime(schedule.ofDay(today, null)));
        }
        if (perms.contains("triagem.fila")) {
            d.put("nursing", true);
            d.put("triage_queue", dashboard.triageQueue(8));
            d.put("by_priority", dashboard.triagedByPriority(today));
            d.put("awaiting_triage", dashboard.awaitingTriage(today));
        }
        if (perms.contains("atendimento.fila")) {
            Map<String, Object> prof = new Professional().byUser(RequestHelper.getSessionUserId(request));
            if (prof != null && "medico".equals(prof.get("tipo"))) {
                int doctorId = ((Number) prof.get("id_profissional")).intValue();
                d.put("doctor", true);
                d.put("professional", prof);
                d.put("my_schedule", withTime(new Appointments().ofDay(today, doctorId)));
                d.put("doctor_queue", dashboard.doctorQueue(doctorId, 8));
                d.put("completed", dashboard.completedByDoctor(doctorId, today));
            }
        }
        if (perms.contains("auditoria.view")) {
            d.put("admin", true);
            d.put("active_patients", dashboard.activePatients());
            d.put("active_professionals", dashboard.activeProfessionals());
            d.put("completed_this_month", dashboard.completedInMonth(today));
            d.put("scheduled_ahead", dashboard.scheduledAhead());
            d.put("active_users", dashboard.activeUsers());
            d.put("blocked_users", dashboard.blockedUsers());
        }
        jsonSuccess(response, d, null);
    }

    /** GET /api/dashboard/team - user, ticket and file counts plus the member list (framework example). */
    public void team(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("users", Sql.number("SELECT COUNT(*) FROM usuarios"));
        d.put("open_tickets", Sql.number("SELECT COUNT(*) FROM chamados WHERE status <> 'fechado'"));
        d.put("files", Sql.number("SELECT COUNT(*) FROM arquivos_upload"));
        d.put("members", Sql.list("SELECT id_usuario, nome, email, status FROM usuarios ORDER BY nome LIMIT 50"));
        jsonSuccess(response, d, null);
    }

    private static List<Map<String, Object>> withTime(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(r.get("data_hora") != null ? Appointments.withTime(r) : r);
        }
        return out;
    }
}
