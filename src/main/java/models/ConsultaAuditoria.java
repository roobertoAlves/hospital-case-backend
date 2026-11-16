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
public class ConsultaAuditoria {

    public static final int POR_PAGINA = 50;

    /** Filtros da tela; null/vazio = sem filtro. "modulo" e o prefixo da acao (ex.: prontuario). */
    public record Filtro(LocalDate de, LocalDate ate, Integer idUsuario, String modulo, Integer idPaciente, int pagina) {
    }

    public record Pagina(List<Map<String, Object>> itens, long total, int pagina) {
        public int totalPaginas() {
            return (int) Math.max(1, (total + POR_PAGINA - 1) / POR_PAGINA);
        }
    }

    public Pagina buscar(Filtro f) {
        StringBuilder where = new StringBuilder(" WHERE a.registrado_em >= ? AND a.registrado_em < ?");
        List<Object> params = new ArrayList<>();
        params.add(f.de().atStartOfDay());
        params.add(f.ate().plusDays(1).atStartOfDay());
        if (f.idUsuario() != null) {
            where.append(" AND a.id_usuario = ?");
            params.add(f.idUsuario());
        }
        if (f.modulo() != null && !f.modulo().isEmpty()) {
            where.append(" AND (a.acao = ? OR a.acao LIKE ?)");
            params.add(f.modulo());
            params.add(f.modulo().replace("%", "").replace("_", "\\_") + ".%");
        }
        if (f.idPaciente() != null) {
            where.append(" AND a.id_paciente = ?");
            params.add(f.idPaciente());
        }
        long total = Sql.numero("SELECT COUNT(*) FROM auditoria a" + where, params.toArray());
        int pagina = Math.max(1, f.pagina());
        List<Object> p2 = new ArrayList<>(params);
        p2.add(POR_PAGINA);
        p2.add((pagina - 1) * POR_PAGINA);
        List<Map<String, Object>> itens = Sql.lista("SELECT a.id_auditoria, a.registrado_em, a.acao, a.entidade, a.id_entidade, "
                + "a.id_paciente, a.detalhes, a.ip, u.nome AS usuario, u.email AS usuario_email, pa.nome AS paciente "
                + "FROM auditoria a LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario "
                + "LEFT JOIN pacientes pa ON pa.id_paciente = a.id_paciente" + where
                + " ORDER BY a.registrado_em DESC, a.id_auditoria DESC LIMIT ? OFFSET ?", p2.toArray());
        return new Pagina(itens, total, pagina);
    }

    /** Prefixos de acao existentes (login, paciente, prontuario...), para o filtro. */
    public List<String> modulos() {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> r : Sql.lista("SELECT DISTINCT SUBSTRING_INDEX(acao, '.', 1) AS modulo FROM auditoria ORDER BY modulo")) {
            out.add(String.valueOf(r.get("modulo")));
        }
        return out;
    }

    /** Usuarios que ja aparecem na auditoria, para o filtro. */
    public List<Map<String, Object>> usuarios() {
        return Sql.lista("SELECT DISTINCT u.id_usuario, u.nome FROM auditoria a JOIN usuarios u ON u.id_usuario = a.id_usuario ORDER BY u.nome");
    }

    /** Ultimas notificacoes (sem corpo: so tipo, destinatario, assunto e situacao do envio). */
    public List<Map<String, Object>> notificacoes(int limite) {
        return Sql.lista("SELECT n.id_notificacao, n.tipo, n.destinatario, n.assunto, n.status, n.erro, n.enviado_em, n.criado_em, "
                + "u.nome AS usuario FROM notificacoes n LEFT JOIN usuarios u ON u.id_usuario = n.id_usuario "
                + "ORDER BY n.id_notificacao DESC LIMIT ?", limite);
    }

    /** "ana.ficticia@exemplo.test" -> "an***@exemplo.test" (a tela nao precisa do e-mail inteiro). */
    public static String emailMascarado(Object email) {
        String e = email == null ? "" : String.valueOf(email);
        int at = e.indexOf('@');
        if (at <= 0) {
            return e.isEmpty() ? "" : "***";
        }
        return e.substring(0, Math.min(2, at)) + "***" + e.substring(at);
    }
}
