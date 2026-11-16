package controllers;

import java.io.IOException;
import java.time.LocalDate;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.Agenda;
import models.ConsultaAuditoria;
import utils.RequestHelper;
import utils.Sanitize;
import utils.Validacao;

/**
 * ZTF_J2EE - AuditoriaController
 *
 * Tela de consulta da auditoria (permissao auditoria.view, routes.json). Somente leitura.
 * A propria consulta fica auditada (quem olhou a trilha, e com quais filtros).
 */
@WebServlet(name = "AuditoriaController", urlPatterns = {"/auditoria"})
public class AuditoriaController extends Controller {

    private static final long serialVersionUID = 1L;
    private static final int MAX_DIAS = 366;

    /** GET /auditoria?de=&ate=&usuario=&modulo=&paciente=&pagina=&aba=notificacoes */
    public void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ConsultaAuditoria consulta = new ConsultaAuditoria();
        boolean abaNotificacoes = "notificacoes".equals(RequestHelper.getParam(request, "aba", ""));
        request.setAttribute("aba", abaNotificacoes ? "notificacoes" : "auditoria");
        if (abaNotificacoes) {
            request.setAttribute("notificacoes", consulta.notificacoes(100));
            auditar(request, "auditoria.notificacoes_consultadas", null, null, null, null);
            renderView(request, response, "auditoria.jsp");
            return;
        }

        LocalDate hoje = Agenda.agora().toLocalDate();
        LocalDate ate = Validacao.data(RequestHelper.getParam(request, "ate", ""));
        LocalDate de = Validacao.data(RequestHelper.getParam(request, "de", ""));
        if (ate == null || ate.isAfter(hoje)) {
            ate = hoje;
        }
        if (de == null || de.isAfter(ate)) {
            de = ate.minusDays(6);
        }
        if (de.isBefore(ate.minusDays(MAX_DIAS))) {
            de = ate.minusDays(MAX_DIAS);
        }
        int usuario = Sanitize.intVal(RequestHelper.getParam(request, "usuario", "0"), 0);
        int paciente = Sanitize.intVal(RequestHelper.getParam(request, "paciente", "0"), 0);
        String modulo = RequestHelper.getParam(request, "modulo", "").trim();
        if (!modulo.matches("^[a-z_]{0,40}$")) {
            modulo = "";
        }
        int pagina = Sanitize.intVal(RequestHelper.getParam(request, "pagina", "1"), 1);

        ConsultaAuditoria.Filtro filtro = new ConsultaAuditoria.Filtro(de, ate, usuario > 0 ? usuario : null,
                modulo, paciente > 0 ? paciente : null, pagina);
        request.setAttribute("filtro", filtro);
        request.setAttribute("resultado", consulta.buscar(filtro));
        request.setAttribute("modulos", consulta.modulos());
        request.setAttribute("usuarios", consulta.usuarios());
        auditar(request, "auditoria.consultada", null, null, paciente > 0 ? paciente : null,
                "de=" + de + " ate=" + ate + (usuario > 0 ? " usuario=" + usuario : "") + (modulo.isEmpty() ? "" : " modulo=" + modulo));
        renderView(request, response, "auditoria.jsp");
    }
}
