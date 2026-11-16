package utils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonResponseTest {

    @Test
    void serializaDatasJavaTimeComoTextoIso() {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("criado_em", LocalDateTime.of(2026, 11, 15, 14, 30, 5));
        linha.put("data_nascimento", LocalDate.of(1990, 1, 2));

        String json = JsonResponse.success(List.of(linha), "ok");

        assertTrue(json.contains("\"criado_em\": \"2026-11-15T14:30:05\""), json);
        assertTrue(json.contains("\"data_nascimento\": \"1990-01-02\""), json);
    }

    @Test
    void serializaDatasJavaSqlComoTextoIso() {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("nasc", java.sql.Date.valueOf("1985-03-14"));
        linha.put("hora", java.sql.Time.valueOf("08:30:00"));
        linha.put("quando", java.sql.Timestamp.valueOf("2026-11-16 10:15:00"));

        String json = JsonResponse.success(List.of(linha), "ok");

        assertTrue(json.contains("\"nasc\": \"1985-03-14\""), json);
        assertTrue(json.contains("\"hora\": \"08:30:00\""), json);
        assertTrue(json.contains("\"quando\": \"2026-11-16T10:15\""), json);
    }
}
