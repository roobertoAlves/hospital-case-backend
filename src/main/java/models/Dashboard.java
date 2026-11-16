package models;

import database.Sql;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ZTF_J2EE - Model Painel
 *
 * Numeros e listas da tela inicial de cada perfil (RF-DSH-01..04). Todos os totais sao
 * contados no banco a partir das linhas, no fuso da aplicacao; nada e digitado a mao.
 * Recepcao e administrador nao recebem dado clinico; a fila do medico mostra so nome,
 * horario e a cor de prioridade definida na triagem.
 */
public class Dashboard {

    private static Object[] day(LocalDate d) {
        return new Object[]{d.atStartOfDay(), d.plusDays(1).atStartOfDay()};
    }

    private static long count(String where, LocalDate d) {
        Object[] p = day(d);
        return Sql.number("SELECT COUNT(*) FROM consultas WHERE data_hora >= ? AND data_hora < ? AND " + where, p[0], p[1]);
    }

    // ------------------------------------------------------------------ recepcao

    public long dayAppointments(LocalDate d) { return count("status <> 'cancelada'", d); }
    public long arrived(LocalDate d) { return count("status NOT IN ('agendada', 'cancelada', 'ausente')", d); }
    public long awaitingTriage(LocalDate d) { return count("status = 'aguardando_triagem'", d); }
    public long dayCancelled(LocalDate d) { return count("status = 'cancelada'", d); }

    public long dayExams(LocalDate d) {
        Object[] p = day(d);
        return Sql.number("SELECT COUNT(*) FROM exames_agendados WHERE data_hora >= ? AND data_hora < ? AND status <> 'cancelado'", p[0], p[1]);
    }

    /** Proximas consultas ainda nao chegadas (a recepcao registra a chegada daqui). */
    public List<Map<String, Object>> nextArrivals(LocalDate d, int limit) {
        Object[] p = day(d);
        return Sql.list("SELECT c.id_consulta, c.data_hora, pa.nome AS paciente, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.data_hora >= ? AND c.data_hora < ? AND c.status = 'agendada' ORDER BY c.data_hora LIMIT ?", p[0], p[1], limit);
    }

    // ------------------------------------------------------------------ enfermagem

    /** Fila de triagem por ordem de chegada (RF-TRI-01). */
    public List<Map<String, Object>> triageQueue(int limit) {
        return Sql.list("SELECT c.id_consulta, c.chegada_em, c.data_hora, pa.nome AS paciente, pr.nome AS medico, c.status "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "WHERE c.status IN ('aguardando_triagem', 'em_triagem') ORDER BY c.chegada_em LIMIT ?", limit);
    }

    /** Triagens validas (nao retificadas) do dia, por classificacao. */
    public List<Map<String, Object>> triagedByPriority(LocalDate d) {
        Object[] p = day(d);
        return Sql.list("SELECT cp.codigo, cp.nome, cp.cor_hex, cp.ordem, "
                + "(SELECT COUNT(*) FROM triagens t WHERE t.id_classificacao = cp.id_classificacao "
                + " AND t.registrado_em >= ? AND t.registrado_em < ? "
                + " AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem)) AS total "
                + "FROM classificacoes_prioridade cp WHERE cp.ativo = 1 ORDER BY cp.ordem", p[0], p[1]);
    }

    // ------------------------------------------------------------------ medico

    /** Fila do medico: prioridade (vermelho primeiro) e, dentro dela, chegada (RN-TRI-05). */
    public List<Map<String, Object>> doctorQueue(int doctorId, int limit) {
        return Sql.list("SELECT c.id_consulta, c.chegada_em, pa.nome AS paciente, cp.nome AS prioridade, cp.cor_hex, cp.ordem "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "LEFT JOIN triagens t ON t.id_consulta = c.id_consulta "
                + " AND NOT EXISTS (SELECT 1 FROM triagens r WHERE r.retifica_id = t.id_triagem) "
                + "LEFT JOIN classificacoes_prioridade cp ON cp.id_classificacao = t.id_classificacao "
                + "WHERE c.id_medico = ? AND c.status = 'aguardando_atendimento' "
                + "ORDER BY COALESCE(cp.ordem, 99), c.chegada_em LIMIT ?", doctorId, limit);
    }

    public long completedByDoctor(int doctorId, LocalDate d) {
        Object[] p = day(d);
        return Sql.number("SELECT COUNT(*) FROM consultas WHERE id_medico = ? AND status = 'realizada' "
                + "AND data_hora >= ? AND data_hora < ?", doctorId, p[0], p[1]);
    }

    // ------------------------------------------------------------------ administrador

    public long activePatients() { return Sql.number("SELECT COUNT(*) FROM pacientes WHERE ativo = 1"); }
    public long activeProfessionals() { return Sql.number("SELECT COUNT(*) FROM profissionais WHERE ativo = 1"); }
    public long activeUsers() { return Sql.number("SELECT COUNT(*) FROM usuarios WHERE status = 1"); }
    public long blockedUsers() { return Sql.number("SELECT COUNT(*) FROM usuarios_seguranca WHERE bloqueado_ate > NOW()"); }

    public long completedInMonth(LocalDate d) {
        LocalDate ini = d.withDayOfMonth(1);
        return Sql.number("SELECT COUNT(*) FROM consultas WHERE status = 'realizada' AND data_hora >= ? AND data_hora < ?",
                ini.atStartOfDay(), ini.plusMonths(1).atStartOfDay());
    }

    public long scheduledAhead() {
        return Sql.number("SELECT COUNT(*) FROM consultas WHERE status = 'agendada' AND data_hora >= NOW()");
    }
}
