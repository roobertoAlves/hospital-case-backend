package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

public class Certificate {

    public static final int MAX_DAYS = 90;
    private static final List<String> TYPES = List.of("afastamento", "comparecimento", "acompanhante");

    public record Result(long id, Map<String, String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public Result issue(int appointmentId, int doctorId, Map<String, Object> data, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> c = Sql.one("SELECT id_paciente, data_hora FROM consultas WHERE id_consulta = ? AND id_medico = ? "
                + "AND status IN ('em_atendimento', 'realizada')", appointmentId, doctorId);
        if (c == null) {
            errors.put("general", "Só o médico do atendimento emite atestado, durante ou logo após a consulta.");
            return new Result(0, errors);
        }
        String type = Sanitize.string(data.get("tipo")).trim();
        if (!TYPES.contains(type)) {
            errors.put("tipo", "Escolha o tipo de atestado.");
        }
        LocalDate start = date(data.get("data_inicio"));
        if (start == null) {
            start = Appointments.now().toLocalDate();
        }
        Integer days = null;
        LocalTime from = null;
        LocalTime to = null;
        String companion = null;
        if ("afastamento".equals(type)) {
            int d = Sanitize.intVal(data.get("dias"), 0);
            if (d < 1 || d > MAX_DAYS) {
                errors.put("dias", "Informe de 1 a " + MAX_DAYS + " dias de afastamento.");
            }
            days = d;
        } else if (TYPES.contains(type)) {
            from = time(data.get("hora_inicio"));
            to = time(data.get("hora_fim"));
            if (from == null) {
                errors.put("hora_inicio", "Informe o horário de chegada.");
            }
            if (to == null) {
                errors.put("hora_fim", "Informe o horário de saída.");
            } else if (from != null && !to.isAfter(from)) {
                errors.put("hora_fim", "A saída deve ser depois da chegada.");
            }
            if ("acompanhante".equals(type)) {
                companion = Sanitize.string(data.get("acompanhante_nome")).trim();
                if (companion.length() < 3) {
                    errors.put("acompanhante_nome", "Informe o nome do acompanhante.");
                }
                companion = cut(companion, 150);
            }
        }
        String cid = Sanitize.string(data.get("cid10")).trim().toUpperCase();
        boolean authorized = List.of("1", "true", "on").contains(String.valueOf(data.get("cid_autorizado")).toLowerCase());
        if (!cid.isEmpty() && !cid.matches("^[A-Z][0-9]{2}(\\.[0-9A-Z]{1,2})?$")) {
            errors.put("cid10", "CID-10 inválido (ex.: J06.9).");
        }
        if (!cid.isEmpty() && !authorized) {
            errors.put("cid_autorizado", "O CID só entra no atestado com autorização do paciente.");
        }
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.insert(conn, "INSERT INTO atestados (id_consulta, id_medico, tipo, data_inicio, dias, hora_inicio, hora_fim, "
                            + "acompanhante_nome, cid10, observacoes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    appointmentId, doctorId, type, start, days, from, to, companion, cid.isEmpty() ? null : cid,
                    nullIfEmpty(cut(Sanitize.string(data.get("observacoes")).trim(), 500)));
            AuditLog.record(conn, userId, "atestado.emitido", "atestados", id, ((Number) c.get("id_paciente")).intValue(), type, null);
            return new Result(id, errors);
        } catch (SQLException e) {
            throw new RuntimeException("Could not issue the certificate", e);
        }
    }

    public Result cancel(int certificateId, int doctorId, String reason, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String m = Sanitize.string(reason).trim();
        if (m.length() < 3) {
            errors.put("motivo", "Informe o motivo do cancelamento.");
            return new Result(0, errors);
        }
        Map<String, Object> a = Sql.one("SELECT c.id_paciente FROM atestados a JOIN consultas c ON c.id_consulta = a.id_consulta "
                + "WHERE a.id_atestado = ? AND a.id_medico = ? AND a.status = 'emitido'", certificateId, doctorId);
        if (a == null) {
            errors.put("general", "Atestado não encontrado, de outro médico ou já cancelado.");
            return new Result(0, errors);
        }
        try (Connection conn = DBConnection.openConnection()) {
            Sql.execute(conn, "UPDATE atestados SET status = 'cancelado', cancelado_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_atestado = ? AND status = 'emitido'", cut(m, 255), certificateId);
            AuditLog.record(conn, userId, "atestado.cancelado", "atestados", certificateId, ((Number) a.get("id_paciente")).intValue(), null, null);
            return new Result(certificateId, errors);
        } catch (SQLException e) {
            throw new RuntimeException("Could not cancel the certificate", e);
        }
    }

    private static final String COLUMNS = "a.id_atestado, a.id_consulta, a.tipo, a.data_inicio, a.dias, a.hora_inicio, a.hora_fim, "
            + "a.acompanhante_nome, a.cid10, a.observacoes, a.status, a.emitido_em, a.cancelado_em, a.motivo_cancelamento ";

    public List<Map<String, Object>> ofAppointment(int appointmentId) {
        return Sql.list("SELECT " + COLUMNS + "FROM atestados a WHERE a.id_consulta = ? ORDER BY a.emitido_em DESC, a.id_atestado DESC",
                appointmentId);
    }

    public List<Map<String, Object>> ofPatient(int patientId) {
        return Sql.list("SELECT " + COLUMNS + ", pr.nome AS medico FROM atestados a JOIN consultas c ON c.id_consulta = a.id_consulta "
                + "JOIN profissionais pr ON pr.id_profissional = a.id_medico WHERE c.id_paciente = ? ORDER BY a.emitido_em DESC", patientId);
    }

    private static final String PRINT = "SELECT " + COLUMNS + ", pr.nome AS medico, m.crm, m.crm_uf, pa.id_paciente, pa.nome AS paciente, "
            + "pa.cpf, pa.data_nascimento FROM atestados a JOIN consultas c ON c.id_consulta = a.id_consulta "
            + "JOIN pacientes pa ON pa.id_paciente = c.id_paciente JOIN profissionais pr ON pr.id_profissional = a.id_medico "
            + "JOIN medicos m ON m.id_profissional = a.id_medico ";

    public Map<String, Object> forPrint(int certificateId, int doctorId) {
        return Sql.one(PRINT + "WHERE a.id_atestado = ? AND a.id_medico = ?", certificateId, doctorId);
    }

    public Map<String, Object> forPatientPrint(int certificateId, int patientId) {
        return Sql.one(PRINT + "WHERE a.id_atestado = ? AND c.id_paciente = ?", certificateId, patientId);
    }

    private static LocalDate date(Object v) {
        try {
            String s = v == null ? "" : String.valueOf(v).trim();
            return s.isEmpty() ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalTime time(Object v) {
        try {
            String s = v == null ? "" : String.valueOf(v).trim();
            return s.isEmpty() ? null : LocalTime.parse(s.length() == 5 ? s : s.substring(0, Math.min(8, s.length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
