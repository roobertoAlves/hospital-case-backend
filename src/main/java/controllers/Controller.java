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
import utils.Url;

/**
 * ZTF_J2EE - Base controller (servlet handler)
 *
 * Abstract HttpServlet. Every controller declares its own @WebServlet(...) and inherits the
 * HTTP life cycle (init/service), route and ACL validation through the Router (routes.json) and
 * the reflective dispatcher handle(action, request, response). Every action answers JSON.
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
            new Router().dispatch(request, response, this);
        } catch (Throwable e) {
            String errorId = java.util.UUID.randomUUID().toString().substring(0, 8);
            java.util.logging.Logger.getLogger(getClass().getName())
                    .log(java.util.logging.Level.SEVERE, "Error " + errorId + " in controller", e);
            if (!response.isCommitted()) {
                JsonResponse.error(response, 500, "ERR_SERVER_ERROR", "Erro interno no servidor (código " + errorId + ").");
            }
        } finally {
            Url.clearRequest();
        }
    }

    /**
     * Central action dispatcher, called by the Router after the route and the ACL were validated.
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

    /** 404 with the standard JSON error envelope. */
    protected void notFound(HttpServletResponse response, String message) throws IOException {
        JsonResponse.error(response, 404, "ERR_NOT_FOUND", message);
    }

    /** 403 with the standard JSON error envelope. */
    protected void forbidden(HttpServletResponse response, String message) throws IOException {
        JsonResponse.error(response, 403, "ERR_FORBIDDEN", message);
    }

    /** Standard JSON success envelope (HTTP 200). */
    protected String jsonSuccess(HttpServletResponse response, Object data, String message) throws IOException {
        return JsonResponse.success(response, data, message, 200);
    }

    /** Standard JSON error envelope with a specific HTTP status code. */
    protected String jsonError(HttpServletResponse response, int httpCode, String errorCode, String message) throws IOException {
        return JsonResponse.error(response, httpCode, errorCode, message, null);
    }

    /** Request parameters (query string, form-urlencoded, multipart or JSON body). */
    protected Map<String, Object> getPayload(HttpServletRequest request) {
        return RequestHelper.getRequestPayload(request);
    }

    /** Signed-in user data stored in the HttpSession ("user"). */
    protected Map<String, Object> getSessionUser(HttpServletRequest request) {
        return RequestHelper.getSessionUser(request);
    }

    /** Dynamic route parameters (e.g. {id}). */
    protected Map<String, String> getRouteParams(HttpServletRequest request) {
        return RequestHelper.getRouteParams(request);
    }

    /**
     * Records an operation in the audit log with the session user and the request IP.
     * Never throws (an audit failure does not stop the operation).
     * "details" must not contain clinical content, passwords or tokens.
     */
    protected void audit(HttpServletRequest request, String action, String entity, Object entityId,
                         Integer patientId, String details) {
        int userId = RequestHelper.getSessionUserId(request);
        models.AuditLog.record(userId > 0 ? userId : null, action, entity, entityId, patientId,
                details, request != null ? request.getRemoteAddr() : null);
    }
}
