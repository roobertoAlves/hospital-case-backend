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
        "/dashboard/usuario"
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

        String perfil = "usuario";
        if (grupos.contains("admin")) {
            perfil = "admin";
        } else if (grupos.contains("gerente")) {
            perfil = "gerente";
        } else if (grupos.contains("financeiro")) {
            perfil = "financeiro";
        } else if (grupos.contains("suporte")) {
            perfil = "suporte";
        }

        Url.redirect(request, response, "dashboard/" + perfil);
    }
}
