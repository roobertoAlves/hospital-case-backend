package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidacaoTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void cpfsValidos(String cpf) {
        assertTrue(Validacao.cpfValido(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "11111111111", "123", "", "abc.def.ghi-jk"})
    void cpfsInvalidos(String cpf) {
        assertFalse(Validacao.cpfValido(cpf));
    }

    @Test
    void formataEMascaraCpf() {
        assertEquals("529.982.247-25", Validacao.formatarCpf("52998224725"));
        assertEquals("***.982.247-**", Validacao.cpfMascarado("52998224725"));
    }

    @Test
    void nascimentoNaoPodeSerFuturoNemAbsurdo() {
        LocalDate hoje = LocalDate.of(2026, 11, 15);
        assertTrue(Validacao.nascimentoValido(LocalDate.of(1990, 1, 1), hoje));
        assertTrue(Validacao.nascimentoValido(hoje, hoje), "recem-nascido");
        assertFalse(Validacao.nascimentoValido(hoje.plusDays(1), hoje));
        assertFalse(Validacao.nascimentoValido(hoje.minusYears(131), hoje));
        assertNull(Validacao.data("15/11/2026"), "so formato ISO vindo do <input type=date>");
    }

    @Test
    void telefoneCepEUf() {
        assertTrue(Validacao.telefoneValido("(11) 90000-0001"));
        assertTrue(Validacao.telefoneValido("1133334444"));
        assertFalse(Validacao.telefoneValido("90000-0001"));
        assertTrue(Validacao.cepValido("01310-100"));
        assertFalse(Validacao.cepValido("0131"));
        assertTrue(Validacao.ufValida("sp"));
        assertFalse(Validacao.ufValida("XX"));
    }
}
