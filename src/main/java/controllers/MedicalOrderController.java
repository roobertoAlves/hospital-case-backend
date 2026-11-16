package controllers;

import database.Sql;
import java.io.IOException;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Notifications;
import models.MedicalOrder;
import models.PatientPortal;
import utils.RequestHelper;

/**
 * ZTF_J2EE - PedidoController
 *
 * Recepcao confere os pedidos medicos anexados no portal (permissao pedidos.conferir).
 * Ver o arquivo fica registrado na auditoria (e dado de saude do paciente).
 */
@WebServlet(name = "MedicalOrderController", urlPatterns = {"/api/medical-orders", "/api/medical-orders/*"})
public class MedicalOrderController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /api/pedidos?status=pendente|aprovado|recusado */
    public void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String status = RequestHelper.getParam(request, "status", "pendente");
        jsonSuccess(response, Map.of("pedidos", new MedicalOrder().forReview(status), "status", status), null);
    }

    /** POST /api/pedidos/{id}/conferir { decisao: aprovar|recusar, motivo } */
    public void review(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        boolean approve = "approve".equals(RequestHelper.getParam(request, "decision", ""));
        String reason = RequestHelper.getParam(request, "reason", "");
        int userId = RequestHelper.getSessionUserId(request);
        PatientPortal.Result r = new MedicalOrder().review(id, approve, reason, userId);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_PEDIDO", r.error());
            return;
        }
        if (!approve) {
            Map<String, Object> p = Sql.one("SELECT pa.nome, pa.email FROM pedidos_medicos pm JOIN pacientes pa ON pa.id_paciente = pm.id_paciente "
                    + "WHERE pm.id_pedido = ?", id);
            if (p != null && p.get("email") != null) {
                Notifications.rejectedOrder(String.valueOf(p.get("email")), String.valueOf(p.get("nome")), reason, userId);
            }
        }
        jsonSuccess(response, null, approve ? "Pedido aprovado." : "Pedido recusado; os exames que dependiam dele foram cancelados.");
    }

    /** GET /api/pedidos/{id}/arquivo */
    public void file(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PatientController.routeId(request);
        int pac = MedicalOrder.orderPatient(id);
        if (pac > 0) {
            audit(request, "pedido.arquivo_visualizado", "pedidos_medicos", id, pac, null);
        }
        PatientPortalController.sendFile(response, new MedicalOrder().file(id, null));
    }
}
