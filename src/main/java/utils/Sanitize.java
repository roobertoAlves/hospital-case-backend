package utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZTF_J2EE - Sanitize
 *
 * Sanitizacao e limpeza de dados de entrada contra XSS e injecao de tags indesejadas.
 */
public class Sanitize {

    public static String string(Object value) {
        if (value == null) {
            return "";
        }
        String str = String.valueOf(value);
        // 1. Remove blocos inteiros de <script> e <style> incluindo o codigo interno
        str = str.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</(script|style)>", "");
        // 2. Remove quaisquer tags HTML/XML restantes
        str = str.replaceAll("<[^>]*>", "");
        // 3. Remove caracteres nulos e de controle perigosos
        str = str.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        return str.trim();
    }

    public static String email(Object value) {
        if (value == null) {
            return "";
        }
        String raw = String.valueOf(value).trim();
        return raw.replaceAll("[^a-zA-Z0-9!#$%&'*+/=?^_`{|}~@.\\-]", "");
    }

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public static int intVal(Object value) {
        return intVal(value, 0);
    }

    public static int intVal(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double floatVal(Object value) {
        return floatVal(value, 0.0);
    }

    public static double floatVal(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String alphaNum(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replaceAll("[^a-zA-Z0-9_\\-]", "");
    }

    public static Map<String, Object> map(Map<?, ?> data) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (data == null) {
            return sanitized;
        }
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String cleanKey = string(entry.getKey());
            Object val = entry.getValue();
            if (val instanceof Map<?, ?>) {
                sanitized.put(cleanKey, map((Map<?, ?>) val));
            } else {
                sanitized.put(cleanKey, string(val));
            }
        }
        return sanitized;
    }

    public static String escapeHtml(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }

    public static String escape(Object value) {
        return escapeHtml(value);
    }
}
