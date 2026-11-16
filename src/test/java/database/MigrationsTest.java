package database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MigrationsTest {

    @Test
    void detectsFileWithoutCommands() {
        assertFalse(Migrations.hasCommands("-- comentario\n/* bloco\n */\n   "));
        assertTrue(Migrations.hasCommands("-- c\nALTER TABLE x ADD y INT;"));
    }
}
