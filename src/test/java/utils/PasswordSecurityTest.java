package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PasswordSecurityTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc12", "abcdefgh", "12345678", "        "})
    void policyRejectsWeakPasswords(String password) {
        assertNotNull(PasswordSecurity.strengthError(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"senha123", "Clinica2026", "ação1234"})
    void policyAcceptsPasswordsWithLettersAndNumbers(String password) {
        assertNull(PasswordSecurity.strengthError(password));
    }

    @Test
    void sha256HexHasFixedLengthAndKnownValue() {
        // Vetor de teste publico do SHA-256 para "abc"
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PasswordSecurity.sha256Hex("abc"));
    }

    @Test
    void hashUsesBcryptWithRandomSaltAndValidatesWithPepper() {
        String a = PasswordSecurity.hash("senha123");
        String b = PasswordSecurity.hash("senha123");
        assertNotEquals(a, b, "salt deve tornar cada hash unico");
        assertTrue(a.startsWith("$2"));
        assertTrue(PasswordSecurity.verify("senha123", a));
        assertFalse(PasswordSecurity.verify("senha124", a));
    }
}
