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
 * ZTF_J2EE - Model PedidoMedico
 *
 * Pedido medico anexado pelo paciente no portal (PDF, JPG ou PNG, ate 5 MB). Seguranca:
 * - o tipo e decidido pelos primeiros bytes do arquivo, nao pela extensao nem pelo navegador;
 * - o arquivo vai para paths.storage/pedidos com nome aleatorio, fora da pasta publica;
 * - so o proprio paciente e quem tem pedidos.conferir leem o arquivo (entrega com
 *   Content-Disposition e CSP sandbox no controller).
 * Recusar um pedido cancela os exames do portal que dependiam dele.
 */
public class PedidoMedico {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    public record Arquivo(File arquivo, String mime, String nome) {
    }

    /** Recebe o arquivo em data URI/base64. Devolve o id ou erros por campo. */
    public Portal.Resultado enviar(int idPaciente, Map<String, Object> dados, int idUsuario) {
        Map<String, String> erros = new LinkedHashMap<>();
        String nome = texto(dados.get("nome_arquivo"));
        String obs = texto(dados.get("observacao"));
        int idTipo = Sanitize.intVal(dados.get("id_tipo_exame"), 0);
        byte[] bytes = decodificar(dados.get("conteudo"));
        String mime = bytes == null ? null : mimePorAssinatura(bytes);
        if (bytes == null || bytes.length == 0) {
            erros.put("conteudo", "Escolha o arquivo do pedido.");
        } else if (bytes.length > MAX_BYTES) {
            erros.put("conteudo", "Arquivo maior que 5 MB.");
        } else if (mime == null) {
            erros.put("conteudo", "Envie PDF, JPG ou PNG.");
        }
        if (obs.length() > 255) {
            erros.put("observacao", "Até 255 caracteres.");
        }
        if (idTipo > 0 && Sql.um("SELECT 1 FROM tipos_exame WHERE id_tipo_exame = ? AND ativo = 1", idTipo) == null) {
            erros.put("id_tipo_exame", "Exame inexistente.");
        }
        if (!erros.isEmpty()) {
            return new Portal.Resultado(false, 0, "Revise os campos destacados.", erros);
        }
        nome = nome.isEmpty() ? "pedido" : nome.replaceAll("[^\\p{L}\\p{N} ._()-]", "_");
        if (nome.length() > 150) {
            nome = nome.substring(0, 150);
        }
        String interno = CodeGenerator.generateSecureToken(24) + ".bin";
        File destino = new File(Config.getStorageDir("pedidos"), interno);
        try {
            Files.write(destino.toPath(), bytes);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gravar o pedido", e);
        }
        long id;
        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            id = Sql.inserir(conn, "INSERT INTO pedidos_medicos (id_paciente, id_tipo_exame, arquivo, nome_original, mime, tamanho_bytes, "
                    + "observacao, enviado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", idPaciente, idTipo > 0 ? idTipo : null, interno, nome, mime,
                    bytes.length, obs.isEmpty() ? null : obs, idUsuario);
        } catch (java.sql.SQLException e) {
            destino.delete();
            throw new RuntimeException("Falha ao registrar o pedido", e);
        }
        Auditoria.registrar(idUsuario, "pedido.enviado", "pedidos_medicos", id, idPaciente, "bytes=" + bytes.length, null);
        return new Portal.Resultado(true, id, null, Map.of());
    }

    public List<Map<String, Object>> doPaciente(int idPaciente) {
        return Sql.lista("SELECT p.id_pedido, p.nome_original, p.mime, p.tamanho_bytes, p.observacao, p.status, p.motivo_recusa, p.enviado_em, "
                + "p.conferido_em, t.nome AS exame FROM pedidos_medicos p LEFT JOIN tipos_exame t ON t.id_tipo_exame = p.id_tipo_exame "
                + "WHERE p.id_paciente = ? ORDER BY p.enviado_em DESC", idPaciente);
    }

    /** Fila da recepcao (pendentes primeiro). */
    public List<Map<String, Object>> paraConferencia(String status) {
        String filtro = List.of("pendente", "aprovado", "recusado").contains(status) ? status : "pendente";
        List<Map<String, Object>> lista = Sql.lista("SELECT p.id_pedido, p.nome_original, p.mime, p.tamanho_bytes, p.observacao, p.status, p.motivo_recusa, "
                + "p.enviado_em, p.conferido_em, p.id_paciente, pa.nome AS paciente, pa.data_nascimento, t.nome AS exame, u.nome AS conferido_por "
                + "FROM pedidos_medicos p JOIN pacientes pa ON pa.id_paciente = p.id_paciente "
                + "LEFT JOIN tipos_exame t ON t.id_tipo_exame = p.id_tipo_exame LEFT JOIN usuarios u ON u.id_usuario = p.conferido_por "
                + "WHERE p.status = ? ORDER BY p.enviado_em " + ("pendente".equals(filtro) ? "ASC" : "DESC") + " LIMIT 200", filtro);
        for (Map<String, Object> p : lista) {
            p.put("agendamentos", Sql.lista("SELECT x.id_exame_agendado, x.data_hora, x.status, t.nome AS exame FROM exames_agendados x "
                    + "JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame WHERE x.id_pedido = ? ORDER BY x.data_hora", p.get("id_pedido")));
        }
        return lista;
    }

    /** Aprova ou recusa (recusa exige motivo e cancela os exames agendados com o pedido). */
    public Portal.Resultado conferir(int idPedido, boolean aprovar, String motivo, int idUsuario) {
        Map<String, Object> p = Sql.um("SELECT id_paciente, status FROM pedidos_medicos WHERE id_pedido = ?", idPedido);
        if (p == null) {
            return Portal.Resultado.falha("Pedido não encontrado.");
        }
        if (!"pendente".equals(p.get("status"))) {
            return Portal.Resultado.falha("Este pedido já foi conferido.");
        }
        String m = motivo == null ? "" : Sanitize.string(motivo).trim();
        if (!aprovar && m.length() < 3) {
            return Portal.Resultado.falha("Informe o motivo da recusa.");
        }
        int idPaciente = ((Number) p.get("id_paciente")).intValue();
        if (aprovar) {
            Sql.executar("UPDATE pedidos_medicos SET status = 'aprovado', conferido_por = ?, conferido_em = NOW() WHERE id_pedido = ? AND status = 'pendente'",
                    idUsuario, idPedido);
        } else {
            String mot = m.length() > 255 ? m.substring(0, 255) : m;
            Sql.executar("UPDATE pedidos_medicos SET status = 'recusado', motivo_recusa = ?, conferido_por = ?, conferido_em = NOW() "
                    + "WHERE id_pedido = ? AND status = 'pendente'", mot, idUsuario, idPedido);
            String cancel = ("Pedido médico recusado: " + mot);
            Sql.executar("UPDATE exames_agendados SET status = 'cancelado', cancelado_em = NOW(), motivo_cancelamento = ? "
                    + "WHERE id_pedido = ? AND status = 'agendado'", cancel.length() > 255 ? cancel.substring(0, 255) : cancel, idPedido);
        }
        Auditoria.registrar(idUsuario, aprovar ? "pedido.aprovado" : "pedido.recusado", "pedidos_medicos", idPedido, idPaciente, null, null);
        return Portal.Resultado.ok(idPedido);
    }

    /** Arquivo do pedido; com idPacienteDono, so se for dele. */
    public Arquivo arquivo(int idPedido, Integer idPacienteDono) {
        Map<String, Object> p = Sql.um("SELECT id_paciente, arquivo, mime, nome_original FROM pedidos_medicos WHERE id_pedido = ?", idPedido);
        if (p == null || (idPacienteDono != null && ((Number) p.get("id_paciente")).intValue() != idPacienteDono)) {
            return null;
        }
        String interno = String.valueOf(p.get("arquivo"));
        if (!interno.matches("^[A-Za-z0-9_-]+\\.bin$")) {
            return null;
        }
        File f = new File(Config.getStorageDir("pedidos"), interno);
        return f.isFile() ? new Arquivo(f, String.valueOf(p.get("mime")), String.valueOf(p.get("nome_original"))) : null;
    }

    public static int pacienteDoPedido(int idPedido) {
        Map<String, Object> p = Sql.um("SELECT id_paciente FROM pedidos_medicos WHERE id_pedido = ?", idPedido);
        return p == null ? 0 : ((Number) p.get("id_paciente")).intValue();
    }

    // ------------------------------------------------------------------ auxiliares

    static byte[] decodificar(Object conteudo) {
        if (conteudo == null) {
            return null;
        }
        String s = String.valueOf(conteudo).trim();
        int virgula = s.indexOf(',');
        if (s.startsWith("data:") && virgula > 0) {
            s = s.substring(virgula + 1);
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

    /** PDF, PNG ou JPEG pelos primeiros bytes (magic numbers); qualquer outro tipo e recusado. */
    static String mimePorAssinatura(byte[] b) {
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

    private static String texto(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
