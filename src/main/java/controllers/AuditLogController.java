package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Appointments;
import models.AuditLogQuery;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validation;

/**
 * ZTF_J2EE - AuditLogController
 *
 * Read-only audit log search (permission auditoria.view, routes.json). The search itself is
 * audited (who looked at the trail, and with which filters).
 */
@WebServlet(name = "AuditLogController", urlPatterns = {"/api/audit-log", "/api/audit-log/*"})
public class AuditLogController extends Controller {

    private static final long serialVersionUID = 1L;
    private static final int MAX_DAYS = 366;

    /** GET /api/audit-log?from=&to=&user=&module=&patient=&page= */
    public void search(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuditLogQuery query = new AuditLogQuery();
        LocalDate today = Appointments.now().toLocalDate();
        LocalDate to = Validation.date(RequestHelper.getParam(request, "to", ""));
        LocalDate from = Validation.date(RequestHelper.getParam(request, "from", ""));
        if (to == null || to.isAfter(today)) {
            to = today;
        }
        if (from == null || from.isAfter(to)) {
            from = to.minusDays(6);
        }
        if (from.isBefore(to.minusDays(MAX_DAYS))) {
            from = to.minusDays(MAX_DAYS);
        }
        int user = Sanitize.intVal(RequestHelper.getParam(request, "user", "0"), 0);
        int patient = Sanitize.intVal(RequestHelper.getParam(request, "patient", "0"), 0);
        String module = RequestHelper.getParam(request, "module", "").trim();
        if (!module.matches("^[a-z_]{0,40}$")) {
            module = "";
        }
        int page = Sanitize.intVal(RequestHelper.getParam(request, "page", "1"), 1);

        AuditLogQuery.Filter filter = new AuditLogQuery.Filter(from, to, user > 0 ? user : null,
                module, patient > 0 ? patient : null, page);
        AuditLogQuery.Page result = query.search(filter);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("filter", filter);
        data.put("items", result.items());
        data.put("total", result.total());
        data.put("page", result.page());
        data.put("total_pages", result.totalPages());
        data.put("modules", query.modules());
        data.put("users", query.users());
        audit(request, "auditoria.consultada", null, null, patient > 0 ? patient : null,
                "de=" + from + " ate=" + to + (user > 0 ? " usuario=" + user : "") + (module.isEmpty() ? "" : " modulo=" + module));
        jsonSuccess(response, data, null);
    }

    /** GET /api/audit-log/notifications - the latest e-mail notifications and their delivery status. */
    public void notifications(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notifications", new AuditLogQuery().notifications(100));
        audit(request, "auditoria.notificacoes_consultadas", null, null, null, null);
        jsonSuccess(response, data, null);
    }
}
