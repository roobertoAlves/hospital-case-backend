package controllers;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.RequestHelper;
import utils.Url;

/**
 * ZTF_J2EE - DashboardController
 *
 * Controlador da pagina inicial ("") e dos paineis de perfil (/dashboard/*).
 */
@WebServlet(
    name = "DashboardController",
    urlPatterns = {
        "",
        "/manual",
        "/dashboard",
        "/dashboard/admin",
        "/dashboard/gerente",
        "/dashboard/financeiro",
        "/dashboard/suporte",
        "/dashboard/usuario",
        "/painel"
    },
    loadOnStartup = 1
)
public class DashboardController extends Controller {

    private static final long serialVersionUID = 1L;

    public void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        List<String> grupos = RequestHelper.getSessionUserGroups(request);
        String perfilReq = request.getParameter("perfil");

        // Se houver alternancia explicita via ?perfil= (ex: Meu Perfil ou Simulacao de Admin), delega para main.jsp
        if (perfilReq != null && !perfilReq.trim().isEmpty()) {
            renderView(request, response, "main.jsp");
            return;
        }

        Url.redirect(request, response, rotaDoPainel(grupos));
    }

    /**
     * GET /painel - tela inicial dos perfis do sistema hospitalar. Cada bloco so e montado
     * para quem tem a permissao correspondente; a JSP apenas exibe.
     */
    public void painel(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        java.util.Map<String, Object> user = RequestHelper.getSessionUser(request);
        List<String> perms = new java.util.ArrayList<>();
        if (user != null && user.get("permissoes") instanceof List<?>) {
            for (Object p : (List<?>) user.get("permissoes")) {
                perms.add(String.valueOf(p));
            }
        }
        java.time.LocalDate hoje = models.Agenda.agora().toLocalDate();
        models.Painel painel = new models.Painel();
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        d.put("hoje", hoje);
        d.put("atalhos", utils.Menu.atalhos(perms));

        if (perms.contains("consultas.chegada")) {
            d.put("recepcao", true);
            d.put("consultasDoDia", painel.consultasDoDia(hoje));
            d.put("chegaram", painel.chegaram(hoje));
            d.put("aguardandoTriagem", painel.aguardandoTriagem(hoje));
            d.put("examesDoDia", painel.examesDoDia(hoje));
            d.put("canceladas", painel.canceladasDoDia(hoje));
            d.put("proximas", comHora(painel.proximasChegadas(hoje, 6)));
        }
        if (perms.contains("agenda.view")) {
            models.Agenda agenda = new models.Agenda();
            d.put("linhaMedicos", agenda.medicosNoDia(hoje));
            d.put("linhaConsultas", comHora(agenda.doDia(hoje, null)));
        }
        if (perms.contains("triagem.fila")) {
            d.put("enfermagem", true);
            d.put("filaTriagem", painel.filaTriagem(8));
            d.put("porPrioridade", painel.triadosPorPrioridade(hoje));
            d.put("aguardandoTriagem", painel.aguardandoTriagem(hoje));
        }
        if (perms.contains("atendimento.fila")) {
            java.util.Map<String, Object> prof = new models.Profissional().porUsuario(RequestHelper.getSessionUserId(request));
            if (prof != null && "medico".equals(prof.get("tipo"))) {
                int idMedico = ((Number) prof.get("id_profissional")).intValue();
                d.put("medico", true);
                d.put("profissional", prof);
                d.put("minhaAgenda", comHora(new models.Agenda().doDia(hoje, idMedico)));
                d.put("filaMedico", painel.filaDoMedico(idMedico, 8));
                d.put("realizadas", painel.realizadasPeloMedico(idMedico, hoje));
            }
        }
        if (perms.contains("auditoria.view")) {
            d.put("admin", true);
            d.put("pacientesAtivos", painel.pacientesAtivos());
            d.put("profissionaisAtivos", painel.profissionaisAtivos());
            d.put("realizadasNoMes", painel.realizadasNoMes(hoje));
            d.put("agendadasAFrente", painel.agendadasAFrente());
            d.put("usuariosAtivos", painel.usuariosAtivos());
            d.put("usuariosBloqueados", painel.usuariosBloqueados());
        }
        request.setAttribute("painel", d);
        renderView(request, response, "painel.jsp");
    }

    private static List<java.util.Map<String, Object>> comHora(List<java.util.Map<String, Object>> rows) {
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> r : rows) {
            out.add(r.get("data_hora") != null ? models.Agenda.comHora(r) : r);
        }
        return out;
    }

    /**
     * Painel inicial conforme o grupo (unico lugar com essa regra; o login tambem usa).
     * Perfis hospitalares vao para o painel comum com menu lateral; os exemplos do framework
     * continuam com seus paineis originais.
     */
    public static String rotaDoPainel(List<String> grupos) {
        if (grupos == null) {
            return "dashboard/usuario";
        }
        if (grupos.contains("admin") || grupos.contains("medico") || grupos.contains("enfermeiro")
                || grupos.contains("recepcionista")) {
            return "painel";
        }
        if (grupos.contains("gerente")) {
            return "dashboard/gerente";
        }
        if (grupos.contains("financeiro")) {
            return "dashboard/financeiro";
        }
        if (grupos.contains("suporte")) {
            return "dashboard/suporte";
        }
        return "dashboard/usuario";
    }
}
