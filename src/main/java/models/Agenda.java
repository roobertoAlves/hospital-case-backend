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
public class Agenda {

    public static final ZoneOffset FUSO = ZoneOffset.ofHours(-3);

    public static LocalDateTime agora() {
        return LocalDateTime.now(FUSO).withSecond(0).withNano(0);
    }

    /** Resultado de uma operacao: ok, ou a mensagem para o usuario. */
    public record Resultado(boolean ok, long id, String erro) {
        static Resultado falha(String erro) {
            return new Resultado(false, 0, erro);
        }
    }

    public record Horario(LocalTime hora, boolean livre) {
    }

    // ------------------------------------------------------------------ consultas de apoio

    public List<Map<String, Object>> especialidadesAtivas() {
        return Sql.lista("SELECT e.id_especialidade, e.nome FROM especialidades e WHERE e.ativo = 1 "
                + "AND EXISTS (SELECT 1 FROM medico_especialidades me JOIN profissionais p ON p.id_profissional = me.id_profissional "
                + "WHERE me.id_especialidade = e.id_especialidade AND p.ativo = 1) ORDER BY e.nome");
    }

    public List<Map<String, Object>> medicosDaEspecialidade(int idEspecialidade) {
        return Sql.lista("SELECT p.id_profissional, p.nome, m.crm, m.crm_uf, m.duracao_consulta_min "
                + "FROM profissionais p JOIN medicos m ON m.id_profissional = p.id_profissional "
                + "JOIN medico_especialidades me ON me.id_profissional = p.id_profissional "
                + "WHERE me.id_especialidade = ? AND p.ativo = 1 ORDER BY p.nome", idEspecialidade);
    }

    /** Medicos que atendem no dia (para a linha do dia), com o horario de cada um. */
    public List<Map<String, Object>> medicosNoDia(LocalDate dia) {
        return Sql.lista("SELECT p.id_profissional, p.nome, MIN(d.hora_inicio) AS inicio, MAX(d.hora_fim) AS fim, "
                + "(SELECT GROUP_CONCAT(e.nome ORDER BY e.nome SEPARATOR ', ') FROM medico_especialidades me "
                + " JOIN especialidades e ON e.id_especialidade = me.id_especialidade WHERE me.id_profissional = p.id_profissional) AS especialidades "
                + "FROM profissionais p JOIN disponibilidades_medico d ON d.id_profissional = p.id_profissional "
                + "WHERE p.ativo = 1 AND d.ativo = 1 AND d.dia_semana = ? GROUP BY p.id_profissional, p.nome ORDER BY MIN(d.hora_inicio), p.nome",
                dia.getDayOfWeek().getValue());
    }

    /** Consultas do dia (opcionalmente de um medico), com paciente, medico e especialidade. */
    public List<Map<String, Object>> doDia(LocalDate dia, Integer idMedico) {
        String sql = "SELECT c.id_consulta, c.data_hora, c.duracao_min, c.status, c.tipo, c.chegada_em, "
                + "c.id_paciente, pa.nome AS paciente, c.id_medico, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                + "WHERE c.data_hora >= ? AND c.data_hora < ?" + (idMedico != null ? " AND c.id_medico = ?" : "")
                + " ORDER BY c.data_hora, pr.nome";
        return idMedico != null
                ? Sql.lista(sql, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay(), idMedico)
                : Sql.lista(sql, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
    }

    /**
     * Horarios do medico no dia, na grade da duracao da consulta, marcando os ocupados e os ja
     * passados (hoje). Vazio se o medico nao atende nesse dia.
     */
    public List<Horario> horarios(int idMedico, LocalDate dia) {
        Map<String, Object> medico = Sql.um("SELECT m.duracao_consulta_min FROM medicos m JOIN profissionais p "
                + "ON p.id_profissional = m.id_profissional WHERE m.id_profissional = ? AND p.ativo = 1", idMedico);
        if (medico == null) {
            return List.of();
        }
        int duracao = ((Number) medico.get("duracao_consulta_min")).intValue();
        List<Map<String, Object>> faixas = Sql.lista("SELECT hora_inicio, hora_fim FROM disponibilidades_medico "
                + "WHERE id_profissional = ? AND dia_semana = ? AND ativo = 1 ORDER BY hora_inicio", idMedico, dia.getDayOfWeek().getValue());
        List<Map<String, Object>> ocupadas = Sql.lista("SELECT data_hora, duracao_min FROM consultas WHERE id_medico = ? "
                + "AND slot_ativo = 1 AND data_hora >= ? AND data_hora < ?", idMedico, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
        LocalDateTime agora = agora();
        List<Horario> lista = new ArrayList<>();
        for (Map<String, Object> f : faixas) {
            LocalTime t = hora(f.get("hora_inicio"));
            LocalTime fim = hora(f.get("hora_fim"));
            while (!t.plusMinutes(duracao).isAfter(fim)) {
                LocalDateTime inicio = dia.atTime(t);
                boolean livre = inicio.isAfter(agora) && !sobrepoe(ocupadas, inicio, duracao);
                lista.add(new Horario(t, livre));
                t = t.plusMinutes(duracao);
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------ operacoes

    public Resultado agendar(int idPaciente, int idMedico, int idEspecialidade, LocalDateTime dataHora, int idUsuario) {
        if (dataHora == null) {
            return Resultado.falha("Escolha a data e o horário.");
        }
        if (!dataHora.isAfter(agora())) {
            return Resultado.falha("Não é possível agendar no passado.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> medico = Sql.um(conn, "SELECT m.duracao_consulta_min FROM medicos m "
                        + "JOIN profissionais p ON p.id_profissional = m.id_profissional "
                        + "WHERE m.id_profissional = ? AND p.ativo = 1 FOR UPDATE", idMedico);
                if (medico == null) {
                    conn.rollback();
                    return Resultado.falha("Médico inativo ou inexistente.");
                }
                int duracao = ((Number) medico.get("duracao_consulta_min")).intValue();
                if (Sql.um(conn, "SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", idPaciente) == null) {
                    conn.rollback();
                    return Resultado.falha("Paciente inativo ou inexistente.");
                }
                if (Sql.um(conn, "SELECT 1 FROM medico_especialidades me JOIN especialidades e ON e.id_especialidade = me.id_especialidade "
                        + "WHERE me.id_profissional = ? AND me.id_especialidade = ? AND e.ativo = 1", idMedico, idEspecialidade) == null) {
                    conn.rollback();
                    return Resultado.falha("A especialidade escolhida não é atendida por este médico.");
                }
                if (!dentroDaDisponibilidade(conn, idMedico, dataHora, duracao)) {
                    conn.rollback();
                    return Resultado.falha("Horário fora da agenda do médico.");
                }
                LocalDateTime fim = dataHora.plusMinutes(duracao);
                if (Sql.um(conn, "SELECT 1 FROM consultas WHERE id_medico = ? AND slot_ativo = 1 "
                        + "AND data_hora < ? AND DATE_ADD(data_hora, INTERVAL duracao_min MINUTE) > ?", idMedico, fim, dataHora) != null) {
                    conn.rollback();
                    return Resultado.falha("Horário indisponível: o médico já tem consulta nesse horário.");
                }
                if (Sql.um(conn, "SELECT 1 FROM consultas WHERE id_paciente = ? AND slot_ativo = 1 "
                        + "AND data_hora < ? AND DATE_ADD(data_hora, INTERVAL duracao_min MINUTE) > ?", idPaciente, fim, dataHora) != null) {
                    conn.rollback();
                    return Resultado.falha("O paciente já tem consulta nesse horário.");
                }
                long id = Sql.inserir(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, duracao_min, "
                        + "criado_por, atualizado_por) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        idPaciente, idMedico, idEspecialidade, dataHora, duracao, idUsuario, idUsuario);
                Auditoria.registrar(conn, idUsuario, "consulta.agendada", "consultas", id, idPaciente, null, null);
                conn.commit();
                return new Resultado(true, id, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.ehDuplicidade(e)) {
                    return Resultado.falha("Horário indisponível: acabou de ser reservado por outra pessoa.");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao agendar consulta", e);
        }
    }

    /** RN-AGE-09: chegada so no dia da consulta, para consulta agendada. */
    public Resultado registrarChegada(int idConsulta, int idUsuario) {
        Map<String, Object> c = Sql.um("SELECT id_paciente, data_hora, status FROM consultas WHERE id_consulta = ?", idConsulta);
        if (c == null) {
            return Resultado.falha("Consulta não encontrada.");
        }
        if (!"agendada".equals(c.get("status"))) {
            return Resultado.falha("Só é possível registrar a chegada de uma consulta agendada.");
        }
        if (!dataHora(c.get("data_hora")).toLocalDate().equals(agora().toLocalDate())) {
            return Resultado.falha("A chegada só pode ser registrada no dia da consulta.");
        }
        int n = Sql.executar("UPDATE consultas SET status = 'aguardando_triagem', chegada_em = NOW(), atualizado_por = ? "
                + "WHERE id_consulta = ? AND status = 'agendada'", idUsuario, idConsulta);
        if (n == 0) {
            return Resultado.falha("A consulta mudou de situação. Atualize a tela.");
        }
        Auditoria.registrar(idUsuario, "consulta.chegada", "consultas", idConsulta, ((Number) c.get("id_paciente")).intValue(), null, null);
        return new Resultado(true, idConsulta, null);
    }

    /** RN-AGE-06: cancelamento com motivo, so antes do atendimento comecar. */
    public Resultado cancelar(int idConsulta, String motivo, int idUsuario) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < 3) {
            return Resultado.falha("Informe o motivo do cancelamento.");
        }
        if (m.length() > 255) {
            m = m.substring(0, 255);
        }
        Map<String, Object> c = Sql.um("SELECT id_paciente FROM consultas WHERE id_consulta = ?", idConsulta);
        if (c == null) {
            return Resultado.falha("Consulta não encontrada.");
        }
        int n = Sql.executar("UPDATE consultas SET status = 'cancelada', motivo_cancelamento = ?, cancelada_em = NOW(), "
                + "cancelada_por = ?, atualizado_por = ? WHERE id_consulta = ? AND status IN ('agendada', 'aguardando_triagem')",
                m, idUsuario, idUsuario, idConsulta);
        if (n == 0) {
            return Resultado.falha("Só consultas ainda não iniciadas podem ser canceladas.");
        }
        Auditoria.registrar(idUsuario, "consulta.cancelada", "consultas", idConsulta, ((Number) c.get("id_paciente")).intValue(), null, null);
        return new Resultado(true, idConsulta, null);
    }

    /** Dados para a notificacao (sem dado clinico: data, hora, medico e especialidade). */
    public Map<String, Object> resumoParaAviso(long idConsulta) {
        return Sql.um("SELECT c.data_hora, pa.nome AS paciente, pa.email, pr.nome AS medico, e.nome AS especialidade "
                + "FROM consultas c JOIN pacientes pa ON pa.id_paciente = c.id_paciente "
                + "JOIN profissionais pr ON pr.id_profissional = c.id_medico "
                + "JOIN especialidades e ON e.id_especialidade = c.id_especialidade WHERE c.id_consulta = ?", idConsulta);
    }

    // ------------------------------------------------------------------ auxiliares

    private static boolean dentroDaDisponibilidade(Connection conn, int idMedico, LocalDateTime inicio, int duracao) throws SQLException {
        List<Map<String, Object>> faixas = Sql.lista(conn, "SELECT hora_inicio, hora_fim FROM disponibilidades_medico "
                + "WHERE id_profissional = ? AND dia_semana = ? AND ativo = 1", idMedico, inicio.getDayOfWeek().getValue());
        LocalTime t = inicio.toLocalTime();
        for (Map<String, Object> f : faixas) {
            LocalTime ini = hora(f.get("hora_inicio"));
            LocalTime fim = hora(f.get("hora_fim"));
            boolean alinhado = java.time.Duration.between(ini, t).toMinutes() % duracao == 0;
            if (!t.isBefore(ini) && !t.plusMinutes(duracao).isAfter(fim) && alinhado) {
                return true;
            }
        }
        return false;
    }

    static boolean sobrepoe(List<Map<String, Object>> ocupadas, LocalDateTime inicio, int duracao) {
        LocalDateTime fim = inicio.plusMinutes(duracao);
        for (Map<String, Object> o : ocupadas) {
            LocalDateTime oi = dataHora(o.get("data_hora"));
            LocalDateTime of = oi.plusMinutes(((Number) o.get("duracao_min")).intValue());
            if (oi.isBefore(fim) && of.isAfter(inicio)) {
                return true;
            }
        }
        return false;
    }

    static LocalTime hora(Object v) {
        if (v instanceof LocalTime) {
            return (LocalTime) v;
        }
        if (v instanceof Time) {
            return ((Time) v).toLocalTime();
        }
        return LocalTime.parse(String.valueOf(v));
    }

    static LocalDateTime dataHora(Object v) {
        if (v instanceof LocalDateTime) {
            return (LocalDateTime) v;
        }
        if (v instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) v).toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));
    }

    /** Converte uma linha para exibicao: hora HH:mm e data dd/MM. */
    public static Map<String, Object> comHora(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>(row);
        LocalDateTime dt = dataHora(row.get("data_hora"));
        r.put("hora", String.format("%02d:%02d", dt.getHour(), dt.getMinute()));
        r.put("minuto_do_dia", dt.getHour() * 60 + dt.getMinute());
        return r;
    }
}
