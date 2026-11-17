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

    protected void notFound(HttpServletResponse response, String message) throws IOException {
        JsonResponse.error(response, 404, "ERR_NOT_FOUND", message);
    }

    protected void forbidden(HttpServletResponse response, String message) throws IOException {
        JsonResponse.error(response, 403, "ERR_FORBIDDEN", message);
    }

    protected String jsonSuccess(HttpServletResponse response, Object data, String message) throws IOException {
        return JsonResponse.success(response, data, message, 200);
    }

    protected String jsonError(HttpServletResponse response, int httpCode, String errorCode, String message) throws IOException {
        return JsonResponse.error(response, httpCode, errorCode, message, null);
    }

    protected Map<String, Object> getPayload(HttpServletRequest request) {
        return RequestHelper.getRequestPayload(request);
    }

    protected Map<String, Object> getSessionUser(HttpServletRequest request) {
        return RequestHelper.getSessionUser(request);
    }

    protected Map<String, String> getRouteParams(HttpServletRequest request) {
        return RequestHelper.getRouteParams(request);
    }

    protected void audit(HttpServletRequest request, String action, String entity, Object entityId,
                         Integer patientId, String details) {
        int userId = RequestHelper.getSessionUserId(request);
        models.AuditLog.record(userId > 0 ? userId : null, action, entity, entityId, patientId,
                details, request != null ? request.getRemoteAddr() : null);
    }
}
