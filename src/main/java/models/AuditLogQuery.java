package models;

import database.Sql;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ZTF_J2EE - Model ConsultaAuditoria
 *
 * Leitura da trilha de auditoria e do registro de notificacoes (RF-AUD-02), so para quem tem
 * auditoria.view. Somente leitura: a tabela auditoria nao aceita UPDATE/DELETE (triggers).
 * Os filtros viram parametros do PreparedStatement; nenhum nome de coluna vem da requisicao.
 */
public class AuditLogQuery {

    public static final int PER_PAGE = 50;

    /** Filtros da tela; null/vazio = sem filtro. "modulo" e o prefixo da acao (ex.: prontuario). */
    public record Filter(LocalDate from, LocalDate to, Integer userId, String module, Integer patientId, int page) {
    }

    public record Page(List<Map<String, Object>> items, long total, int page) {
        public int totalPages() {
            return (int) Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        }
    }

    public Page search(Filter f) {
        StringBuilder where = new StringBuilder(" WHERE a.registrado_em >= ? AND a.registrado_em < ?");
        List<Object> params = new ArrayList<>();
        params.add(f.from().atStartOfDay());
        params.add(f.to().plusDays(1).atStartOfDay());
        if (f.userId() != null) {
            where.append(" AND a.id_usuario = ?");
            params.add(f.userId());
        }
        if (f.module() != null && !f.module().isEmpty()) {
            where.append(" AND (a.acao = ? OR a.acao LIKE ?)");
            params.add(f.module());
            params.add(f.module().replace("%", "").replace("_", "\\_") + ".%");
        }
        if (f.patientId() != null) {
            where.append(" AND a.id_paciente = ?");
            params.add(f.patientId());
        }
        long total = Sql.number("SELECT COUNT(*) FROM auditoria a" + where, params.toArray());
        int page = Math.max(1, f.page());
        List<Object> p2 = new ArrayList<>(params);
        p2.add(PER_PAGE);
        p2.add((page - 1) * PER_PAGE);
        List<Map<String, Object>> items = Sql.list("SELECT a.id_auditoria, a.registrado_em, a.acao, a.entidade, a.id_entidade, "
                + "a.id_paciente, a.detalhes, a.ip, u.nome AS usuario, u.email AS usuario_email, pa.nome AS paciente "
                + "FROM auditoria a LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario "
                + "LEFT JOIN pacientes pa ON pa.id_paciente = a.id_paciente" + where
                + " ORDER BY a.registrado_em DESC, a.id_auditoria DESC LIMIT ? OFFSET ?", p2.toArray());
        return new Page(items, total, page);
    }

    /** Prefixos de acao existentes (login, paciente, prontuario...), para o filtro. */
    public List<String> modules() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : Sql.list("SELECT DISTINCT SUBSTRING_INDEX(acao, '.', 1) AS modulo FROM auditoria ORDER BY modulo")) {
            out.add(String.valueOf(r.get("modulo")));
        }
        return out;
    }

    /** Usuarios que ja aparecem na auditoria, para o filtro. */
    public List<Map<String, Object>> users() {
        return Sql.list("SELECT DISTINCT u.id_usuario, u.nome FROM auditoria a JOIN usuarios u ON u.id_usuario = a.id_usuario ORDER BY u.nome");
    }

    /** Ultimas notificacoes (sem corpo: so tipo, destinatario, assunto e situacao do envio). */
    public List<Map<String, Object>> notifications(int limit) {
        return Sql.list("SELECT n.id_notificacao, n.tipo, n.destinatario, n.assunto, n.status, n.erro, n.enviado_em, n.criado_em, "
                + "u.nome AS usuario FROM notificacoes n LEFT JOIN usuarios u ON u.id_usuario = n.id_usuario "
                + "ORDER BY n.id_notificacao DESC LIMIT ?", limit);
    }

    /** "ana.ficticia@exemplo.test" -> "an***@exemplo.test" (a tela nao precisa do e-mail inteiro). */
    public static String maskedEmail(Object email) {
        String e = email == null ? "" : String.valueOf(email);
        int at = e.indexOf('@');
        if (at <= 0) {
            return e.isEmpty() ? "" : "***";
        }
        return e.substring(0, Math.min(2, at)) + "***" + e.substring(at);
    }
}
