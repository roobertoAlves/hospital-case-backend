package database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MigracoesTest {

    @Test
    void identificaArquivoSemComandos() {
        assertFalse(Migracoes.temComandos("-- comentario\n/* bloco\n */\n   "));
        assertTrue(Migracoes.temComandos("-- c\nALTER TABLE x ADD y INT;"));
    }
}
