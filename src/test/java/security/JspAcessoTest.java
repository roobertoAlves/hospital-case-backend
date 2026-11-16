package security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Toda JSP publica (fora de WEB-INF) confere o acesso logo no inicio com
 * Router.checkJspAccess, para nao abrir pelo endereco direto sem passar pelas regras do
 * routes.json. So as paginas de erro ficam de fora.
 */
class JspAcessoTest {

    @Test
    void todaJspConfereOAcesso() throws IOException {
        Path webapp = Path.of("src/main/webapp");
        List<String> sem = new ArrayList<>();
        try (Stream<Path> jsps = Files.walk(webapp)) {
            for (Path p : (Iterable<Path>) jsps.filter(f -> f.toString().endsWith(".jsp"))::iterator) {
                String rel = webapp.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith("WEB-INF/") || rel.startsWith("errors/")) {
                    continue;
                }
                String txt = Files.readString(p, StandardCharsets.UTF_8);
                int idx = txt.indexOf("Router.checkJspAccess(request, response)");
                if (idx < 0 || idx > 600) {
                    sem.add(rel);
                }
            }
        }
        assertTrue(sem.isEmpty(), "JSPs sem Router.checkJspAccess no inicio: " + sem);
    }
}
