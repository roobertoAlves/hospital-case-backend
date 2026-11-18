package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClinicalRoutesTest {

    private static JsonObject routes() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/webapp/WEB-INF/etc/routes.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void administratorCannotReachSchedulesOrPatients() throws Exception {
        JsonObject r = routes();
        for (String path : List.of("/api/appointments", "/api/exams", "/api/vaccines", "/api/patients", "/api/patients/{id}",
                "/api/patients/{id}/history", "/api/medical-orders", "/api/encounters/{id}")) {
            JsonObject get = r.getAsJsonObject(path).getAsJsonObject("get");
            assertTrue(get.has("strictGroup") && get.get("strictGroup").getAsBoolean(), path + " must be strict");
            assertFalse(get.getAsJsonArray("groups").toString().contains("\"admin\""), path + " must not list admin");
        }
    }

    @Test
    void clinicalRecordsOnlyForDoctorsAndNurses() throws Exception {
        JsonObject r = routes();
        assertEquals("[\"medico\"]", r.getAsJsonObject("/api/encounters/{id}").getAsJsonObject("get").getAsJsonArray("groups").toString());
        assertEquals("[\"medico\"]", r.getAsJsonObject("/api/encounters/{id}/certificates").getAsJsonObject("post").getAsJsonArray("groups").toString());
        assertEquals("[\"enfermeiro\"]", r.getAsJsonObject("/api/triage/{id}").getAsJsonObject("get").getAsJsonArray("groups").toString());
    }
}
