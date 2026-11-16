package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Agenda;
import models.Notificacao;
import models.Profissional;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validacao;

/**
 * ZTF_J2EE - AgendaController (consultas)
 *
 * Agenda do dia (recepcao: todos os medicos; medico: so a propria agenda), agendamento com
 * horarios livres, chegada, cancelamento e remarcacao. Permissoes no routes.json; o filtro "propria
 * agenda" do medico e aplicado aqui, no servidor, pelo profissional vinculado ao usuario.
 */
@WebServlet(name = "AgendaController", urlPatterns = {"/agenda", "/agenda/*", "/api/agenda", "/api/agenda/*", "/api/consultas", "/api/consultas/*"})
public class AgendaController extends Controller {

    private static final long serialVersionUID = 1L;

    /** GET /agenda?data=AAAA-MM-DD */
    public void dia(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LocalDate dia = dataParam(request);
        Integer somenteMedico = medicoSeAgendaPropria(request);
        Agenda agenda = new Agenda();
        List<Map<String, Object>> consultas = new ArrayList<>();
        for (Map<String, Object> c : agenda.doDia(dia, somenteMedico)) {
            consultas.add(Agenda.comHora(c));
        }
        List<Map<String, Object>> medicos = agenda.medicosNoDia(dia);
        if (somenteMedico != null) {
            medicos.removeIf(m -> ((Number) m.get("id_profissional")).intValue() != somenteMedico);
        }
        request.setAttribute("dia", dia);
        request.setAttribute("consultas", consultas);
        request.setAttribute("medicos", medicos);
        request.setAttribute("agendaPropria", somenteMedico != null);
        renderView(request, response, "agenda.jsp");
    }

    /** GET /agenda/nova?paciente=ID */
    public void nova(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("especialidades", new Agenda().especialidadesAtivas());
        request.setAttribute("paciente", pacienteDoParametro(request));
        request.setAttribute("hoje", Agenda.agora().toLocalDate());
        renderView(request, response, "agenda_form.jsp");
    }

    /** GET /api/agenda/medicos?especialidade=ID */
    public void medicos(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int esp = Sanitize.intVal(RequestHelper.getParam(request, "especialidade", "0"), 0);
        jsonSuccess(response, new Agenda().medicosDaEspecialidade(esp), null);
    }

    /** GET /api/agenda/horarios?medico=ID&data=AAAA-MM-DD */
    public void horarios(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int medico = Sanitize.intVal(RequestHelper.getParam(request, "medico", "0"), 0);
        LocalDate dia = Validacao.data(RequestHelper.getParam(request, "data", ""));
        if (medico <= 0 || dia == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o médico e a data.");
            return;
        }
        jsonSuccess(response, horariosJson(new Agenda().horarios(medico, dia)), null);
    }

    /** POST /api/consultas { id_paciente, id_especialidade, id_medico, data, hora } */
    public void agendar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        int paciente = Sanitize.intVal(RequestHelper.getParam(request, "id_paciente", "0"), 0);
        int especialidade = Sanitize.intVal(RequestHelper.getParam(request, "id_especialidade", "0"), 0);
        int medico = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        LocalDateTime quando = dataHora(request);
        if (paciente <= 0 || especialidade <= 0 || medico <= 0 || quando == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Preencha paciente, especialidade, médico, data e horário.");
            return;
        }
        Agenda.Resultado r = new Agenda().agendar(paciente, medico, especialidade, quando, idUsuario);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_AGENDA", r.erro());
            return;
        }
        Notificacao.consultaAgendada(new Agenda().resumoParaAviso(r.id()), r.id(), idUsuario);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_consulta", r.id());
        data.put("redirect", "agenda?data=" + quando.toLocalDate());
        jsonSuccess(response, data, "Consulta agendada.");
    }

    /** POST /api/consultas/{id}/chegada */
    public void chegada(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Agenda.Resultado r = new Agenda().registrarChegada(PacienteController.idDaRota(request), RequestHelper.getSessionUserId(request));
        responder(response, r, "Chegada registrada. O paciente entrou na fila de triagem.");
    }

    /** POST /api/consultas/{id}/cancelar { motivo } */
    public void cancelar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        int idUsuario = RequestHelper.getSessionUserId(request);
        Agenda.Resultado r = new Agenda().cancelar(id, RequestHelper.getParam(request, "motivo", ""), idUsuario);
        if (r.ok()) {
            Notificacao.consultaCancelada(new Agenda().resumoParaAviso(id), id, idUsuario);
        }
        responder(response, r, "Consulta cancelada.");
    }

    /** GET /agenda/{id}/remarcar */
    public void remarcarForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, Object> c = new Agenda().paraRemarcar(PacienteController.idDaRota(request));
        if (c == null) {
            naoEncontrado(request, response, "Consulta não encontrada.");
            return;
        }
        request.setAttribute("especialidades", new Agenda().especialidadesAtivas());
        request.setAttribute("original", Agenda.comHora(c));
        Map<String, Object> pac = new LinkedHashMap<>();
        pac.put("id_paciente", c.get("id_paciente"));
        pac.put("nome", c.get("paciente"));
        pac.put("cpf", c.get("cpf"));
        request.setAttribute("paciente", pac);
        request.setAttribute("hoje", Agenda.agora().toLocalDate());
        renderView(request, response, "agenda_form.jsp");
    }

    /** POST /api/consultas/{id}/remarcar { id_medico, data, hora, motivo } */
    public void remarcar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int id = PacienteController.idDaRota(request);
        int idUsuario = RequestHelper.getSessionUserId(request);
        int medico = Sanitize.intVal(RequestHelper.getParam(request, "id_medico", "0"), 0);
        LocalDateTime quando = dataHora(request);
        if (medico <= 0 || quando == null) {
            jsonError(response, 400, "ERR_VALIDATION", "Escolha o médico, a nova data e o horário.");
            return;
        }
        Agenda.Resultado r = new Agenda().remarcar(id, medico, quando, RequestHelper.getParam(request, "motivo", ""), idUsuario);
        if (!r.ok()) {
            jsonError(response, 409, "ERR_AGENDA", r.erro());
            return;
        }
        Notificacao.consultaRemarcada(new Agenda().resumoParaAviso(r.id()), r.id(), idUsuario);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id_consulta", r.id());
        data.put("redirect", "agenda?data=" + quando.toLocalDate());
        jsonSuccess(response, data, "Consulta remarcada. O paciente recebe o aviso com o novo horário.");
    }

    // ------------------------------------------------------------------ auxiliares

    /** Medico com permissao so da propria agenda: devolve o id do profissional; senao null. */
    private static Integer medicoSeAgendaPropria(HttpServletRequest request) {
        Map<String, Object> user = RequestHelper.getSessionUser(request);
        Object perms = user != null ? user.get("permissoes") : null;
        boolean todas = perms instanceof List<?> && ((List<?>) perms).contains("agenda.view");
        if (todas) {
            return null;
        }
        Map<String, Object> prof = new Profissional().porUsuario(RequestHelper.getSessionUserId(request));
        return prof != null ? ((Number) prof.get("id_profissional")).intValue() : -1;
    }

    static LocalDate dataParam(HttpServletRequest request) {
        LocalDate d = Validacao.data(RequestHelper.getParam(request, "data", ""));
        return d != null ? d : Agenda.agora().toLocalDate();
    }

    static LocalDateTime dataHora(HttpServletRequest request) {
        LocalDate d = Validacao.data(RequestHelper.getParam(request, "data", ""));
        String h = RequestHelper.getParam(request, "hora", "");
        if (d == null || !h.matches("^\\d{2}:\\d{2}$")) {
            return null;
        }
        try {
            return d.atTime(LocalTime.parse(h));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    static List<Map<String, Object>> horariosJson(List<Agenda.Horario> horarios) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Agenda.Horario h : horarios) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hora", h.hora().toString());
            m.put("livre", h.livre());
            out.add(m);
        }
        return out;
    }

    static Map<String, Object> pacienteDoParametro(HttpServletRequest request) {
        int id = Sanitize.intVal(RequestHelper.getParam(request, "paciente", "0"), 0);
        if (id <= 0) {
            return null;
        }
        Map<String, Object> p = new models.Paciente().porId(id);
        return p != null && ((Number) p.get("ativo")).intValue() == 1 ? p : null;
    }

    private void responder(HttpServletResponse response, Agenda.Resultado r, String mensagem) throws IOException {
        if (r.ok()) {
            jsonSuccess(response, null, mensagem);
        } else {
            jsonError(response, 409, "ERR_AGENDA", r.erro());
        }
    }
}
