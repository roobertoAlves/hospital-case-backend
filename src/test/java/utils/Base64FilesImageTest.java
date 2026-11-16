package utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Testes de seguranca da validacao de foto de perfil (origem do XSS no painel admin). */
class Base64FilesImageTest {

    private static final int LIMITE = 2 * 1024 * 1024;

    /** PNG 1x1 transparente real. */
    private static final String PNG_1X1 =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";

    private static String dataUri(String mime, byte[] conteudo) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(conteudo);
    }

    @Test
    void aceitaPngReal() {
        assertTrue(Base64Files.isSafeImageDataUri(PNG_1X1, LIMITE));
    }

    @Test
    void aceitaJpegComAssinaturaCorreta() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
        assertTrue(Base64Files.isSafeImageDataUri(dataUri("image/jpeg", jpeg), LIMITE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // quebra de atributo: era aceito antes da correcao (so checava o prefixo "data:image/")
            "data:image/x\" onerror=\"alert(1)",
            "data:image/png;base64,AAAA\" onerror=\"alert(1)",
            "javascript:alert(1)",
            "https://exemplo.com/foto.png",
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB",
            "data:image/png;base64,",
            "data:image/png;base64,@@@@"
    })
    void rejeitaValoresQueNaoSaoImagemSegura(String valor) {
        assertFalse(Base64Files.isSafeImageDataUri(valor, LIMITE));
    }

    @Test
    void rejeitaSvgMesmoQueSejaImagem() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8);
        assertFalse(Base64Files.isSafeImageDataUri(dataUri("image/svg+xml", svg), LIMITE));
    }

    @Test
    void rejeitaTipoDeclaradoDiferenteDoConteudo() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertFalse(Base64Files.isSafeImageDataUri(dataUri("image/png", html), LIMITE));
    }

    @Test
    void rejeitaImagemAcimaDoLimite() {
        assertFalse(Base64Files.isSafeImageDataUri(PNG_1X1, 10));
    }
}
