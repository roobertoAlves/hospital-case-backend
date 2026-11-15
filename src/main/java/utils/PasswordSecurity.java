package utils;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * ZTF_J2EE - PasswordSecurity
 *
 * Gerenciador de seguranca de senhas com:
 * 1. BCrypt (algoritmo adaptativo Blowfish com fator de custo configuravel)
 * 2. Salt criptografico gerado automaticamente por hash
 * 3. Pepper secreto vindo do config.json (HMAC-SHA256)
 */
public class PasswordSecurity {

    public static String hash(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("A senha nao pode ser vazia.");
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
            throw new RuntimeException("Falha no calculo HMAC-SHA256: " + e.getMessage(), e);
        }
    }

    private static String getPepper() {
        return Config.getString("security.password_pepper", "ZTFrameworkDefaultPepper#2026!");
    }
}
