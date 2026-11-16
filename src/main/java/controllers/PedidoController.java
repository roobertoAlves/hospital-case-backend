package controllers;

import database.Sql;
import java.io.IOException;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Notificacao;
import models.PedidoMedico;
import models.Portal;
import utils.RequestHelper;

/**
 * ZTF_J2EE - PedidoController
 *
 * Recepcao confere os pedidos medicos anexados no portal (permissao pedidos.conferir).
 * Ver o arquivo fica registrado na auditoria (e dado de saude do paciente).
 */
@WebServlet(name = "PedidoController", urlPatterns = {"/api/pedidos", "/api/pedidos/*"})
public class PedidoController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /api/pedidos?status=pendente|aprovado|recusado */
    public void lista(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String status = RequestHelper.getParam(request, "status", "pendente");
        jsonSuccess(response, Map.of("pedidos", new PedidoMedico().paraConferencia(status), "status", status), null);
    }

    /** POST /api/pedidos/{id}/conferir { decisao: aprovar|recusar, motivo } */
    public void conferir(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        boolean aprovar = "aprovar".equals(RequestHelper.getParam(request, "decisao", ""));
        String motivo = RequestHelper.getParam(request, "motivo", "");
        int idUsuario = RequestHelper.getSessionUserId(request);
        Portal.Resultado r = new PedidoMedico().conferir(id, aprovar, motivo, idUsuario);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_PEDIDO", r.erro());
            return;
        }
        if (!aprovar) {
            Map<String, Object> p = Sql.um("SELECT pa.nome, pa.email FROM pedidos_medicos pm JOIN pacientes pa ON pa.id_paciente = pm.id_paciente "
                    + "WHERE pm.id_pedido = ?", id);
            if (p != null && p.get("email") != null) {
                Notificacao.pedidoRecusado(String.valueOf(p.get("email")), String.valueOf(p.get("nome")), motivo, idUsuario);
            }
        }
        jsonSuccess(response, null, aprovar ? "Pedido aprovado." : "Pedido recusado; os exames que dependiam dele foram cancelados.");
    }

    /** GET /api/pedidos/{id}/arquivo */
    public void arquivo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        int pac = PedidoMedico.pacienteDoPedido(id);
        if (pac > 0) {
            auditar(request, "pedido.arquivo_visualizado", "pedidos_medicos", id, pac, null);
        }
        PortalController.enviarArquivo(response, new PedidoMedico().arquivo(id, null));
    }
}
