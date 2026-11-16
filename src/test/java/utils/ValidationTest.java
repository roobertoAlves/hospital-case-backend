package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void validCpfs(String cpf) {
        assertTrue(Validation.validCpf(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "11111111111", "123", "", "abc.def.ghi-jk"})
    void invalidCpfs(String cpf) {
        assertFalse(Validation.validCpf(cpf));
    }

    @Test
    void formatsAndMasksCpf() {
        assertEquals("529.982.247-25", Validation.formatCpf("52998224725"));
        assertEquals("***.982.247-**", Validation.maskedCpf("52998224725"));
    }

    @Test
    void birthDateCannotBeFutureOrAbsurd() {
        LocalDate today = LocalDate.of(2026, 11, 15);
        assertTrue(Validation.validBirth(LocalDate.of(1990, 1, 1), today));
        assertTrue(Validation.validBirth(today, today), "recem-nascido");
        assertFalse(Validation.validBirth(today.plusDays(1), today));
        assertFalse(Validation.validBirth(today.minusYears(131), today));
        assertNull(Validation.date("15/11/2026"), "so formato ISO vindo do <input type=date>");
    }

    @Test
    void phoneCepAndUf() {
        assertTrue(Validation.validPhone("(11) 90000-0001"));
        assertTrue(Validation.validPhone("1133334444"));
        assertFalse(Validation.validPhone("90000-0001"));
        assertTrue(Validation.validCep("01310-100"));
        assertFalse(Validation.validCep("0131"));
        assertTrue(Validation.validUf("sp"));
        assertFalse(Validation.validUf("XX"));
    }
}
