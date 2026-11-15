package mail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import utils.Config;

/**
 * ZTF_J2EE - Mail
 *
 * Servico de envio de e-mails integrado ao config.json via javax.mail (JavaMail).
 * Suporta templates HTML, anexos e modo simulacao em ambiente de desenvolvimento (WEB-INF/storage/logs/mail.log).
 */
public class Mail {

    private final boolean enabled;
    private final boolean simulateMode;
    private String fluentTo = "";
    private String fluentSubject = "";
    private String fluentBody = "";

    public Mail() {
        this.enabled = Config.getBoolean("mail.enabled", false);
        this.simulateMode = Config.getBoolean("mail.simulate_in_dev", false);

        if (!this.enabled && !this.simulateMode) {
            throw new RuntimeException("O servico de e-mail esta desativado no config.json.");
        }
    }

    public Mail setRecipient(String email) {
        this.fluentTo = email;
        return this;
    }

    public Mail setRecipient(String email, String name) {
        this.fluentTo = email;
        return this;
    }

    public Mail setSubject(String subject) {
        this.fluentSubject = subject;
        return this;
    }

    public Mail setBody(String htmlBody) {
        this.fluentBody = htmlBody;
        return this;
    }

    public Mail setBody(String htmlBody, String altText) {
        this.fluentBody = htmlBody;
        return this;
    }

    public boolean send() {
        return send(null, null, null, null);
    }

    public boolean send(String to, String subject, String htmlBody) {
        return send(to, subject, htmlBody, null);
    }

    public boolean send(String to, String subject, String htmlBody, List<String> attachments) {
        String targetTo = (to != null && !to.isEmpty()) ? to : this.fluentTo;
        String targetSubject = (subject != null && !subject.isEmpty()) ? subject : this.fluentSubject;
        String targetBody = (htmlBody != null && !htmlBody.isEmpty()) ? htmlBody : this.fluentBody;

        String host = Config.getString("mail.host", "localhost");
        int port = Config.getInt("mail.port", 2525);
        String user = Config.getString("mail.user", "");
        String password = Config.getString("mail.password", "");
        String fromName = Config.getString("mail.from_name", "ZTF_J2EE");

        if (this.simulateMode && (!this.enabled || password == null || password.isEmpty())) {
            logSimulation(targetTo, targetSubject, targetBody);
            return true;
        }

        try {
            String from = (user != null && !user.isEmpty()) ? user : "no-reply@hospital.local";
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.mime.charset", "UTF-8");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from, fromName, "UTF-8"));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(targetTo, false));
            msg.setSubject(targetSubject, "UTF-8");
            msg.setSentDate(new Date());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(targetBody, "text/html; charset=UTF-8");

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(htmlPart);

            if (attachments != null) {
                for (String filePath : attachments) {
                    File file = new File(filePath);
                    if (file.isFile()) {
                        MimeBodyPart filePart = new MimeBodyPart();
                        FileDataSource fds = new FileDataSource(file);
                        filePart.setDataHandler(new DataHandler(fds));
                        filePart.setFileName(file.getName());
                        mp.addBodyPart(filePart);
                    }
                }
            }

            msg.setContent(mp);
            Transport.send(msg);
            return true;
        } catch (Exception e) {
            if (this.simulateMode) {
                logSimulation(targetTo, targetSubject, targetBody);
                return true;
            }
            throw new RuntimeException("Falha no envio de e-mail: " + e.getMessage(), e);
        }
    }

    private void logSimulation(String to, String subject, String htmlBody) {
        try {
            String root = Config.getWebappRoot();
            String relStorage = Config.getString("paths.storage", "WEB-INF/storage");
            File logDir = new File(new File(root, relStorage), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "mail.log");
            String plainText = htmlBody != null ? htmlBody.replaceAll("<[^>]*>", "").trim() : "";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String entry = String.format(
                    "[%s] SIMULAÇÃO DE E-MAIL%nPara: %s%nAssunto: %s%nCorpo:%n%s%n%s%n",
                    timestamp,
                    to,
                    subject,
                    plainText,
                    "-".repeat(60)
            );
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

    public static boolean quickSend(String to, String subject, String htmlBody) {
        return new Mail().send(to, subject, htmlBody);
    }

    public static boolean quickSend(String toEmail, String toName, String subject, String htmlBody) {
        return new Mail().setRecipient(toEmail, toName).send(toEmail, subject, htmlBody);
    }
}
