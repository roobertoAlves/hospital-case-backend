package controllers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Atendimento;
import models.Paciente;
import models.Receita;
import models.SolicitacaoExame;
import models.Triagem;
import models.VinculoClinico;
import utils.RequestHelper;

/**
 * ZTF_J2EE - HistoricoController
 *
 * Historico do paciente filtrado pelo perfil (RF-HIS-01, RN-ACE-02):
 * - medico com vinculo: prontuarios finalizados, triagens, alergias, receitas e exames;
 * - enfermeiro com vinculo: triagens e alergias;
 * - recepcao: so o historico administrativo (consultas e exames agendados, sem conteudo clinico).
 * Profissional sem vinculo com o paciente recebe 403; toda abertura fica na auditoria.
 */
@WebServlet(name = "HistoricoController", urlPatterns = {"/historico/*"})
public class HistoricoController extends Controller {

    private static final long serialVersionUID = 1L;

    public enum Nivel { MEDICO, ENFERMAGEM, ADMINISTRATIVO, NEGADO }

    /** GET /pacientes/{id}/historico */
    public void historico(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        int idPaciente = PacienteController.idDaRota(request);
        Map<String, Object> paciente = idPaciente > 0 ? new Paciente().porId(idPaciente) : null;
        if (paciente == null) {
            response.setStatus(404);
            request.getRequestDispatcher("/errors/404.jsp").forward(request, response);
            return;
        }
        Nivel nivel = nivel(RequestHelper.getSessionUserGroups(request),
                TriagemController.idProfissional(request, "medico"),
                TriagemController.idProfissional(request, "enfermeiro"), idPaciente);
        if (nivel == Nivel.NEGADO) {
            auditar(request, "historico.negado", "pacientes", idPaciente, idPaciente, null);
            response.setStatus(403);
            request.getRequestDispatcher("/errors/403.jsp").forward(request, response);
            return;
        }
        request.setAttribute("paciente", paciente);
        request.setAttribute("nivel", nivel.name());
        request.setAttribute("administrativo", new Paciente().historicoAdministrativo(idPaciente));
        if (nivel == Nivel.MEDICO || nivel == Nivel.ENFERMAGEM) {
            request.setAttribute("alergias", new Atendimento().alergias(idPaciente));
            request.setAttribute("triagens", new Triagem().doPaciente(idPaciente));
        }
        if (nivel == Nivel.MEDICO) {
            request.setAttribute("prontuarios", new Atendimento().prontuariosDoPaciente(idPaciente));
            request.setAttribute("receitas", new Receita().doPaciente(idPaciente));
            request.setAttribute("exames", new SolicitacaoExame().doPaciente(idPaciente));
        }
        auditar(request, "historico.leitura", "pacientes", idPaciente, idPaciente, nivel.name().toLowerCase());
        renderView(request, response, "historico.jsp");
    }

    /**
     * Regra de acesso isolada (testavel sem HTTP): o perfil clinico vale so com vinculo; sem
     * vinculo, o profissional cai para o nivel administrativo apenas se tambem for da recepcao.
     */
    static Nivel nivel(List<String> grupos, int idMedico, int idEnfermeiro, int idPaciente) {
        if (grupos.contains("medico") && idMedico > 0 && VinculoClinico.medicoPodeAcessarPaciente(idMedico, idPaciente)) {
            return Nivel.MEDICO;
        }
        if (grupos.contains("enfermeiro") && idEnfermeiro > 0 && VinculoClinico.enfermeiroPodeAcessarPaciente(idEnfermeiro, idPaciente)) {
            return Nivel.ENFERMAGEM;
        }
        if (grupos.contains("recepcionista")) {
            return Nivel.ADMINISTRATIVO;
        }
        return Nivel.NEGADO;
    }
}
