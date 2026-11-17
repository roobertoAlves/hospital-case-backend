package controllers;

import database.DBQuery;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "TicketController", urlPatterns = {"/api/tickets", "/api/tickets/*"})
public class TicketController extends Controller {

    private static final long serialVersionUID = 1L;

    public void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> tickets = new DBQuery("chamados c", "c.*, u.nome AS solicitante, u.email AS email_solicitante")
                .joinRaw("INNER JOIN usuarios u ON u.id_usuario = c.id_usuario")
                .orderBy("c.id_chamado", "DESC")
                .select();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", tickets.size());
        counts.put("open", tickets.stream().filter(t -> "aberto".equals(t.get("status"))).count());
        counts.put("in_progress", tickets.stream().filter(t -> "em_andamento".equals(t.get("status"))).count());
        counts.put("closed", tickets.stream().filter(t -> "fechado".equals(t.get("status"))).count());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tickets", tickets);
        data.put("counts", counts);
        jsonSuccess(response, data, null);
    }

    static final Map<String, List<String>> TEAMS = Map.of(
            "ti", List.of("acesso", "senha", "equipamento", "sistema", "rede", "outro"),
            "produtos", List.of("erro", "melhoria", "duvida", "outro"),
            "infraestrutura", List.of("manutencao", "limpeza", "materiais", "outro"),
            "financeiro", List.of("faturamento", "reembolso", "pagamento", "outro"),
            "rh", List.of("ponto", "beneficios", "ferias", "outro"));

    public void create(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String title    = Sanitize.string(RequestHelper.getParam(request, "titulo", ""));
        String description = Sanitize.string(RequestHelper.getParam(request, "descricao", ""));
        String team = Sanitize.string(RequestHelper.getParam(request, "equipe", ""));
        String subject = Sanitize.string(RequestHelper.getParam(request, "assunto", ""));
        if (!TEAMS.containsKey(team) || !TEAMS.get(team).contains(subject)) {
            JsonResponse.error(response, 422, "ERR_VALIDATION", "Escolha a equipe e o assunto do chamado.",
                    Map.of(TEAMS.containsKey(team) ? "assunto" : "equipe", "Escolha uma opção."));
            return;
        }
        int userId    = RequestHelper.getSessionUserId(request);

        if (userId <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Sessão expirada. Faça login novamente.");
            return;
        }

        if (title.isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_TITLE", "O título do chamado é obrigatório.");
            return;
        }

        Map<String, Object> insertData = new LinkedHashMap<>();
        insertData.put("id_usuario", userId);
        insertData.put("titulo", title);
        insertData.put("descricao", description);
        insertData.put("equipe", team);
        insertData.put("assunto", subject);
        insertData.put("status", "aberto");

        long ticketId = new DBQuery("chamados").insert(insertData);

        Map<String, Object> sessionUser = RequestHelper.getSessionUser(request);
        String requesterName = (sessionUser != null && sessionUser.get("nome") != null)
                ? String.valueOf(sessionUser.get("nome"))
                : "Eu";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_chamado", ticketId);
        data.put("titulo", title);
        data.put("status", "aberto");
        data.put("solicitante", requesterName);
        data.put("aberto_em", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

        JsonResponse.success(response, data, "Chamado registrado com sucesso!");
    }

    public void toggleStatus(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int ticketId     = Sanitize.intVal(RequestHelper.getParam(request, "id_chamado", "0"), 0);
        String newStatus = Sanitize.string(RequestHelper.getParam(request, "status", "aberto"));

        List<String> validStatuses = Arrays.asList("aberto", "em_andamento", "fechado");
        if (!validStatuses.contains(newStatus)) {
            JsonResponse.error(response, 400, "ERR_INVALID_STATUS", "Status informado é inválido.");
            return;
        }

        Map<String, Object> updateData = new LinkedHashMap<>();
        updateData.put("status", newStatus);

        new DBQuery("chamados")
                .addCondition("AND", "id_chamado", "=", ticketId)
                .update(updateData);

        JsonResponse.success(response, null, "Status do chamado atualizado com sucesso!");
    }
}
