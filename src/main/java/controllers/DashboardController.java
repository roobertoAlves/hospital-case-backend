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
     * Painel inicial conforme o grupo (unico lugar com essa regra; o login tambem usa).
     * Perfis hospitalares vao para o painel comum com menu lateral; os exemplos do framework
     * continuam com seus paineis originais.
     */
    public static String rotaDoPainel(List<String> grupos) {
        if (grupos == null) {
            return "dashboard/usuario";
        }
        if (grupos.contains("admin")) {
            return "dashboard/admin";
        }
        if (grupos.contains("medico") || grupos.contains("enfermeiro") || grupos.contains("recepcionista")) {
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
