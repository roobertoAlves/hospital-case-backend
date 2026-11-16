package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ZTF_J2EE - ExamSchedule model
 *
 * Exam booking by the reception (requirement added in phase 5, migration 001):
 * - each exam type has a duration, preparation and a default location;
 * - the location works within opening hours (Monday to Friday, and Saturday when
 *   atende_sabado = 1);
 * - no two exams may overlap at the same location, nor the same patient in two exams at once;
 * - a booking may come from a doctor's request (table exames) or stand alone.
 * Concurrency as in Appointments: locks the location row (FOR UPDATE) plus the database UNIQUE.
 */
public class ExamSchedule {

    /** Offered slot grid: every 10 minutes within the location's opening hours. */
    static final int STEP_MIN = 10;

    public List<Map<String, Object>> activeTypes() {
        return Sql.list("SELECT t.id_tipo_exame, t.nome, t.categoria, t.duracao_min, t.preparo, t.id_local, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 AND l.ativo = 1 ORDER BY t.categoria, t.nome");
    }

    public List<Map<String, Object>> activeLocations() {
        return Sql.list("SELECT id_local, nome, hora_inicio, hora_fim, atende_sabado FROM locais_exame WHERE ativo = 1 ORDER BY nome");
    }

    public List<Map<String, Object>> ofDay(LocalDate day) {
        return Sql.list("SELECT x.id_exame_agendado, x.data_hora, x.duracao_min, x.status, x.id_paciente, pa.nome AS paciente, "
                + "t.nome AS exame, l.id_local, l.nome AS local "
                + "FROM exames_agendados x JOIN pacientes pa ON pa.id_paciente = x.id_paciente "
                + "JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame JOIN locais_exame l ON l.id_local = x.id_local "
                + "WHERE x.data_hora >= ? AND x.data_hora < ? ORDER BY x.data_hora, l.nome",
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /** Slots for an exam type on the day, at the type's location, flagging taken and past ones. */
    public List<Appointments.Slot> slots(int examTypeId, LocalDate day) {
        Map<String, Object> type = Sql.one("SELECT t.duracao_min, l.id_local, l.hora_inicio, l.hora_fim, l.atende_sabado "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local "
                + "WHERE t.id_tipo_exame = ? AND t.ativo = 1 AND l.ativo = 1", examTypeId);
        if (type == null || !locationServes(type, day)) {
            return List.of();
        }
        int duration = ((Number) type.get("duracao_min")).intValue();
        int idLocal = ((Number) type.get("id_local")).intValue();
        List<Map<String, Object>> taken = Sql.list("SELECT data_hora, duracao_min FROM exames_agendados WHERE id_local = ? "
                + "AND slot_ativo = 1 AND data_hora >= ? AND data_hora < ?", idLocal, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        LocalDateTime now = Appointments.now();
        List<Appointments.Slot> list = new ArrayList<>();
        LocalTime t = Appointments.time(type.get("hora_inicio"));
        LocalTime end = Appointments.time(type.get("hora_fim"));
        while (!t.plusMinutes(duration).isAfter(end)) {
            LocalDateTime start = day.atTime(t);
            list.add(new Appointments.Slot(t, start.isAfter(now) && !Appointments.overlaps(taken, start, duration)));
            t = t.plusMinutes(STEP_MIN);
        }
        return list;
    }

    public Appointments.Result book(int patientId, int examTypeId, LocalDateTime dateTime, Integer requestId, int userId) {
        if (dateTime == null) {
            return Appointments.Result.failure("Escolha a data e o horário.");
        }
        if (!dateTime.isAfter(Appointments.now())) {
            return Appointments.Result.failure("Não é possível agendar no passado.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> type = Sql.one(conn, "SELECT t.duracao_min, l.id_local, l.hora_inicio, l.hora_fim, l.atende_sabado "
                        + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local "
                        + "WHERE t.id_tipo_exame = ? AND t.ativo = 1 AND l.ativo = 1", examTypeId);
                if (type == null) {
                    conn.rollback();
                    return Appointments.Result.failure("Tipo de exame inativo ou inexistente.");
                }
                int idLocal = ((Number) type.get("id_local")).intValue();
                int duration = ((Number) type.get("duracao_min")).intValue();
                Sql.one(conn, "SELECT id_local FROM locais_exame WHERE id_local = ? FOR UPDATE", idLocal);

                if (Sql.one(conn, "SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", patientId) == null) {
                    conn.rollback();
                    return Appointments.Result.failure("Paciente inativo ou inexistente.");
                }
                LocalTime ini = Appointments.time(type.get("hora_inicio"));
                LocalTime endLocation = Appointments.time(type.get("hora_fim"));
                LocalTime t = dateTime.toLocalTime();
                if (!locationServes(type, dateTime.toLocalDate()) || t.isBefore(ini) || t.plusMinutes(duration).isAfter(endLocation)) {
                    conn.rollback();
                    return Appointments.Result.failure("Horário fora do atendimento do local do exame.");
                }
                if (requestId != null && Sql.one(conn, "SELECT 1 FROM exames x JOIN consultas c ON c.id_consulta = x.id_consulta "
                        + "WHERE x.id_exame = ? AND c.id_paciente = ? AND x.status = 'solicitado'", requestId, patientId) == null) {
                    conn.rollback();
                    return Appointments.Result.failure("Solicitação médica não encontrada para este paciente.");
                }
                LocalDateTime end = dateTime.plusMinutes(duration);
                String overlap = " AND slot_ativo = 1 AND data_hora < ? AND DATE_ADD(data_hora, INTERVAL duracao_min MINUTE) > ?";
                if (Sql.one(conn, "SELECT 1 FROM exames_agendados WHERE id_local = ?" + overlap, idLocal, end, dateTime) != null) {
                    conn.rollback();
                    return Appointments.Result.failure("Horário indisponível no local do exame.");
                }
                if (Sql.one(conn, "SELECT 1 FROM exames_agendados WHERE id_paciente = ?" + overlap, patientId, end, dateTime) != null) {
                    conn.rollback();
                    return Appointments.Result.failure("O paciente já tem exame nesse horário.");
                }
                long id = Sql.insert(conn, "INSERT INTO exames_agendados (id_paciente, id_tipo_exame, id_local, id_solicitacao, "
                        + "data_hora, duracao_min, criado_por) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        patientId, examTypeId, idLocal, requestId, dateTime, duration, userId);
                AuditLog.record(conn, userId, "exame.agendado", "exames_agendados", id, patientId, null, null);
                conn.commit();
                return new Appointments.Result(true, id, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.isDuplicate(e)) {
                    return Appointments.Result.failure("Horário indisponível: acabou de ser reservado por outra pessoa.");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not book the exam", e);
        }
    }

    public Appointments.Result cancel(int scheduledExamId, String reason, int userId) {
        String m = reason == null ? "" : reason.trim();
        if (m.length() < 3) {
            return Appointments.Result.failure("Informe o motivo do cancelamento.");
        }
        Map<String, Object> x = Sql.one("SELECT id_paciente FROM exames_agendados WHERE id_exame_agendado = ?", scheduledExamId);
        if (x == null) {
            return Appointments.Result.failure("Exame não encontrado.");
        }
        int n = Sql.execute("UPDATE exames_agendados SET status = 'cancelado', motivo_cancelamento = ?, cancelado_em = NOW() "
                + "WHERE id_exame_agendado = ? AND status = 'agendado'", m.length() > 255 ? m.substring(0, 255) : m, scheduledExamId);
        if (n == 0) {
            return Appointments.Result.failure("Só exames ainda agendados podem ser cancelados.");
        }
        AuditLog.record(userId, "exame.cancelado", "exames_agendados", scheduledExamId,
                ((Number) x.get("id_paciente")).intValue(), null, null);
        return new Appointments.Result(true, scheduledExamId, null);
    }

    /** Marks the exam as done or no-show (only on the day, for a scheduled exam). */
    public Appointments.Result recordStatus(int scheduledExamId, String status, int userId) {
        if (!"realizado".equals(status) && !"ausente".equals(status)) {
            return Appointments.Result.failure("Situação inválida.");
        }
        Map<String, Object> x = Sql.one("SELECT id_paciente, data_hora FROM exames_agendados WHERE id_exame_agendado = ? AND status = 'agendado'",
                scheduledExamId);
        if (x == null) {
            return Appointments.Result.failure("Só exames agendados podem ser atualizados.");
        }
        if (!Appointments.dateTime(x.get("data_hora")).toLocalDate().equals(Appointments.now().toLocalDate())) {
            return Appointments.Result.failure("A situação só pode ser registrada no dia do exame.");
        }
        Sql.execute("UPDATE exames_agendados SET status = ? WHERE id_exame_agendado = ? AND status = 'agendado'", status, scheduledExamId);
        AuditLog.record(userId, "exame." + status, "exames_agendados", scheduledExamId,
                ((Number) x.get("id_paciente")).intValue(), null, null);
        return new Appointments.Result(true, scheduledExamId, null);
    }

    /** Data for the notification: without the exam name (health data, RN-NOT-01). */
    public Map<String, Object> noticeSummary(long id) {
        return Sql.one("SELECT x.data_hora, pa.nome AS paciente, pa.email, l.nome AS local FROM exames_agendados x "
                + "JOIN pacientes pa ON pa.id_paciente = x.id_paciente JOIN locais_exame l ON l.id_local = x.id_local "
                + "WHERE x.id_exame_agendado = ?", id);
    }

    private static boolean locationServes(Map<String, Object> local, LocalDate day) {
        DayOfWeek d = day.getDayOfWeek();
        if (d == DayOfWeek.SUNDAY) {
            return false;
        }
        return d != DayOfWeek.SATURDAY || ((Number) local.get("atende_sabado")).intValue() == 1;
    }
}
