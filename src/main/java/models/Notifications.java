package models;

import database.Sql;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import mail.Mail;
import utils.Config;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Notificacao
 *
 * Envia avisos ao paciente (RF-NOT-01/02) pelo mail.Mail (SMTP ou simulacao em mail.log) e
 * registra cada envio na tabela notificacoes (destinatario, tipo, status, erro).
 * RN-NOT-01: o e-mail leva so data, hora, local e profissional; nunca dado clinico (nem o
 * nome do exame). RNF-15: falha no envio fica registrada e nao desfaz a operacao.
 */
public final class Notifications {

    private static final Logger LOG = Logger.getLogger(Notifications.class.getName());
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'às' HH:mm", Locale.forLanguageTag("pt-BR"));

    private Notifications() {
    }

    public static void scheduledAppointment(Map<String, Object> c, long appointmentId, int userId) {
        send("consulta.agendada", c, "Consulta agendada",
                "Sua consulta de <strong>" + esc(c.get("especialidade")) + "</strong> com " + esc(c.get("medico"))
                        + " está marcada para <strong>" + when(c) + "</strong>.",
                appointmentId, userId);
    }

    public static void cancelledAppointment(Map<String, Object> c, long appointmentId, int userId) {
        send("consulta.cancelada", c, "Consulta cancelada",
                "Sua consulta de " + when(c) + " com " + esc(c.get("medico")) + " foi cancelada. "
                        + "Para remarcar, fale com a recepção.", appointmentId, userId);
    }

    /** RF-NOT-01: remarcacao (a consulta antiga fica cancelada; o aviso traz so o novo horario). */
    public static void rescheduledAppointment(Map<String, Object> c, long appointmentId, int userId) {
        send("consulta.remarcada", c, "Consulta remarcada",
                "Sua consulta de <strong>" + esc(c.get("especialidade")) + "</strong> foi remarcada para <strong>"
                        + when(c) + "</strong>, com " + esc(c.get("medico")) + ". O horário anterior foi liberado.",
                appointmentId, userId);
    }

    public static void scheduledExam(Map<String, Object> x, int userId) {
        send("exame.agendado", x, "Exame agendado",
                "Seu exame está marcado para <strong>" + when(x) + "</strong>, no local "
                        + esc(x.get("local")) + ". As orientações de preparo são entregues pela recepção.",
                null, userId);
    }

    /**
     * Notificacao administrativa: acesso criado no cadastro do profissional. A senha
     * temporaria vai so no e-mail (o corpo nao e gravado) e precisa ser trocada no primeiro acesso.
     */
    public static void createdAccess(String email, String name, String profile, String temporaryPassword, int userId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("email", email);
        data.put("paciente", name);
        send("usuario.acesso_criado", data, "Seu acesso ao sistema hospitalar",
                "Foi criado um acesso para você com o perfil <strong>" + esc(profile) + "</strong>.<br>"
                        + "Login: <strong>" + esc(email) + "</strong><br>"
                        + "Senha temporária: <strong>" + esc(temporaryPassword) + "</strong><br>"
                        + "No primeiro acesso o sistema pede uma senha nova. Não compartilhe esta mensagem.",
                null, userId);
    }

    /** Portal: codigo para ligar a conta a ficha ja existente (vai para o e-mail DA FICHA). */
    public static void linkCode(String recordEmail, String name, String code, int userId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("email", recordEmail);
        data.put("paciente", name);
        send("portal.codigo_vinculo", data, "Código para liberar seu acesso ao portal",
                "Alguém criou uma conta no portal do paciente com o seu CPF. Se foi você, use o código "
                        + "<strong style='font-size:20px;letter-spacing:3px'>" + esc(code) + "</strong> para liberar o acesso. "
                        + "Ele vale por 15 minutos.<br>Se não foi você, ignore esta mensagem e avise a recepção.",
                null, userId);
    }

    /** Portal: pedido medico recusado (o exame que dependia dele foi cancelado). Sem nome do exame. */
    public static void rejectedOrder(String email, String name, String reason, int userId) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("email", email);
        data.put("paciente", name);
        send("portal.pedido_recusado", data, "Pedido médico não aceito",
                "Não foi possível aceitar o pedido médico que você anexou no portal (" + esc(reason) + "). "
                        + "O agendamento que dependia dele foi cancelado. Envie um novo pedido pelo portal ou fale com a recepção.",
                null, userId);
    }

    private static void send(String type, Map<String, Object> data, String subject, String message,
                               Long appointmentId, int userId) {
        Object email = data.get("email");
        if (email == null || String.valueOf(email).isBlank()) {
            return;
        }
        String html = "<div style='font-family:Arial,sans-serif;max-width:560px'>"
                + "<p>Olá, " + esc(data.get("paciente")) + ".</p><p>" + message + "</p>"
                + "<p style='color:#55595c;font-size:13px'>Mensagem automática. Em caso de dúvida, fale com a recepção.</p></div>";
        boolean simulated = Config.getBoolean("mail.simulate_in_dev", false)
                && Config.getString("mail.password", "").isEmpty();
        String status;
        String error = null;
        try {
            Mail.quickSend(String.valueOf(email), subject, html);
            status = simulated ? "simulada" : "enviada";
        } catch (RuntimeException e) {
            status = "falhou";
            error = e.getMessage();
            LOG.log(Level.WARNING, "Falha ao enviar notificacao " + type, e);
        }
        try {
            Sql.execute("INSERT INTO notificacoes (tipo, destinatario, assunto, id_consulta, id_usuario, status, erro, enviado_em) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, IF(? = 'falhou', NULL, NOW()))",
                    type, String.valueOf(email), subject, appointmentId, userId > 0 ? userId : null, status,
                    error != null && error.length() > 500 ? error.substring(0, 500) : error, status);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao registrar notificacao " + type, e);
        }
    }

    private static String when(Map<String, Object> data) {
        LocalDateTime dt = Appointments.dateTime(data.get("data_hora"));
        return dt.format(WHEN);
    }

    private static String esc(Object v) {
        return Sanitize.escapeHtml(v == null ? "" : String.valueOf(v));
    }
}
