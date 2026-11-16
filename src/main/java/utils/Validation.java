package utils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * ZTF_J2EE - Validation
 *
 * Registration data validation used by the backend (the frontend repeats some checks only for
 * immediate feedback; the server always decides).
 */
public final class Validation {

    private Validation() {
    }

    /** Removes everything that is not a digit (e.g. "123.456.789-09" -> "12345678909"). */
    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /** CPF with 11 digits, correct check digits and not made of one repeated digit. */
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

    /** Formats 11 digits as 000.000.000-00 (for display). */
    public static String formatCpf(String cpf) {
        String c = digits(cpf);
        if (c.length() != 11) {
            return c;
        }
        return c.substring(0, 3) + "." + c.substring(3, 6) + "." + c.substring(6, 9) + "-" + c.substring(9);
    }

    /** Masks the CPF for lists (LGPD data minimization): ***.456.789-** */
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

    /** ISO date (YYYY-MM-DD), or null when invalid. */
    public static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** RN-PAC-03: birth date can be neither in the future nor more than 130 years ago. */
    public static boolean validBirth(LocalDate birth, LocalDate today) {
        return birth != null && !birth.isAfter(today) && !birth.isBefore(today.minusYears(130));
    }

    /** Brazilian phone number: 10 or 11 digits (with area code). */
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

    /** Age in full years on the given date. */
    public static int age(LocalDate birth, LocalDate today) {
        return java.time.Period.between(birth, today).getYears();
    }
}
