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
public final class Notificacao {

    private static final Logger LOG = Logger.getLogger(Notificacao.class.getName());
    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'às' HH:mm", new Locale("pt", "BR"));

    private Notificacao() {
    }

    public static void consultaAgendada(Map<String, Object> c, long idConsulta, int idUsuario) {
        enviar("consulta.agendada", c, "Consulta agendada",
                "Sua consulta de <strong>" + esc(c.get("especialidade")) + "</strong> com " + esc(c.get("medico"))
                        + " está marcada para <strong>" + quando(c) + "</strong>.",
                idConsulta, idUsuario);
    }

    public static void consultaCancelada(Map<String, Object> c, long idConsulta, int idUsuario) {
        enviar("consulta.cancelada", c, "Consulta cancelada",
                "Sua consulta de " + quando(c) + " com " + esc(c.get("medico")) + " foi cancelada. "
                        + "Para remarcar, fale com a recepção.", idConsulta, idUsuario);
    }

    public static void exameAgendado(Map<String, Object> x, int idUsuario) {
        enviar("exame.agendado", x, "Exame agendado",
                "Seu exame está marcado para <strong>" + quando(x) + "</strong>, no local "
                        + esc(x.get("local")) + ". As orientações de preparo são entregues pela recepção.",
                null, idUsuario);
    }

    private static void enviar(String tipo, Map<String, Object> dados, String assunto, String mensagem,
                               Long idConsulta, int idUsuario) {
        Object email = dados.get("email");
        if (email == null || String.valueOf(email).isBlank()) {
            return;
        }
        String html = "<div style='font-family:Arial,sans-serif;max-width:560px'>"
                + "<p>Olá, " + esc(dados.get("paciente")) + ".</p><p>" + mensagem + "</p>"
                + "<p style='color:#55595c;font-size:13px'>Mensagem automática. Em caso de dúvida, fale com a recepção.</p></div>";
        boolean simulado = Config.getBoolean("mail.simulate_in_dev", false)
                && Config.getString("mail.password", "").isEmpty();
        String status;
        String erro = null;
        try {
            Mail.quickSend(String.valueOf(email), assunto, html);
            status = simulado ? "simulada" : "enviada";
        } catch (RuntimeException e) {
            status = "falhou";
            erro = e.getMessage();
            LOG.log(Level.WARNING, "Falha ao enviar notificacao " + tipo, e);
        }
        try {
            Sql.executar("INSERT INTO notificacoes (tipo, destinatario, assunto, id_consulta, id_usuario, status, erro, enviado_em) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, IF(? = 'falhou', NULL, NOW()))",
                    tipo, String.valueOf(email), assunto, idConsulta, idUsuario > 0 ? idUsuario : null, status,
                    erro != null && erro.length() > 500 ? erro.substring(0, 500) : erro, status);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao registrar notificacao " + tipo, e);
        }
    }

    private static String quando(Map<String, Object> dados) {
        LocalDateTime dt = Agenda.dataHora(dados.get("data_hora"));
        return dt.format(QUANDO);
    }

    private static String esc(Object v) {
        return Sanitize.escapeHtml(v == null ? "" : String.valueOf(v));
    }
}
