package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ZTF_J2EE - Model Agenda (consultas)
 *
 * Agendamento de consultas (RF-AGE-01..07) com as regras RN-AGE-01..09:
 * - o horario esta na disponibilidade do medico e alinhado a duracao da consulta;
 * - nao ha duas consultas ativas do mesmo medico (ou do mesmo paciente) que se sobreponham;
 * - nada no passado, nem com paciente, medico ou especialidade inativos;
 * - a especialidade pertence ao medico.
 *
 * Concorrencia: o agendamento trava a linha do medico (SELECT ... FOR UPDATE) antes de
 * conferir sobreposicao, e a UNIQUE (id_medico, data_hora, slot_ativo) do banco e a ultima
 * barreira. Duas recepcionistas clicando no mesmo horario: so uma grava.
 */
public class Appointments {

    public static final ZoneOffset TIMEZONE = ZoneOffset.ofHours(-3);

    public static LocalDateTime now() {
        return LocalDateTime.now(TIMEZONE).withSecond(0).withNano(0);
    }

    /** Resultado de uma operacao: ok, ou a mensagem para o usuario. */
    public record Result(boolean ok, long id, String error) {
        static Result failure(String error) {
            return new Result(false, 0, error);
        }
    }

    public record Slot(LocalTime time, boolean free) {
    }

    // ------------------------------------------------------------------ consultas de apoio

    public List<Map<String, Object>> activeSpecialties() {
        return Sql.list("SELECT e.id_especialidade, e.nome FROM especialidades e WHERE e.ativo = 1 "
                + "AND EXISTS (SELECT 1 FROM medico_especialidades me JOIN profissionais p ON p.id_profissional = me.id_profissional "
                + "WHERE me.id_especialidade = e.id_especialidade AND p.ativo = 1) ORDER BY e.nome");
    }

    public List<Map<String, Object>> specialtyDoctors(int specialtyId) {
        return Sql.list("SELECT p.id_profissional, p.nome, m.crm, m.crm_uf, m.duracao_consulta_min "
                + "FROM profissionais p JOIN medicos m ON m.id_profissional = p.id_profissional "
                + "JOIN medico_especialidades me ON me.id_profissional = p.id_profissional "
                + "WHERE me.id_especialidade = ? AND p.ativo = 1 ORDER BY p.nome", specialtyId);
    }

    /** Medicos que atendem no dia (para a linha do dia), com o horario de cada um. */
    public List<Map<String, Object>> doctorsOnDay(LocalDate day) {
        return Sql.list("SELECT p.id_profissional, p.nome, MIN(d.hora_inicio) AS inicio, MAX(d.hora_fim) AS fim, "
                + "(SELECT GROUP_CONCAT(e.nome ORDER BY e.nome SEPARATOR ', ') FROM medico_especialidades me "
                + " JOIN especialidades e ON e.id_especialidade = me.id_especialidade WHERE me.id_profissional = p.id_profissional) AS especialidades "
                + "FROM profissionais p JOIN disponibilidades_medico d ON d.id_profissional = p.id_profissional "
                + "WHERE p.ativo = 1 AND d.ativo = 1 AND d.dia_semana = ? GROUP BY p.id_profissional, p.nome ORDER BY MIN(d.hora_inicio), p.nome",
                day.getDayOfWeek().getValue());
    }

    /** Consultas do dia (opcionalmente de um medico), com paciente, medico e especialidade. */
    public List<Map<String, Object>> ofDay(LocalDate day, Integer doctorId) {
        String sql = "SELECT c.id_consulta, c.data_hora, c.duracao_min, c.status, c.tipo, c.chegada_em, "
                + "c.id_paciente, pa.nome AS paciente, c.id_medico, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.data_hora >= ? AND c.data_hora < ?" + (doctorId != null ? " AND c.id_medico = ?" : "")
                + " ORDER BY c.data_hora, pr.nome";
        return doctorId != null
                ? Sql.list(sql, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), doctorId)
                : Sql.list(sql, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /**
     * Horarios do medico no dia, na grade da duracao da consulta, marcando os ocupados e os ja
     * passados (hoje). Vazio se o medico nao atende nesse dia.
     */
    public List<Slot> slots(int doctorId, LocalDate day) {
        Map<String, Object> doctor = Sql.one("SELECT m.duracao_consulta_min FROM medicos m JOIN profissionais p "
                + "ON p.id_profissional = m.id_profissional WHERE m.id_profissional = ? AND p.ativo = 1", doctorId);
        if (doctor == null) {
            return List.of();
        }
        int duration = ((Number) doctor.get("duracao_consulta_min")).intValue();
        List<Map<String, Object>> ranges = Sql.list("SELECT hora_inicio, hora_fim FROM disponibilidades_medico "
                + "WHERE id_profissional = ? AND dia_semana = ? AND ativo = 1 ORDER BY hora_inicio", doctorId, day.getDayOfWeek().getValue());
        List<Map<String, Object>> taken = Sql.list("SELECT data_hora, duracao_min FROM consultas WHERE id_medico = ? "
                + "AND slot_ativo = 1 AND data_hora >= ? AND data_hora < ?", doctorId, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        LocalDateTime now = now();
        List<Slot> list = new ArrayList<>();
        for (Map<String, Object> f : ranges) {
            LocalTime t = time(f.get("hora_inicio"));
            LocalTime end = time(f.get("hora_fim"));
            while (!t.plusMinutes(duration).isAfter(end)) {
                LocalDateTime start = day.atTime(t);
                boolean free = start.isAfter(now) && !overlaps(taken, start, duration);
                list.add(new Slot(t, free));
                t = t.plusMinutes(duration);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ operacoes

    public Result book(int patientId, int doctorId, int specialtyId, LocalDateTime dateTime, int userId) {
        if (dateTime == null) {
            return Result.failure("Escolha a data e o horário.");
        }
        if (!dateTime.isAfter(now())) {
            return Result.failure("Não é possível agendar no passado.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Result r = reserve(conn, patientId, doctorId, specialtyId, dateTime, userId, null);
                if (!r.ok()) {
                    conn.rollback();
                    return r;
                }
                AuditLog.record(conn, userId, "consulta.agendada", "consultas", r.id(), patientId, null, null);
                conn.commit();
                return r;
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.isDuplicate(e)) {
                    return Result.failure("Horário indisponível: acabou de ser reservado por outra pessoa.");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao agendar consulta", e);
        }
    }

    /**
     * RN-AGE-07: remarcar = cancelar a consulta original e criar outra ligada a ela
     * (remarcada_de), na mesma transacao. Vale so para consulta agendada (antes da chegada).
     * O paciente e a especialidade se mantem; o medico pode mudar, se atender a especialidade.
     */
    public Result reschedule(int appointmentId, int doctorId, LocalDateTime newDateTime, String reason, int userId) {
        if (newDateTime == null) {
            return Result.failure("Escolha a nova data e o horário.");
        }
        if (!newDateTime.isAfter(now())) {
            return Result.failure("Não é possível remarcar para o passado.");
        }
        String m = reason == null ? "" : reason.trim();
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> c = Sql.one(conn, "SELECT id_paciente, id_medico, id_especialidade, data_hora, status "
                        + "FROM consultas WHERE id_consulta = ? FOR UPDATE", appointmentId);
                if (c == null) {
                    conn.rollback();
                    return Result.failure("Consulta não encontrada.");
                }
                if (!"agendada".equals(c.get("status"))) {
                    conn.rollback();
                    return Result.failure("Só consultas agendadas (antes da chegada) podem ser remarcadas.");
                }
                int patientId = ((Number) c.get("id_paciente")).intValue();
                int specialtyId = ((Number) c.get("id_especialidade")).intValue();
                if (doctorId == ((Number) c.get("id_medico")).intValue() && newDateTime.equals(dateTime(c.get("data_hora")))) {
                    conn.rollback();
                    return Result.failure("Escolha um horário diferente do atual.");
                }
                String record = "Remarcada para "
                        + newDateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        + (m.isEmpty() ? "" : " · " + m);
                if (record.length() > 255) {
                    record = record.substring(0, 255);
                }
                Sql.execute(conn, "UPDATE consultas SET status = 'cancelada', motivo_cancelamento = ?, cancelada_em = NOW(), "
                        + "cancelada_por = ?, atualizado_por = ? WHERE id_consulta = ?", record, userId, userId, appointmentId);
                Result r = reserve(conn, patientId, doctorId, specialtyId, newDateTime, userId, appointmentId);
                if (!r.ok()) {
                    conn.rollback();
                    return r;
                }
                AuditLog.record(conn, userId, "consulta.remarcada", "consultas", r.id(), patientId,
                        "remarcada_de=" + appointmentId, null);
                conn.commit();
                return r;
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.isDuplicate(e)) {
                    return Result.failure("Horário indisponível: acabou de ser reservado por outra pessoa.");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao remarcar consulta", e);
        }
    }

    /**
     * Confere as regras RN-AGE-01..05 e grava a consulta, dentro da transacao de quem chama.
     * Trava a linha do medico (FOR UPDATE) antes de conferir sobreposicao.
     */
    private static Result reserve(Connection conn, int patientId, int doctorId, int specialtyId,
                                      LocalDateTime dateTime, int userId, Integer rescheduledFrom) throws SQLException {
        Map<String, Object> doctor = Sql.one(conn, "SELECT m.duracao_consulta_min FROM medicos m "
                + "JOIN profissionais p ON p.id_profissional = m.id_profissional "
                + "WHERE m.id_profissional = ? AND p.ativo = 1 FOR UPDATE", doctorId);
        if (doctor == null) {
            return Result.failure("Médico inativo ou inexistente.");
        }
        int duration = ((Number) doctor.get("duracao_consulta_min")).intValue();
        if (Sql.one(conn, "SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", patientId) == null) {
            return Result.failure("Paciente inativo ou inexistente.");
        }
        if (Sql.one(conn, "SELECT 1 FROM medico_especialidades me JOIN especialidades e ON e.id_especialidade = me.id_especialidade "
                + "WHERE me.id_profissional = ? AND me.id_especialidade = ? AND e.ativo = 1", doctorId, specialtyId) == null) {
            return Result.failure("A especialidade escolhida não é atendida por este médico.");
        }
        if (!withinAvailability(conn, doctorId, dateTime, duration)) {
            return Result.failure("Horário fora da agenda do médico.");
        }
        LocalDateTime end = dateTime.plusMinutes(duration);
        if (Sql.one(conn, "SELECT 1 FROM consultas WHERE id_medico = ? AND slot_ativo = 1 "
                + "AND data_hora < ? AND DATE_ADD(data_hora, INTERVAL duracao_min MINUTE) > ?", doctorId, end, dateTime) != null) {
            return Result.failure("Horário indisponível: o médico já tem consulta nesse horário.");
        }
        if (Sql.one(conn, "SELECT 1 FROM consultas WHERE id_paciente = ? AND slot_ativo = 1 "
                + "AND data_hora < ? AND DATE_ADD(data_hora, INTERVAL duracao_min MINUTE) > ?", patientId, end, dateTime) != null) {
            return Result.failure("O paciente já tem consulta nesse horário.");
        }
        long id = Sql.insert(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, duracao_min, "
                + "remarcada_de, criado_por, atualizado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                patientId, doctorId, specialtyId, dateTime, duration, rescheduledFrom, userId, userId);
        return new Result(true, id, null);
    }

    /** Dados da consulta para a tela de remarcar (sem dado clinico). */
    public Map<String, Object> forReschedule(int appointmentId) {
        return Sql.one("SELECT c.id_consulta, c.id_paciente, c.id_medico, c.id_especialidade, c.data_hora, c.status, "
                + "pa.nome AS paciente, pa.cpf, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade WHERE c.id_consulta = ?", appointmentId);
    }

    /** RN-AGE-09: chegada so no dia da consulta, para consulta agendada. */
    public Result recordArrival(int appointmentId, int userId) {
        Map<String, Object> c = Sql.one("SELECT id_paciente, data_hora, status FROM consultas WHERE id_consulta = ?", appointmentId);
        if (c == null) {
            return Result.failure("Consulta não encontrada.");
        }
        if (!"agendada".equals(c.get("status"))) {
            return Result.failure("Só é possível registrar a chegada de uma consulta agendada.");
        }
        if (!dateTime(c.get("data_hora")).toLocalDate().equals(now().toLocalDate())) {
            return Result.failure("A chegada só pode ser registrada no dia da consulta.");
        }
        int n = Sql.execute("UPDATE consultas SET status = 'aguardando_triagem', chegada_em = NOW(), atualizado_por = ? "
                + "WHERE id_consulta = ? AND status = 'agendada'", userId, appointmentId);
        if (n == 0) {
            return Result.failure("A consulta mudou de situação. Atualize a tela.");
        }
        AuditLog.record(userId, "consulta.chegada", "consultas", appointmentId, ((Number) c.get("id_paciente")).intValue(), null, null);
        return new Result(true, appointmentId, null);
    }

    /** RN-AGE-06: cancelamento com motivo, so antes do atendimento comecar. */
    public Result cancel(int appointmentId, String reason, int userId) {
        String m = reason == null ? "" : reason.trim();
        if (m.length() < 3) {
            return Result.failure("Informe o motivo do cancelamento.");
        }
        if (m.length() > 255) {
            m = m.substring(0, 255);
        }
        Map<String, Object> c = Sql.one("SELECT id_paciente FROM consultas WHERE id_consulta = ?", appointmentId);
        if (c == null) {
            return Result.failure("Consulta não encontrada.");
        }
        int n = Sql.execute("UPDATE consultas SET status = 'cancelada', motivo_cancelamento = ?, cancelada_em = NOW(), "
                + "cancelada_por = ?, atualizado_por = ? WHERE id_consulta = ? AND status IN ('agendada', 'aguardando_triagem')",
                m, userId, userId, appointmentId);
        if (n == 0) {
            return Result.failure("Só consultas ainda não iniciadas podem ser canceladas.");
        }
        AuditLog.record(userId, "consulta.cancelada", "consultas", appointmentId, ((Number) c.get("id_paciente")).intValue(), null, null);
        return new Result(true, appointmentId, null);
    }

    /** Dados para a notificacao (sem dado clinico: data, hora, medico e especialidade). */
    public Map<String, Object> noticeSummary(long appointmentId) {
        return Sql.one("SELECT c.data_hora, pa.nome AS paciente, pa.email, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade WHERE c.id_consulta = ?", appointmentId);
    }

    // ------------------------------------------------------------------ auxiliares

    private static boolean withinAvailability(Connection conn, int doctorId, LocalDateTime start, int duration) throws SQLException {
        List<Map<String, Object>> ranges = Sql.list(conn, "SELECT hora_inicio, hora_fim FROM disponibilidades_medico "
                + "WHERE id_profissional = ? AND dia_semana = ? AND ativo = 1", doctorId, start.getDayOfWeek().getValue());
        LocalTime t = start.toLocalTime();
        for (Map<String, Object> f : ranges) {
            LocalTime ini = time(f.get("hora_inicio"));
            LocalTime end = time(f.get("hora_fim"));
            boolean aligned = java.time.Duration.between(ini, t).toMinutes() % duration == 0;
            if (!t.isBefore(ini) && !t.plusMinutes(duration).isAfter(end) && aligned) {
                return true;
            }
        }
        return false;
    }

    static boolean overlaps(List<Map<String, Object>> taken, LocalDateTime start, int duration) {
        LocalDateTime end = start.plusMinutes(duration);
        for (Map<String, Object> o : taken) {
            LocalDateTime oi = dateTime(o.get("data_hora"));
            LocalDateTime of = oi.plusMinutes(((Number) o.get("duracao_min")).intValue());
            if (oi.isBefore(end) && of.isAfter(start)) {
                return true;
            }
        }
        return false;
    }

    static LocalTime time(Object v) {
        if (v instanceof LocalTime) {
            return (LocalTime) v;
        }
        if (v instanceof Time) {
            return ((Time) v).toLocalTime();
        }
        return LocalTime.parse(String.valueOf(v));
    }

    static LocalDateTime dateTime(Object v) {
        if (v instanceof LocalDateTime) {
            return (LocalDateTime) v;
        }
        if (v instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) v).toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));
    }

    /** Converte uma linha para exibicao: hora HH:mm e data dd/MM. */
    public static Map<String, Object> withTime(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>(row);
        LocalDateTime dt = dateTime(row.get("data_hora"));
        r.put("hora", String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
        r.put("minuto_do_dia", dt.getHour() * 60 + dt.getMinute());
        return r;
    }
}
