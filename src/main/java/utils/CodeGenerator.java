package utils;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * ZTF_J2EE - CodeGenerator
 *
 * Gerador criptograficamente seguro de senhas temporarias, tokens e codigos numericos OTP.
 */
public class CodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generatePassword() {
        return generatePassword(10);
    }

    public static String generatePassword(int length) {
        String uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lowercase = "abcdefghijkmnopqrstuvwxyz";
        String numbers   = "23456789";
        String symbols   = "@#$%&*!";
        String all       = uppercase + lowercase + numbers + symbols;

        int targetLen = Math.max(4, length);
        char[] chars = new char[targetLen];

        chars[0] = uppercase.charAt(RANDOM.nextInt(uppercase.length()));
        chars[1] = lowercase.charAt(RANDOM.nextInt(lowercase.length()));
        chars[2] = numbers.charAt(RANDOM.nextInt(numbers.length()));
        chars[3] = symbols.charAt(RANDOM.nextInt(symbols.length()));

        for (int i = 4; i < targetLen; i++) {
            chars[i] = all.charAt(RANDOM.nextInt(all.length()));
        }

        // Fisher-Yates shuffle
        for (int i = targetLen - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }

        return new String(chars);
    }

    public static String generateSecureToken() {
        return generateSecureToken(32);
    }

    public static String generateSecureToken(int bytes) {
        int len = Math.max(16, bytes);
        byte[] buf = new byte[len];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(len * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static String generateToken() {
        return generateSecureToken(32);
    }

    public static String generateToken(int bytes) {
        return generateSecureToken(bytes);
    }

    public static String generateNumericCode() {
        return generateNumericCode(6);
    }

    public static String generateNumericCode(int digits) {
        int d = Math.max(4, Math.min(10, digits));
        StringBuilder sb = new StringBuilder(d);
        sb.append(1 + RANDOM.nextInt(9)); // Primeiro digito de 1 a 9
        for (int i = 1; i < d; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String generateUniqId(String prefix) {
        return (prefix != null ? prefix : "") + UUID.randomUUID().toString().replace("-", "");
    }
}
