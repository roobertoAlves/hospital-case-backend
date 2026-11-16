package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Vacina
 *
 * Catalogo de vacinas, agenda da sala de vacinacao e cartao de vacinacao.
 * - Sala: segunda a sexta, 08h-17h, um paciente a cada 15 min (UNIQUE no banco).
 * - Dose: a proxima do esquema; nao agenda alem do total de doses nem antes do intervalo
 *   minimo desde a ultima aplicacao; uma dose pendente por vacina.
 * - Aplicacao: so a enfermagem registra (lote obrigatorio), no dia; o registro e imutavel (triggers).
 */
public class Vaccine {

    public static final LocalTime OPENS = LocalTime.of(8, 0);
    public static final LocalTime CLOSES = LocalTime.of(17, 0);
    public static final int STEP_MIN = 15;

    public List<Map<String, Object>> catalog() {
        return Sql.list("SELECT id_vacina, nome, descricao, doses, intervalo_dias, publico, faixa FROM vacinas WHERE ativo = 1 ORDER BY nome");
    }

    public List<Appointments.Slot> slots(LocalDate day) {
        List<Appointments.Slot> list = new ArrayList<>();
        if (day == null || day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return list;
        }
        List<Object> taken = new ArrayList<>();
        for (Map<String, Object> r : Sql.list("SELECT data_hora FROM vacinas_agendadas WHERE slot_ativo = 1 AND data_hora >= ? AND data_hora < ?",
                day.atStartOfDay(), day.plusDays(1).atStartOfDay())) {
            taken.add(Appointments.dateTime(r.get("data_hora")));
        }
        LocalDateTime now = Appointments.now();
        for (LocalTime t = OPENS; t.isBefore(CLOSES); t = t.plusMinutes(STEP_MIN)) {
            LocalDateTime start = day.atTime(t);
            list.add(new Appointments.Slot(t, start.isAfter(now) && !taken.contains(start)));
        }
        return list;
    }

    public Appointments.Result book(int patientId, int vaccineId, LocalDateTime when, int userId, String origin) {
        if (when == null || !when.isAfter(Appointments.now())) {
            return Appointments.Result.failure("Escolha uma data e um horário futuros.");
        }
        LocalTime t = when.toLocalTime();
        DayOfWeek d = when.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY || t.isBefore(OPENS) || !t.isBefore(CLOSES) || t.getMinute() % STEP_MIN != 0) {
            return Appointments.Result.failure("Horário fora do atendimento da sala de vacinação.");
        }
        Map<String, Object> v = Sql.one("SELECT doses, intervalo_dias FROM vacinas WHERE id_vacina = ? AND ativo = 1", vaccineId);
        if (v == null) {
            return Appointments.Result.failure("Vacina indisponível.");
        }
        if (Sql.one("SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", patientId) == null) {
            return Appointments.Result.failure("Paciente inativo ou inexistente.");
        }
        if (Sql.one("SELECT 1 FROM vacinas_agendadas WHERE id_paciente = ? AND id_vacina = ? AND status = 'agendada'", patientId, vaccineId) != null) {
            return Appointments.Result.failure("Já existe uma dose desta vacina agendada.");
        }
        Map<String, Object> last = Sql.one("SELECT COUNT(*) AS n, MAX(aplicada_em) AS ultima FROM vacinas_aplicadas WHERE id_paciente = ? AND id_vacina = ?",
                patientId, vaccineId);
        int applied = ((Number) last.get("n")).intValue();
        int doses = ((Number) v.get("doses")).intValue();
        if (applied >= doses && doses > 1) {
            return Appointments.Result.failure("Esquema completo: todas as " + doses + " doses já foram aplicadas.");
        }
        Object interval = v.get("intervalo_dias");
        if (applied > 0 && interval != null && last.get("ultima") != null
                && when.isBefore(Appointments.dateTime(last.get("ultima")).plusDays(((Number) interval).longValue()))) {
            return Appointments.Result.failure("A próxima dose só pode ser a partir de "
                    + Appointments.dateTime(last.get("ultima")).plusDays(((Number) interval).longValue()).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        int dose = doses == 1 ? 1 : applied + 1;
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO vacinas_agendadas (id_paciente, id_vacina, dose, data_hora, origem, criado_por) VALUES (?, ?, ?, ?, ?, ?)",
                    patientId, vaccineId, dose, when, "portal".equals(origin) ? "portal" : "recepcao", userId);
            AuditLog.record(conn, userId, "vacina.agendada", "vacinas_agendadas", id, patientId, "dose=" + dose, null);
            return new Appointments.Result(true, id, null);
        } catch (SQLException e) {
            if (Sql.isDuplicate(e)) {
                return Appointments.Result.failure("Horário indisponível: acabou de ser reservado por outra pessoa.");
            }
            throw new RuntimeException("Falha ao agendar vacina", e);
        }
    }

    /** Cancela; com idPacienteDono, so se o agendamento for dele (portal). */
    public Appointments.Result cancel(int bookingId, String reason, int userId, Integer ownerPatientId) {
        String m = reason == null ? "" : Sanitize.string(reason).trim();
        if (m.length() < 3) {
            return Appointments.Result.failure("Informe o motivo do cancelamento.");
        }
        Map<String, Object> a = Sql.one("SELECT id_paciente FROM vacinas_agendadas WHERE id_agendamento = ?", bookingId);
        if (a == null || (ownerPatientId != null && ((Number) a.get("id_paciente")).intValue() != ownerPatientId)) {
            return Appointments.Result.failure("Agendamento não encontrado.");
        }
        int n = Sql.execute("UPDATE vacinas_agendadas SET status = 'cancelada', motivo_cancelamento = ? WHERE id_agendamento = ? AND status = 'agendada'",
                m.length() > 255 ? m.substring(0, 255) : m, bookingId);
        if (n == 0) {
            return Appointments.Result.failure("Só agendamentos ainda não realizados podem ser cancelados.");
        }
        AuditLog.record(userId, "vacina.cancelada", "vacinas_agendadas", bookingId, ((Number) a.get("id_paciente")).intValue(), null, null);
        return new Appointments.Result(true, bookingId, null);
    }

    /** Enfermagem registra a aplicacao (no dia do agendamento). Devolve erros por campo ou vazio. */
    public Map<String, String> apply(int bookingId, Map<String, Object> data, int nurseId, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String batch = text(data.get("lote"));
        String manufacturer = text(data.get("fabricante"));
        String obs = text(data.get("observacoes"));
        if (batch.length() < 2 || batch.length() > 40) {
            errors.put("lote", "Informe o lote (até 40 caracteres).");
        }
        if (manufacturer.length() > 80) {
            errors.put("fabricante", "Até 80 caracteres.");
        }
        if (obs.length() > 255) {
            errors.put("observacoes", "Até 255 caracteres.");
        }
        Map<String, Object> a = Sql.one("SELECT id_paciente, id_vacina, dose, data_hora, status FROM vacinas_agendadas WHERE id_agendamento = ?", bookingId);
        if (a == null) {
            errors.put("_", "Agendamento não encontrado.");
        } else if (!"agendada".equals(a.get("status"))) {
            errors.put("_", "Este agendamento não está mais pendente.");
        } else if (!Appointments.dateTime(a.get("data_hora")).toLocalDate().equals(Appointments.now().toLocalDate())) {
            errors.put("_", "A aplicação é registrada no dia agendado.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        int patientId = ((Number) a.get("id_paciente")).intValue();
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                int n = Sql.execute(conn, "UPDATE vacinas_agendadas SET status = 'aplicada' WHERE id_agendamento = ? AND status = 'agendada'", bookingId);
                if (n == 0) {
                    conn.rollback();
                    errors.put("_", "Este agendamento acabou de mudar. Atualize a tela.");
                    return errors;
                }
                long id = Sql.insert(conn, "INSERT INTO vacinas_aplicadas (id_paciente, id_vacina, dose, lote, fabricante, id_enfermeiro, id_agendamento, observacoes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", patientId, a.get("id_vacina"), a.get("dose"), batch, manufacturer.isEmpty() ? null : manufacturer,
                        nurseId, bookingId, obs.isEmpty() ? null : obs);
                AuditLog.record(conn, userId, "vacina.aplicada", "vacinas_aplicadas", id, patientId, null, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar aplicacao", e);
        }
        return errors;
    }

    public Appointments.Result absent(int bookingId, int userId) {
        Map<String, Object> a = Sql.one("SELECT id_paciente FROM vacinas_agendadas WHERE id_agendamento = ?", bookingId);
        int n = Sql.execute("UPDATE vacinas_agendadas SET status = 'ausente' WHERE id_agendamento = ? AND status = 'agendada' AND data_hora <= NOW()", bookingId);
        if (a == null || n == 0) {
            return Appointments.Result.failure("Só agendamentos pendentes cujo horário já passou podem ser marcados como falta.");
        }
        AuditLog.record(userId, "vacina.ausente", "vacinas_agendadas", bookingId, ((Number) a.get("id_paciente")).intValue(), null, null);
        return new Appointments.Result(true, bookingId, null);
    }

    public List<Map<String, Object>> daySchedule(LocalDate day) {
        return Sql.list("SELECT a.id_agendamento, a.data_hora, a.dose, a.status, a.origem, a.id_paciente, p.nome AS paciente, p.data_nascimento, "
                + "v.nome AS vacina, v.doses FROM vacinas_agendadas a JOIN pacientes p ON p.id_paciente = a.id_paciente "
                + "JOIN vacinas v ON v.id_vacina = a.id_vacina WHERE a.data_hora >= ? AND a.data_hora < ? ORDER BY a.data_hora",
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /** Cartao de vacinacao (aplicadas) e agendamentos do paciente. */
    public Map<String, Object> card(int patientId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("aplicadas", Sql.list("SELECT ap.id_aplicacao, ap.dose, ap.lote, ap.fabricante, ap.aplicada_em, v.id_vacina, v.nome AS vacina, v.doses, "
                + "pr.nome AS aplicador FROM vacinas_aplicadas ap JOIN vacinas v ON v.id_vacina = ap.id_vacina "
                + "LEFT JOIN profissionais pr ON pr.id_profissional = ap.id_enfermeiro WHERE ap.id_paciente = ? ORDER BY ap.aplicada_em DESC", patientId));
        d.put("agendadas", Sql.list("SELECT a.id_agendamento, a.data_hora, a.dose, a.status, a.motivo_cancelamento, v.nome AS vacina "
                + "FROM vacinas_agendadas a JOIN vacinas v ON v.id_vacina = a.id_vacina WHERE a.id_paciente = ? AND a.status IN ('agendada', 'cancelada', 'ausente') "
                + "ORDER BY a.data_hora DESC LIMIT 50", patientId));
        return d;
    }

    private static String text(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
