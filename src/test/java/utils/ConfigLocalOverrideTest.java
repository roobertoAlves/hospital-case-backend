package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLocalOverrideTest {

    @TempDir
    Path dir;

    @AfterEach
    void restoreDefaultConfig() {
        Config.reset();
    }

    @Test
    void localConfigOverridesOnlyGivenKeys() throws IOException {
        Files.writeString(dir.resolve("config.json"),
                "{\"database\":{\"connections\":{\"mysql\":{\"host\":\"localhost\",\"user\":\"root\",\"password\":\"senha\"}}},"
                        + "\"app\":{\"name\":\"Base\"}}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("config.local.json"),
                "{\"database\":{\"connections\":{\"mysql\":{\"password\":\"\"}}}}", StandardCharsets.UTF_8);

        Config.setConfigPath(dir.resolve("config.json").toString());

        assertEquals("", Config.getString("database.connections.mysql.password", "x"));
        assertEquals("root", Config.getString("database.connections.mysql.user", "x"));
        assertEquals("localhost", Config.getString("database.connections.mysql.host", "x"));
        assertEquals("Base", Config.getString("app.name", "x"));
    }

    @Test
    void withoutLocalConfigUsesOnlyBaseFile() throws IOException {
        Files.writeString(dir.resolve("config.json"), "{\"app\":{\"env\":\"development\"}}", StandardCharsets.UTF_8);
        Config.setConfigPath(dir.resolve("config.json").toString());
        assertEquals("development", Config.getString("app.env", "x"));
    }
}
