package utils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class Validation {

    private Validation() {
    }

    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static boolean validCpf(String cpf) {
        String c = digits(cpf);
        if (c.length() != 11 || c.chars().distinct().count() == 1) {
            return false;
        }
        return c.charAt(9) == checkDigit(c, 9) && c.charAt(10) == checkDigit(c, 10);
    }

    private static char checkDigit(String c, int size) {
        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += (c.charAt(i) - '0') * (size + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return (char) ('0' + (rest == 10 ? 0 : rest));
    }

    public static String formatCpf(String cpf) {
        String c = digits(cpf);
        if (c.length() != 11) {
            return c;
        }
        return c.substring(0, 3) + "." + c.substring(3, 6) + "." + c.substring(6, 9) + "-" + c.substring(9);
    }

    public static String maskedCpf(String cpf) {
        String c = digits(cpf);
        if (c.length() != 11) {
            return "—";
        }
        return "***." + c.substring(3, 6) + "." + c.substring(6, 9) + "-**";
    }

    public static boolean validEmail(String email) {
        return email != null && email.length() <= 150 && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean validBirth(LocalDate birth, LocalDate today) {
        return birth != null && !birth.isAfter(today) && !birth.isBefore(today.minusYears(130));
    }

    public static boolean validPhone(String phone) {
        int n = digits(phone).length();
        return n == 10 || n == 11;
    }

    public static boolean validCep(String cep) {
        return digits(cep).length() == 8;
    }

    private static final java.util.Set<String> UFS = java.util.Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR",
            "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    public static boolean validUf(String uf) {
        return uf != null && UFS.contains(uf.trim().toUpperCase());
    }

    public static int age(LocalDate birth, LocalDate today) {
        return java.time.Period.between(birth, today).getYears();
    }
}
