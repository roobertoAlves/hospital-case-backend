package utils;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * ZTF_J2EE - PasswordSecurity
 *
 * Password security with:
 * 1. BCrypt (adaptive Blowfish algorithm with a configurable cost factor)
 * 2. a cryptographic salt generated for every hash
 * 3. a secret pepper from config.json (HMAC-SHA256)
 */
public class PasswordSecurity {

    public static String hash(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("The password cannot be empty.");
        }

        String pepper = getPepper();
        String peppered = hmacSha256(password, pepper);

        int cost = Config.getInt("security.bcrypt_cost", 12);
        return BCrypt.hashpw(peppered, BCrypt.gensalt(cost));
    }

    public static boolean verify(String password, String storedHash) {
        if (password == null || password.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        String pepper = getPepper();
        String peppered = hmacSha256(password, pepper);

        return BCrypt.checkpw(peppered, storedHash);
    }

    public static boolean verifyLegacyUnpeppered(String password, String storedHash) {
        if (password == null || password.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        return BCrypt.checkpw(password, storedHash);
    }

    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.length() < 7) {
            return true;
        }
        int cost = Config.getInt("security.bcrypt_cost", 12);
        try {
            int off = storedHash.startsWith("$2$") ? 3 : 4;
            int hashCost = Integer.parseInt(storedHash.substring(off, off + 2));
            return hashCost != cost;
        } catch (Exception e) {
            return true;
        }
    }

    public static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed: " + e.getMessage(), e);
        }
    }

    /** Minimum length for new passwords chosen by the user. */
    public static final int MIN_LENGTH = 8;

    /**
     * Minimum password policy: at least {@value #MIN_LENGTH} characters, one letter and one digit.
     * Returns the error message for the user, or null when the password is accepted.
     */
    public static String strengthError(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return "A senha deve possuir no mínimo " + MIN_LENGTH + " caracteres.";
        }
        if (!password.matches(".*\\p{L}.*") || !password.matches(".*\\d.*")) {
            return "A senha deve conter letras e números.";
        }
        return null;
    }

    /** SHA-256 in hexadecimal (64 characters). Used to keep tokens without storing them in clear text. */
    public static String sha256Hex(String data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM.", e);
        }
    }

    private static String getPepper() {
        return Config.getString("security.password_pepper", "ZTFrameworkDefaultPepper#2026!");
    }
}
