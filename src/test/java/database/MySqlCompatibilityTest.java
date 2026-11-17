package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** MySQL 8 has no "ADD COLUMN IF NOT EXISTS": migrations are rewritten before running there. */
class MySqlCompatibilityTest {

    private static final String SQL = "-- comment\n"
            + "ALTER TABLE `pacientes`\n"
            + "    ADD COLUMN IF NOT EXISTS `nome_social` VARCHAR(150) NULL AFTER `nome`,\n"
            + "    ADD COLUMN IF NOT EXISTS `tipo_sanguineo` ENUM('A+','A-','O+') NULL AFTER `nome_social`;\n"
            + "UPDATE `pacientes` SET `nome_social` = 'a;b' WHERE id_paciente = 1;\n";

    @Test
    void newColumnsBecomePlainAddColumn() {
        String out = Migrations.withoutAddColumnIfNotExists(SQL, (t, c) -> false);
        assertFalse(out.toUpperCase().contains("IF NOT EXISTS"));
        assertTrue(out.contains("ADD COLUMN `nome_social` VARCHAR(150) NULL AFTER `nome`,"));
        assertTrue(out.contains("ADD COLUMN `tipo_sanguineo` ENUM('A+','A-','O+') NULL AFTER `nome_social`;"));
        assertTrue(out.startsWith("-- comment\n"));
        assertTrue(out.endsWith("UPDATE `pacientes` SET `nome_social` = 'a;b' WHERE id_paciente = 1;\n"));
    }

    @Test
    void existingColumnsAreSkipped() {
        String out = Migrations.withoutAddColumnIfNotExists(SQL, (t, c) -> c.equals("nome_social"));
        assertFalse(out.contains("ADD COLUMN `nome_social`"));
        assertTrue(out.contains("ALTER TABLE `pacientes`\n    ADD COLUMN `tipo_sanguineo` ENUM('A+','A-','O+') NULL AFTER `nome_social`;"));
    }

    @Test
    void statementDisappearsWhenEveryColumnExists() {
        Set<String> all = Set.of("nome_social", "tipo_sanguineo");
        String out = Migrations.withoutAddColumnIfNotExists(SQL, (t, c) -> all.contains(c));
        assertEquals("-- comment\n\nUPDATE `pacientes` SET `nome_social` = 'a;b' WHERE id_paciente = 1;\n", out);
    }

    @Test
    void otherStatementsAreUntouched() {
        String sql = "ALTER TABLE `x` ADD COLUMN `y` INT NULL;\nCREATE TABLE IF NOT EXISTS `z` (id INT);\n";
        assertEquals(sql, Migrations.withoutAddColumnIfNotExists(sql, (t, c) -> false));
    }
}
