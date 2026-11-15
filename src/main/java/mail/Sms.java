package mail;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import utils.Config;

/**
 * ZTF_J2EE - Sms
 *
 * Componente oficial de envio de mensagens SMS via Twilio REST API
 * com suporte a modo de simulacao em desenvolvimento (storage/logs/sms.log).
 */
public class Sms {

    public static boolean send(String to, String message) {
        boolean enabled = Config.getBoolean("sms.enabled", true);
        if (!enabled) {
            return false;
        }

        boolean simulate = Config.getBoolean("sms.simulate_in_dev", true);
        if (simulate) {
            logSimulation(to, message);
            return true;
        }

        String accountSid = Config.getString("sms.twilio.account_sid", "");
        String authToken  = Config.getString("sms.twilio.auth_token", "");
        String fromNumber = Config.getString("sms.twilio.from_number", "");

        if (accountSid.isEmpty() || authToken.isEmpty() || fromNumber.isEmpty()) {
            throw new RuntimeException("Credenciais do Twilio nao configuradas no config.json.");
        }

        try {
            String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
            String body = "From=" + URLEncoder.encode(fromNumber, StandardCharsets.UTF_8)
                    + "&To=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            String basicAuth = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            return status >= 200 && status < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static void logSimulation(String to, String message) {
        try {
            String root = Config.getWebappRoot();
            String relStorage = Config.getString("paths.storage", "WEB-INF/storage");
            File logDir = new File(new File(root, relStorage), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "sms.log");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String entry = String.format("[%s] [SIMULAÇÃO SMS TWILIO] Destinatário: %s | Mensagem: %s%n", timestamp, to, message);
            Files.writeString(
                    logFile.toPath(),
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }
}
