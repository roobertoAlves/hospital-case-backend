package controllers;

import database.DBQuery;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

/**
 * ZTF_J2EE - ChamadoController
 *
 * Controlador de exemplo didatico para o modulo de Chamados de Suporte.
 */
@WebServlet(
    name = "ChamadoController",
    urlPatterns = {"/chamados", "/api/chamados/novo"}
)
public class ChamadoController extends Controller {

    private static final long serialVersionUID = 1L;

    public void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        List<Map<String, Object>> chamados = new DBQuery("chamados c", "c.*, u.nome AS solicitante, u.email AS emailSolicitante")
                .joinRaw("INNER JOIN usuarios u ON u.idUsuario = c.idUsuario")
                .orderBy("c.idChamado", "DESC")
                .select();

        request.setAttribute("chamados", chamados);
        renderView(request, response, "chamados.jsp");
    }

    public void criar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String titulo    = Sanitize.string(RequestHelper.getParam(request, "titulo", ""));
        String descricao = Sanitize.string(RequestHelper.getParam(request, "descricao", ""));
        int idUsuario    = RequestHelper.getSessionUserId(request);

        if (idUsuario <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Sessão expirada. Faça login novamente.");
            return;
        }

        if (titulo.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_TITLE", "O título do chamado é obrigatório.");
            return;
        }

        Map<String, Object> insertData = new LinkedHashMap<>();
        insertData.put("idUsuario", idUsuario);
        insertData.put("titulo", titulo);
        insertData.put("descricao", descricao);
        insertData.put("status", "aberto");

        long idChamado = new DBQuery("chamados").insert(insertData);

        Map<String, Object> sessionUser = RequestHelper.getSessionUser(request);
        String nomeSolicitante = (sessionUser != null && sessionUser.get("nome") != null)
                ? String.valueOf(sessionUser.get("nome"))
                : "Eu";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idChamado", idChamado);
        data.put("titulo", titulo);
        data.put("status", "aberto");
        data.put("solicitante", nomeSolicitante);
        data.put("dataAbertura", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        JsonResponse.success(response, data, "Chamado registrado com sucesso!");
    }

    public void alternarStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idChamado     = Sanitize.intVal(RequestHelper.getParam(request, "idChamado", "0"), 0);
        String novoStatus = Sanitize.string(RequestHelper.getParam(request, "status", "aberto"));

        List<String> statusValidos = Arrays.asList("aberto", "em_andamento", "fechado");
        if (!statusValidos.contains(novoStatus)) {
            JsonResponse.error(response, 400, "ERR_INVALID_STATUS", "Status informado é inválido.");
            return;
        }

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("status", novoStatus);

        new DBQuery("chamados")
                .addCondition("AND", "idChamado", "=", idChamado)
                .update(updateData);

        JsonResponse.success(response, null, "Status do chamado atualizado com sucesso!");
    }
}
