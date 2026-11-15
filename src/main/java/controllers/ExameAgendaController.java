package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Agenda;
import models.AgendaExames;
import models.Notificacao;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validacao;

/**
 * ZTF_J2EE - ExameAgendaController
 *
 * Agenda de exames: exames do dia por local, agendamento com horarios livres do local do
 * exame, cancelamento e registro de realizado/ausente. Permissoes no routes.json
 * (exames.agenda.view para ver, exames.agendar para alterar).
 */
@WebServlet(name = "ExameAgendaController",
        urlPatterns = {"/exames", "/exames/*", "/api/exames/*", "/api/exames-agendados", "/api/exames-agendados/*"})
public class ExameAgendaController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /exames?data=AAAA-MM-DD */
    public void dia(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LocalDate dia = AgendaController.dataParam(request);
        AgendaExames agenda = new AgendaExames();
        List<Map<String, Object>> exames = new ArrayList<>();
        for (Map<String, Object> x : agenda.doDia(dia)) {
            exames.add(Agenda.comHora(x));
        }
        request.setAttribute("dia", dia);
        request.setAttribute("exames", exames);
        request.setAttribute("locais", agenda.locaisAtivos());
        renderView(request, response, "exames.jsp");
    }

    /** GET /exames/novo?paciente=ID */
    public void novo(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("tipos", new AgendaExames().tiposAtivos());
        request.setAttribute("paciente", AgendaController.pacienteDoParametro(request));
        request.setAttribute("hoje", Agenda.agora().toLocalDate());
        renderView(request, response, "exame_form.jsp");
    }

    /** GET /api/exames/horarios?tipo=ID&data=AAAA-MM-DD */
    public void horarios(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int tipo = Sanitize.intVal(RequestHelper.getParam(request, "tipo", "0"), 0);
        LocalDate dia = Validacao.data(RequestHelper.getParam(request, "data", ""));
        if (tipo <= 0 || dia == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o exame e a data.");
            return;
        }
        jsonSuccess(response, AgendaController.horariosJson(new AgendaExames().horarios(tipo, dia)), null);
    }

    /** POST /api/exames-agendados { id_paciente, id_tipo_exame, data, hora } */
    public void agendar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        int paciente = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int tipo = Sanitize.intVal(RequestHelper.getParam(request, "id_tipo_exame", "0"), 0);
        LocalDateTime quando = AgendaController.dataHora(request);
        if (paciente <= 0 || tipo <= 0 || quando == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Preencha paciente, exame, data e horário.");
            return;
        }
        Agenda.Resultado r = new AgendaExames().agendar(paciente, tipo, quando, null, idUsuario);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_AGENDA", r.erro());
            return;
        }
        Notificacao.exameAgendado(new AgendaExames().resumoParaAviso(r.id()), idUsuario);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_exame_agendado", r.id());
        data.put("redirect", "exames?data=" + quando.toLocalDate());
        jsonSuccess(response, data, "Exame agendado.");
    }

    /** POST /api/exames-agendados/{id}/cancelar { motivo } */
    public void cancelar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Agenda.Resultado r = new AgendaExames().cancelar(PacienteController.idDaRota(request),
                RequestHelper.getParam(request, "motivo", ""), RequestHelper.getSessionUserId(request));
        responder(response, r, "Exame cancelado.");
    }

    /** POST /api/exames-agendados/{id}/situacao { situacao: realizado|ausente } */
    public void situacao(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String s = RequestHelper.getParam(request, "situacao", "");
        Agenda.Resultado r = new AgendaExames().registrarSituacao(PacienteController.idDaRota(request), s,
                RequestHelper.getSessionUserId(request));
        responder(response, r, "realizado".equals(s) ? "Exame marcado como realizado." : "Ausência registrada.");
    }

    private void responder(HttpServletResponse response, Agenda.Resultado r, String mensagem) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, null, mensagem);
        } else {
            jsonError(response, 409, "ERR_AGENDA", r.erro());
        }
    }
}
