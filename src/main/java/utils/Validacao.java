package utils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * ZTF_J2EE - Validacao
 *
 * Validacoes de dados cadastrais usadas no backend (o front-end repete algumas so para
 * dar retorno imediato; quem decide e sempre o servidor).
 */
public final class Validacao {

    private Validacao() {
    }

    /** Remove tudo que nao for digito (ex.: "123.456.789-09" -> "12345678909"). */
    public static String digitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** CPF com 11 digitos, digitos verificadores corretos e nao formado por um digito repetido. */
    public static boolean cpfValido(String cpf) {
        String c = digitos(cpf);
        if (c.length() != 11 || c.chars().distinct().count() == 1) {
            return false;
        }
        return c.charAt(9) == digitoVerificador(c, 9) && c.charAt(10) == digitoVerificador(c, 10);
    }

    private static char digitoVerificador(String c, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (c.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return (char) ('0' + (resto == 10 ? 0 : resto));
    }

    /** Formata 11 digitos como 000.000.000-00 (para exibicao). */
    public static String formatarCpf(String cpf) {
        String c = digitos(cpf);
        if (c.length() != 11) {
            return c;
        }
        return c.substring(0, 3) + "." + c.substring(3, 6) + "." + c.substring(6, 9) + "-" + c.substring(9);
    }

    /** Mascara o CPF para listas (LGPD - minimizacao): ***.456.789-** */
    public static String cpfMascarado(String cpf) {
        String c = digitos(cpf);
        if (c.length() != 11) {
            return "—";
        }
        return "***." + c.substring(3, 6) + "." + c.substring(6, 9) + "-**";
    }

    public static boolean emailValido(String email) {
        return email != null && email.length() <= 150 && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    /** Data no formato ISO (AAAA-MM-DD), ou null se invalida. */
    public static LocalDate data(String valor) {
        try {
            return valor == null || valor.isBlank() ? null : LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** RN-PAC-03: nascimento nao pode ser futuro nem anterior a 130 anos. */
    public static boolean nascimentoValido(LocalDate nascimento, LocalDate hoje) {
        return nascimento != null && !nascimento.isAfter(hoje) && !nascimento.isBefore(hoje.minusYears(130));
    }

    /** Telefone brasileiro: 10 ou 11 digitos (com DDD). */
    public static boolean telefoneValido(String telefone) {
        int n = digitos(telefone).length();
        return n == 10 || n == 11;
    }

    public static boolean cepValido(String cep) {
        return digitos(cep).length() == 8;
    }

    private static final java.util.Set<String> UFS = java.util.Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR",
            "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public static boolean ufValida(String uf) {
        return uf != null && UFS.contains(uf.trim().toUpperCase());
    }

    /** Idade em anos completos na data informada. */
    public static int idade(LocalDate nascimento, LocalDate hoje) {
        return java.time.Period.between(nascimento, hoje).getYears();
    }
}
