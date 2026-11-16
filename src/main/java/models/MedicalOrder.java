package models;

import database.Sql;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.CodeGenerator;
import utils.Config;
import utils.Sanitize;

/**
 * ZTF_J2EE - MedicalOrder model
 *
 * Medical order attached by the patient in the portal (PDF, JPG or PNG, up to 5 MB). Security:
 * - the type is decided by the file's first bytes, not by the extension or the browser;
 * - the file goes to paths.storage/pedidos with a random name, outside any public folder;
 * - only the patient and holders of pedidos.conferir read the file (served with
 *   Content-Disposition and a sandbox CSP by the controller).
 * Rejecting an order cancels the portal exams that depended on it.
 */
public class MedicalOrder {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public record StoredFile(File file, String mime, String name) {
    }

    /** Receives the file as a data URI/base64. Returns the id or per-field errors. */
    public PatientPortal.Result send(int patientId, Map<String, Object> data, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = text(data.get("file_name"));
        String note = text(data.get("observacao"));
        int typeId = Sanitize.intVal(data.get("id_tipo_exame"), 0);
        byte[] bytes = decodeDataUri(data.get("content"));
        String mime = bytes == null ? null : mimeBySignature(bytes);
        if (bytes == null || bytes.length == 0) {
            errors.put("content", "Escolha o arquivo do pedido.");
        } else if (bytes.length > MAX_BYTES) {
            errors.put("content", "Arquivo maior que 5 MB.");
        } else if (mime == null) {
            errors.put("content", "Envie PDF, JPG ou PNG.");
        }
        if (note.length() > 255) {
            errors.put("observacao", "Até 255 caracteres.");
        }
        if (typeId > 0 && Sql.one("SELECT 1 FROM tipos_exame WHERE id_tipo_exame = ? AND ativo = 1", typeId) == null) {
            errors.put("id_tipo_exame", "Exame inexistente.");
        }
        if (!errors.isEmpty()) {
            return new PatientPortal.Result(false, 0, "Revise os campos destacados.", errors);
        }
        name = name.isEmpty() ? "pedido" : name.replaceAll("[^\\p{L}\\p{N} ._()-]", "_");
        if (name.length() > 150) {
            name = name.substring(0, 150);
        }
        String internal = CodeGenerator.generateSecureToken(24) + ".bin";
        File target = new File(Config.getStorageDir("pedidos"), internal);
        try {
            Files.write(target.toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException("Could not store the medical order file", e);
        }
        long id;
        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            id = Sql.insert(conn, "INSERT INTO pedidos_medicos (id_paciente, id_tipo_exame, arquivo, nome_original, mime, tamanho_bytes, "
                    + "observacao, enviado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", patientId, typeId > 0 ? typeId : null, internal, name, mime,
                    bytes.length, note.isEmpty() ? null : note, userId);
        } catch (java.sql.SQLException e) {
            target.delete();
            throw new RuntimeException("Could not record the medical order", e);
        }
        AuditLog.record(userId, "pedido.enviado", "pedidos_medicos", id, patientId, "bytes=" + bytes.length, null);
        return new PatientPortal.Result(true, id, null, Map.of());
    }

    public List<Map<String, Object>> ofPatient(int patientId) {
        return Sql.list("SELECT p.id_pedido, p.nome_original, p.mime, p.tamanho_bytes, p.observacao, p.status, p.motivo_recusa, p.enviado_em, "
                + "p.conferido_em, t.nome AS exame FROM pedidos_medicos p LEFT JOIN tipos_exame t ON t.id_tipo_exame = p.id_tipo_exame "
                + "WHERE p.id_paciente = ? ORDER BY p.enviado_em DESC", patientId);
    }

    /** Reception queue (pending first). */
    public List<Map<String, Object>> forReview(String status) {
        String filter = List.of("pendente", "aprovado", "recusado").contains(status) ? status : "pendente";
        List<Map<String, Object>> list = Sql.list("SELECT p.id_pedido, p.nome_original, p.mime, p.tamanho_bytes, p.observacao, p.status, p.motivo_recusa, "
                + "p.enviado_em, p.conferido_em, p.id_paciente, pa.nome AS paciente, pa.data_nascimento, t.nome AS exame, u.nome AS conferido_por "
                + "FROM pedidos_medicos p JOIN pacientes pa ON pa.id_paciente = p.id_paciente "
                + "LEFT JOIN tipos_exame t ON t.id_tipo_exame = p.id_tipo_exame LEFT JOIN usuarios u ON u.id_usuario = p.conferido_por "
                + "WHERE p.status = ? ORDER BY p.enviado_em " + ("pendente".equals(filter) ? "ASC" : "DESC") + " LIMIT 200", filter);
        for (Map<String, Object> p : list) {
            p.put("bookings", Sql.list("SELECT x.id_exame_agendado, x.data_hora, x.status, t.nome AS exame FROM exames_agendados x "
                    + "JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame WHERE x.id_pedido = ? ORDER BY x.data_hora", p.get("id_pedido")));
        }
        return list;
    }

    /** Approves or rejects (rejecting requires a reason and cancels the exams booked with the order). */
    public PatientPortal.Result review(int orderId, boolean approve, String reason, int userId) {
        Map<String, Object> p = Sql.one("SELECT id_paciente, status FROM pedidos_medicos WHERE id_pedido = ?", orderId);
        if (p == null) {
            return PatientPortal.Result.failure("Pedido não encontrado.");
        }
        if (!"pendente".equals(p.get("status"))) {
            return PatientPortal.Result.failure("Este pedido já foi conferido.");
        }
        String m = reason == null ? "" : Sanitize.string(reason).trim();
        if (!approve && m.length() < 3) {
            return PatientPortal.Result.failure("Informe o motivo da recusa.");
        }
        int patientId = ((Number) p.get("id_paciente")).intValue();
        if (approve) {
            Sql.execute("UPDATE pedidos_medicos SET status = 'aprovado', conferido_por = ?, conferido_em = NOW() WHERE id_pedido = ? AND status = 'pendente'",
                    userId, orderId);
        } else {
            String shortReason = m.length() > 255 ? m.substring(0, 255) : m;
            Sql.execute("UPDATE pedidos_medicos SET status = 'recusado', motivo_recusa = ?, conferido_por = ?, conferido_em = NOW() "
                    + "WHERE id_pedido = ? AND status = 'pendente'", shortReason, userId, orderId);
            String cancel = ("Pedido médico recusado: " + shortReason);
            Sql.execute("UPDATE exames_agendados SET status = 'cancelado', cancelado_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_pedido = ? AND status = 'agendado'", cancel.length() > 255 ? cancel.substring(0, 255) : cancel, orderId);
        }
        AuditLog.record(userId, approve ? "pedido.aprovado" : "pedido.recusado", "pedidos_medicos", orderId, patientId, null, null);
        return PatientPortal.Result.ok(orderId);
    }

    /** The order's file; with ownerPatientId, only if it belongs to that patient. */
    public StoredFile file(int orderId, Integer ownerPatientId) {
        Map<String, Object> p = Sql.one("SELECT id_paciente, arquivo, mime, nome_original FROM pedidos_medicos WHERE id_pedido = ?", orderId);
        if (p == null || (ownerPatientId != null && ((Number) p.get("id_paciente")).intValue() != ownerPatientId)) {
            return null;
        }
        String internal = String.valueOf(p.get("arquivo"));
        if (!internal.matches("^[A-Za-z0-9_-]+\\.bin$")) {
            return null;
        }
        File f = new File(Config.getStorageDir("pedidos"), internal);
        return f.isFile() ? new StoredFile(f, String.valueOf(p.get("mime")), String.valueOf(p.get("nome_original"))) : null;
    }

    public static int orderPatient(int orderId) {
        Map<String, Object> p = Sql.one("SELECT id_paciente FROM pedidos_medicos WHERE id_pedido = ?", orderId);
        return p == null ? 0 : ((Number) p.get("id_paciente")).intValue();
    }

    // ------------------------------------------------------------------ auxiliares

    static byte[] decodeDataUri(Object content) {
        if (content == null) {
            return null;
        }
        String s = String.valueOf(content).trim();
        int comma = s.indexOf(',');
        if (s.startsWith("data:") && comma > 0) {
            s = s.substring(comma + 1);
        }
        if (s.length() > (MAX_BYTES / 3 + 2) * 4 + 16) {
            return new byte[MAX_BYTES + 1];
        }
        try {
            return Base64.getDecoder().decode(s.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** PDF, PNG or JPEG by the first bytes (magic numbers); any other type is rejected. */
    static String mimeBySignature(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private static String text(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
