package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/**
 * ZTF_J2EE - JsonResponse
 *
 * Standard JSON response envelopes for the REST API.
 */
public class JsonResponse {

    /**
     * MySQL Connector/J 8+ returns DATETIME/DATE/TIME as java.time.*. Without adapters, Gson tries
     * to reach private fields of those classes, which Java 17+ forbids ("Failed making field
     * 'java.time.LocalDateTime#date' accessible"). Here they are serialized as ISO-8601 text
     * (e.g. "2026-11-15T14:30").
     */
    private static final JsonSerializer<Object> ISO_TEXT = (src, type, ctx) -> new JsonPrimitive(src.toString());

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .serializeNulls()
            .registerTypeAdapter(LocalDateTime.class, ISO_TEXT)
            .registerTypeAdapter(LocalDate.class, ISO_TEXT)
            .registerTypeAdapter(LocalTime.class, ISO_TEXT)
            .registerTypeAdapter(OffsetDateTime.class, ISO_TEXT)
            // DATE/TIME/TIMESTAMP columns read as java.sql.*: also ISO (no locale formatting).
            .registerTypeAdapter(java.sql.Date.class, ISO_TEXT)
            .registerTypeAdapter(java.sql.Time.class, ISO_TEXT)
            .registerTypeAdapter(java.sql.Timestamp.class,
                    (JsonSerializer<java.sql.Timestamp>) (src, type, ctx) -> new JsonPrimitive(src.toLocalDateTime().toString()))
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

    /**
     * HTTP 500 without technical details: the exception goes to the server log with a tracking
     * code, and the client only gets the friendly message and the code.
     */
    public static String serverError(HttpServletResponse response, String userMessage, Throwable cause) {
        String errorId = java.util.UUID.randomUUID().toString().substring(0, 8);
        java.util.logging.Logger.getLogger(JsonResponse.class.getName())
                .log(java.util.logging.Level.SEVERE, "Erro " + errorId + ": " + userMessage, cause);
        return error(response, 500, "ERR_SERVER_ERROR", userMessage + " (código " + errorId + ")");
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

    // Methods without an HttpServletResponse (CLI/tests)
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
