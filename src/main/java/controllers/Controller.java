package controllers;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.Config;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Router;
import utils.Sanitize;
import utils.Url;

/**
 * ZTF_J2EE - Controller Base (Servlet Handler)
 *
 * Superclasse abstrata que herda de HttpServlet.
 * Cada Controller subclasse possui seu proprio @WebServlet(...) e herda automaticamente
 * o ciclo de vida HTTP (init/service), validacao de rotas/ACL via Router (routes.json)
 * e o despachante reflexivo handle(action, request, response).
 */
public abstract class Controller extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void init() throws ServletException {
        super.init();
        Config.bindServletContext(getServletContext());
        Url.setDetectedContextPath(getServletContext().getContextPath());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        Config.bindServletContext(request.getServletContext());
        Url.bindRequest(request);
        try {
            Router router = new Router();
            router.dispatch(request, response, this);
        } catch (Throwable e) {
            String errorId = java.util.UUID.randomUUID().toString().substring(0, 8);
            java.util.logging.Logger.getLogger(getClass().getName())
                    .log(java.util.logging.Level.SEVERE, "Erro " + errorId + " no Controller", e);
            if (!response.isCommitted()) {
                response.setStatus(500);
                response.setContentType("text/html; charset=utf-8");
                response.getWriter().write("Erro interno no servidor (código " + Sanitize.escapeHtml(errorId) + ").");
            }
        } finally {
            Url.clearRequest();
        }
    }

    /**
     * Manipulador central de acoes do Controller invocado pelo Router apos validacao da rota e ACL.
     */
    public Object handle(String action, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String methodName = (action == null || action.trim().isEmpty()) ? "index" : action.trim();
        try {
            Method method = this.getClass().getMethod(methodName, HttpServletRequest.class, HttpServletResponse.class);
            return method.invoke(this, request, response);
        } catch (NoSuchMethodException e) {
            Method method = this.getClass().getMethod(methodName);
            return method.invoke(this);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /**
     * Renderiza uma View JSP localizada na raiz do webapp (ex: "chamados.jsp" -> "/chamados.jsp").
     */
    protected void renderView(HttpServletRequest request, HttpServletResponse response, String viewName)
            throws ServletException, IOException {
        if (response != null && !response.isCommitted()) {
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/html; charset=utf-8");
        }
        String cleanView = viewName.replaceAll("^/+", "");
        if (cleanView.startsWith("WEB-INF/views/")) {
            cleanView = cleanView.substring("WEB-INF/views/".length());
        }
        String target = "/" + cleanView;
        if (request != null && response != null) {
            request.getRequestDispatcher(target).forward(request, response);
        }
    }

    /**
     * Emite resposta JSON padronizada de sucesso (HTTP 200).
     */
    protected String jsonSuccess(HttpServletResponse response, Object data, String message) throws IOException {
        return JsonResponse.success(response, data, message, 200);
    }

    /**
     * Emite resposta JSON padronizada de erro com HTTP Status Code especifico.
     */
    protected String jsonError(HttpServletResponse response, int httpCode, String errorCode, String message) throws IOException {
        return JsonResponse.error(response, httpCode, errorCode, message, null);
    }

    /**
     * Extrai os parametros da requisicao (Query, Form-UrlEncoded, Multipart FormData ou JSON Body).
     */
    protected Map<String, Object> getPayload(HttpServletRequest request) {
        return RequestHelper.getRequestPayload(request);
    }

    /**
     * Retorna o mapa de dados do usuario autenticado na HttpSession ("user").
     */
    protected Map<String, Object> getSessionUser(HttpServletRequest request) {
        return RequestHelper.getSessionUser(request);
    }

    /**
     * Retorna os parametros dinamicos extraidos da rota (ex: {hash}).
     */
    protected Map<String, String> getRouteParams(HttpServletRequest request) {
        return RequestHelper.getRouteParams(request);
    }

    /**
     * Redireciona o cliente respeitando dinamicamente o contextPath da requisicao HTTP.
     */
    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        Url.redirect(request, response, path);
    }
}
