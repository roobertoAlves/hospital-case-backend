package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/**
 * ZTF_J2EE - JsonResponse
 *
 * Padroniza contratos de resposta JSON em APIs REST e chamadas AJAX.
 */
public class JsonResponse {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static String success(HttpServletResponse response, Object data) {
        return success(response, data, "Operacao realizada com sucesso.", 200);
    }

    public static String success(HttpServletResponse response, Object data, String message) {
        return success(response, data, message, 200);
    }

    public static String success(HttpServletResponse response, Object data, String message, int statusCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", true);
        payload.put("code", statusCode);
        payload.put("message", message);
        payload.put("data", data);
        return send(response, payload, statusCode);
    }

    public static String error(HttpServletResponse response, int statusCode, String errorCode, String message) {
        return error(response, statusCode, errorCode, message, null);
    }

    public static String error(HttpServletResponse response, int statusCode, String errorCode, String message, Object details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", false);
        payload.put("code", statusCode);
        payload.put("error", errorCode);
        payload.put("message", message);
        if (details != null) {
            payload.put("details", details);
        }
        return send(response, payload, statusCode);
    }

    public static String send(HttpServletResponse response, Map<String, Object> payload, int statusCode) {
        String json = GSON.toJson(payload);
        if (response != null) {
            response.setStatus(statusCode);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            try {
                response.getWriter().write(json);
                response.getWriter().flush();
            } catch (IOException ignored) {
            }
        }
        return json;
    }

    // Metodos sem HttpServletResponse (para uso em CLI/Testes)
    public static String success(Object data, String message, int statusCode) {
        return success(null, data, message, statusCode);
    }

    public static String success(Object data, String message) {
        return success(null, data, message, 200);
    }

    public static String error(int statusCode, String errorCode, String message, Object details) {
        return error(null, statusCode, errorCode, message, details);
    }

    public static String error(int statusCode, String errorCode, String message) {
        return error(null, statusCode, errorCode, message, null);
    }
}
