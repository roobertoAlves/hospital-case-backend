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
 * ZTF_J2EE - Model AgendaExames
 *
 * Agendamento de exames pela recepcao (requisito incluido na Fase 5, migracao 001):
 * - cada tipo de exame tem duracao, preparo e um local padrao;
 * - o local atende num horario (de segunda a sexta, e sabado quando atende_sabado = 1);
 * - nao pode haver dois exames que se sobreponham no mesmo local, nem o mesmo paciente em
 *   dois exames ao mesmo tempo;
 * - o agendamento pode vir de uma solicitacao medica (tabela exames) ou ser avulso.
 * Concorrencia como em Agenda: trava a linha do local (FOR UPDATE) e a UNIQUE do banco.
 */
public class AgendaExames {

    /** Grade dos horarios oferecidos: a cada 10 minutos dentro do horario do local. */
    static final int PASSO_MIN = 10;

    public List<Map<String, Object>> tiposAtivos() {
        return Sql.lista("SELECT t.id_tipo_exame, t.nome, t.categoria, t.duracao_min, t.preparo, t.id_local, l.nome AS local "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local WHERE t.ativo = 1 AND l.ativo = 1 ORDER BY t.categoria, t.nome");
    }

    public List<Map<String, Object>> locaisAtivos() {
        return Sql.lista("SELECT id_local, nome, hora_inicio, hora_fim, atende_sabado FROM locais_exame WHERE ativo = 1 ORDER BY nome");
    }

    public List<Map<String, Object>> doDia(LocalDate dia) {
        return Sql.lista("SELECT x.id_exame_agendado, x.data_hora, x.duracao_min, x.status, x.id_paciente, pa.nome AS paciente, "
                + "t.nome AS exame, l.id_local, l.nome AS local "
                + "FROM exames_agendados x JOIN pacientes pa ON pa.id_paciente = x.id_paciente "
                + "JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame JOIN locais_exame l ON l.id_local = x.id_local "
                + "WHERE x.data_hora >= ? AND x.data_hora < ? ORDER BY x.data_hora, l.nome",
                dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
    }

    /** Horarios para um tipo de exame no dia, no local do tipo, marcando os ocupados e passados. */
    public List<Agenda.Horario> horarios(int idTipoExame, LocalDate dia) {
        Map<String, Object> tipo = Sql.um("SELECT t.duracao_min, l.id_local, l.hora_inicio, l.hora_fim, l.atende_sabado "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local "
                + "WHERE t.id_tipo_exame = ? AND t.ativo = 1 AND l.ativo = 1", idTipoExame);
        if (tipo == null || !localAtende(tipo, dia)) {
            return List.of();
        }
        int duracao = ((Number) tipo.get("duracao_min")).intValue();
        int idLocal = ((Number) tipo.get("id_local")).intValue();
        List<Map<String, Object>> ocupados = Sql.lista("SELECT data_hora, duracao_min FROM exames_agendados WHERE id_local = ? "
                + "AND slot_ativo = 1 AND data_hora >= ? AND data_hora < ?", idLocal, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
        LocalDateTime agora = Agenda.agora();
        List<Agenda.Horario> lista = new ArrayList<>();
        LocalTime t = Agenda.hora(tipo.get("hora_inicio"));
        LocalTime fim = Agenda.hora(tipo.get("hora_fim"));
        while (!t.plusMinutes(duracao).isAfter(fim)) {
            LocalDateTime inicio = dia.atTime(t);
            lista.add(new Agenda.Horario(t, inicio.isAfter(agora) && !Agenda.sobrepoe(ocupados, inicio, duracao)));
            t = t.plusMinutes(PASSO_MIN);
        }
        return lista;
    }

    public Agenda.Resultado agendar(int idPaciente, int idTipoExame, LocalDateTime dataHora, Integer idSolicitacao, int idUsuario) {
        if (dataHora == null) {
            return Agenda.Resultado.falha("Escolha a data e o horário.");
        }
        if (!dataHora.isAfter(Agenda.agora())) {
            return Agenda.Resultado.falha("Não é possível agendar no passado.");
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Object> tipo = Sql.um(conn, "SELECT t.duracao_min, l.id_local, l.hora_inicio, l.hora_fim, l.atende_sabado "
                        + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local "
                        + "WHERE t.id_tipo_exame = ? AND t.ativo = 1 AND l.ativo = 1", idTipoExame);
                if (tipo == null) {
                    conn.rollback();
                    return Agenda.Resultado.falha("Tipo de exame inativo ou inexistente.");
                }
                int idLocal = ((Number) tipo.get("id_local")).intValue();
                int duracao = ((Number) tipo.get("duracao_min")).intValue();
                Sql.um(conn, "SELECT id_local FROM locais_exame WHERE id_local = ? FOR UPDATE", idLocal);

                if (Sql.um(conn, "SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", idPaciente) == null) {
                    conn.rollback();
                    return Agenda.Resultado.falha("Paciente inativo ou inexistente.");
                }
                LocalTime ini = Agenda.hora(tipo.get("hora_inicio"));
                LocalTime fimLocal = Agenda.hora(tipo.get("hora_fim"));
                LocalTime t = dataHora.toLocalTime();
                if (!localAtende(tipo, dataHora.toLocalDate()) || t.isBefore(ini) || t.plusMinutes(duracao).isAfter(fimLocal)) {
                    conn.rollback();
                    return Agenda.Resultado.falha("Horário fora do atendimento do local do exame.");
                }
                if (idSolicitacao != null && Sql.um(conn, "SELECT 1 FROM exames x JOIN consultas c ON c.id_consulta = x.id_consulta "
                        + "WHERE x.id_exame = ? AND c.id_paciente = ? AND x.status = 'solicitado'", idSolicitacao, idPaciente) == null) {
                    conn.rollback();
                    return Agenda.Resultado.falha("Solicitação médica não encontrada para este paciente.");
                }
                LocalDateTime fim = dataHora.plusMinutes(duracao);
                String sobreposicao = " AND slot_ativo = 1 AND data_hora < ? AND DATE_ADD(data_hora, INTERVAL duracao_min MINUTE) > ?";
                if (Sql.um(conn, "SELECT 1 FROM exames_agendados WHERE id_local = ?" + sobreposicao, idLocal, fim, dataHora) != null) {
                    conn.rollback();
                    return Agenda.Resultado.falha("Horário indisponível no local do exame.");
                }
                if (Sql.um(conn, "SELECT 1 FROM exames_agendados WHERE id_paciente = ?" + sobreposicao, idPaciente, fim, dataHora) != null) {
                    conn.rollback();
                    return Agenda.Resultado.falha("O paciente já tem exame nesse horário.");
                }
                long id = Sql.inserir(conn, "INSERT INTO exames_agendados (id_paciente, id_tipo_exame, id_local, id_solicitacao, "
                        + "data_hora, duracao_min, criado_por) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        idPaciente, idTipoExame, idLocal, idSolicitacao, dataHora, duracao, idUsuario);
                Auditoria.registrar(conn, idUsuario, "exame.agendado", "exames_agendados", id, idPaciente, null, null);
                conn.commit();
                return new Agenda.Resultado(true, id, null);
            } catch (SQLException e) {
                conn.rollback();
                if (Sql.ehDuplicidade(e)) {
                    return Agenda.Resultado.falha("Horário indisponível: acabou de ser reservado por outra pessoa.");
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao agendar exame", e);
        }
    }

    public Agenda.Resultado cancelar(int idExameAgendado, String motivo, int idUsuario) {
        String m = motivo == null ? "" : motivo.trim();
        if (m.length() < 3) {
            return Agenda.Resultado.falha("Informe o motivo do cancelamento.");
        }
        Map<String, Object> x = Sql.um("SELECT id_paciente FROM exames_agendados WHERE id_exame_agendado = ?", idExameAgendado);
        if (x == null) {
            return Agenda.Resultado.falha("Exame não encontrado.");
        }
        int n = Sql.executar("UPDATE exames_agendados SET status = 'cancelado', motivo_cancelamento = ?, cancelado_em = NOW() "
                + "WHERE id_exame_agendado = ? AND status = 'agendado'", m.length() > 255 ? m.substring(0, 255) : m, idExameAgendado);
        if (n == 0) {
            return Agenda.Resultado.falha("Só exames ainda agendados podem ser cancelados.");
        }
        Auditoria.registrar(idUsuario, "exame.cancelado", "exames_agendados", idExameAgendado,
                ((Number) x.get("id_paciente")).intValue(), null, null);
        return new Agenda.Resultado(true, idExameAgendado, null);
    }

    /** Marca o exame como realizado ou ausente (so no dia, para exame agendado). */
    public Agenda.Resultado registrarSituacao(int idExameAgendado, String situacao, int idUsuario) {
        if (!"realizado".equals(situacao) && !"ausente".equals(situacao)) {
            return Agenda.Resultado.falha("Situação inválida.");
        }
        Map<String, Object> x = Sql.um("SELECT id_paciente, data_hora FROM exames_agendados WHERE id_exame_agendado = ? AND status = 'agendado'",
                idExameAgendado);
        if (x == null) {
            return Agenda.Resultado.falha("Só exames agendados podem ser atualizados.");
        }
        if (!Agenda.dataHora(x.get("data_hora")).toLocalDate().equals(Agenda.agora().toLocalDate())) {
            return Agenda.Resultado.falha("A situação só pode ser registrada no dia do exame.");
        }
        Sql.executar("UPDATE exames_agendados SET status = ? WHERE id_exame_agendado = ? AND status = 'agendado'", situacao, idExameAgendado);
        Auditoria.registrar(idUsuario, "exame." + situacao, "exames_agendados", idExameAgendado,
                ((Number) x.get("id_paciente")).intValue(), null, null);
        return new Agenda.Resultado(true, idExameAgendado, null);
    }

    /** Dados para a notificacao: sem o nome do exame (dado de saude, RN-NOT-01). */
    public Map<String, Object> resumoParaAviso(long id) {
        return Sql.um("SELECT x.data_hora, pa.nome AS paciente, pa.email, l.nome AS local FROM exames_agendados x "
                + "JOIN pacientes pa ON pa.id_paciente = x.id_paciente JOIN locais_exame l ON l.id_local = x.id_local "
                + "WHERE x.id_exame_agendado = ?", id);
    }

    private static boolean localAtende(Map<String, Object> local, LocalDate dia) {
        DayOfWeek d = dia.getDayOfWeek();
        if (d == DayOfWeek.SUNDAY) {
            return false;
        }
        return d != DayOfWeek.SATURDAY || ((Number) local.get("atende_sabado")).intValue() == 1;
    }
}
