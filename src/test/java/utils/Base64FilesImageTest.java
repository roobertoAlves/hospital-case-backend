package utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Security tests for the profile photo validation (source of the admin panel XSS). */
class Base64FilesImageTest {

    private static final int LIMIT = 2 * 1024 * 1024;

    /** PNG 1x1 transparente real. */
    private static final String PNG_1X1 =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private static String dataUri(String mime, byte[] content) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(content);
    }

    @Test
    void acceptsPngReal() {
        assertTrue(Base64Files.isSafeImageDataUri(PNG_1X1, LIMIT));
    }

    @Test
    void acceptsJpegWithCorrectSignature() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
        assertTrue(Base64Files.isSafeImageDataUri(dataUri("image/jpeg", jpeg), LIMIT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // attribute break-out: accepted before the fix (only the "data:image/" prefix was checked)
            "data:image/x\" onerror=\"alert(1)",
            "data:image/png;base64,AAAA\" onerror=\"alert(1)",
            "javascript:alert(1)",
            "https://exemplo.com/foto.png",
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB",
            "data:image/png;base64,",
            "data:image/png;base64,@@@@"
    })
    void rejectsValuesThatAreNotSafeImages(String value) {
        assertFalse(Base64Files.isSafeImageDataUri(value, LIMIT));
    }

    @Test
    void rejectsSvgEvenThoughItIsAnImage() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8);
        assertFalse(Base64Files.isSafeImageDataUri(dataUri("image/svg+xml", svg), LIMIT));
    }

    @Test
    void rejectsDeclaredTypeDifferentFromContent() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertFalse(Base64Files.isSafeImageDataUri(dataUri("image/png", html), LIMIT));
    }

    @Test
    void rejectsImageAboveLimit() {
        assertFalse(Base64Files.isSafeImageDataUri(PNG_1X1, 10));
    }
}
