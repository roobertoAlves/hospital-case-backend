package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Vacina
 *
 * Catalogo de vacinas, agenda da sala de vacinacao e cartao de vacinacao.
 * - Sala: segunda a sexta, 08h-17h, um paciente a cada 15 min (UNIQUE no banco).
 * - Dose: a proxima do esquema; nao agenda alem do total de doses nem antes do intervalo
 *   minimo desde a ultima aplicacao; uma dose pendente por vacina.
 * - Aplicacao: so a enfermagem registra (lote obrigatorio), no dia; o registro e imutavel (triggers).
 */
public class Vacina {

    public static final LocalTime ABRE = LocalTime.of(8, 0);
    public static final LocalTime FECHA = LocalTime.of(17, 0);
    public static final int PASSO_MIN = 15;

    public List<Map<String, Object>> catalogo() {
        return Sql.lista("SELECT id_vacina, nome, descricao, doses, intervalo_dias, publico FROM vacinas WHERE ativo = 1 ORDER BY nome");
    }

    public List<Agenda.Horario> horarios(LocalDate dia) {
        List<Agenda.Horario> lista = new ArrayList<>();
        if (dia == null || dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return lista;
        }
        List<Object> ocupados = new ArrayList<>();
        for (Map<String, Object> r : Sql.lista("SELECT data_hora FROM vacinas_agendadas WHERE slot_ativo = 1 AND data_hora >= ? AND data_hora < ?",
                dia.atStartOfDay(), dia.plusDays(1).atStartOfDay())) {
            ocupados.add(Agenda.dataHora(r.get("data_hora")));
        }
        LocalDateTime agora = Agenda.agora();
        for (LocalTime t = ABRE; t.isBefore(FECHA); t = t.plusMinutes(PASSO_MIN)) {
            LocalDateTime inicio = dia.atTime(t);
            lista.add(new Agenda.Horario(t, inicio.isAfter(agora) && !ocupados.contains(inicio)));
        }
        return lista;
    }

    public Agenda.Resultado agendar(int idPaciente, int idVacina, LocalDateTime quando, int idUsuario, String origem) {
        if (quando == null || !quando.isAfter(Agenda.agora())) {
            return Agenda.Resultado.falha("Escolha uma data e um horário futuros.");
        }
        LocalTime t = quando.toLocalTime();
        DayOfWeek d = quando.getDayOfWeek();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY || t.isBefore(ABRE) || !t.isBefore(FECHA) || t.getMinute() % PASSO_MIN != 0) {
            return Agenda.Resultado.falha("Horário fora do atendimento da sala de vacinação.");
        }
        Map<String, Object> v = Sql.um("SELECT doses, intervalo_dias FROM vacinas WHERE id_vacina = ? AND ativo = 1", idVacina);
        if (v == null) {
            return Agenda.Resultado.falha("Vacina indisponível.");
        }
        if (Sql.um("SELECT 1 FROM pacientes WHERE id_paciente = ? AND ativo = 1", idPaciente) == null) {
            return Agenda.Resultado.falha("Paciente inativo ou inexistente.");
        }
        if (Sql.um("SELECT 1 FROM vacinas_agendadas WHERE id_paciente = ? AND id_vacina = ? AND status = 'agendada'", idPaciente, idVacina) != null) {
            return Agenda.Resultado.falha("Já existe uma dose desta vacina agendada.");
        }
        Map<String, Object> ultima = Sql.um("SELECT COUNT(*) AS n, MAX(aplicada_em) AS ultima FROM vacinas_aplicadas WHERE id_paciente = ? AND id_vacina = ?",
                idPaciente, idVacina);
        int aplicadas = ((Number) ultima.get("n")).intValue();
        int doses = ((Number) v.get("doses")).intValue();
        if (aplicadas >= doses && doses > 1) {
            return Agenda.Resultado.falha("Esquema completo: todas as " + doses + " doses já foram aplicadas.");
        }
        Object intervalo = v.get("intervalo_dias");
        if (aplicadas > 0 && intervalo != null && ultima.get("ultima") != null
                && quando.isBefore(Agenda.dataHora(ultima.get("ultima")).plusDays(((Number) intervalo).longValue()))) {
            return Agenda.Resultado.falha("A próxima dose só pode ser a partir de "
                    + Agenda.dataHora(ultima.get("ultima")).plusDays(((Number) intervalo).longValue()).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        int dose = doses == 1 ? 1 : aplicadas + 1;
        try (Connection conn = DBConnection.openConnection()) {
            long id = Sql.inserir(conn, "INSERT INTO vacinas_agendadas (id_paciente, id_vacina, dose, data_hora, origem, criado_por) VALUES (?, ?, ?, ?, ?, ?)",
                    idPaciente, idVacina, dose, quando, "portal".equals(origem) ? "portal" : "recepcao", idUsuario);
            Auditoria.registrar(conn, idUsuario, "vacina.agendada", "vacinas_agendadas", id, idPaciente, "dose=" + dose, null);
            return new Agenda.Resultado(true, id, null);
        } catch (SQLException e) {
            if (Sql.ehDuplicidade(e)) {
                return Agenda.Resultado.falha("Horário indisponível: acabou de ser reservado por outra pessoa.");
            }
            throw new RuntimeException("Falha ao agendar vacina", e);
        }
    }

    /** Cancela; com idPacienteDono, so se o agendamento for dele (portal). */
    public Agenda.Resultado cancelar(int idAgendamento, String motivo, int idUsuario, Integer idPacienteDono) {
        String m = motivo == null ? "" : Sanitize.string(motivo).trim();
        if (m.length() < 3) {
            return Agenda.Resultado.falha("Informe o motivo do cancelamento.");
        }
        Map<String, Object> a = Sql.um("SELECT id_paciente FROM vacinas_agendadas WHERE id_agendamento = ?", idAgendamento);
        if (a == null || (idPacienteDono != null && ((Number) a.get("id_paciente")).intValue() != idPacienteDono)) {
            return Agenda.Resultado.falha("Agendamento não encontrado.");
        }
        int n = Sql.executar("UPDATE vacinas_agendadas SET status = 'cancelada', motivo_cancelamento = ? WHERE id_agendamento = ? AND status = 'agendada'",
                m.length() > 255 ? m.substring(0, 255) : m, idAgendamento);
        if (n == 0) {
            return Agenda.Resultado.falha("Só agendamentos ainda não realizados podem ser cancelados.");
        }
        Auditoria.registrar(idUsuario, "vacina.cancelada", "vacinas_agendadas", idAgendamento, ((Number) a.get("id_paciente")).intValue(), null, null);
        return new Agenda.Resultado(true, idAgendamento, null);
    }

    /** Enfermagem registra a aplicacao (no dia do agendamento). Devolve erros por campo ou vazio. */
    public Map<String, String> aplicar(int idAgendamento, Map<String, Object> dados, int idEnfermeiro, int idUsuario) {
        Map<String, String> erros = new LinkedHashMap<>();
        String lote = texto(dados.get("lote"));
        String fabricante = texto(dados.get("fabricante"));
        String obs = texto(dados.get("observacoes"));
        if (lote.length() < 2 || lote.length() > 40) {
            erros.put("lote", "Informe o lote (até 40 caracteres).");
        }
        if (fabricante.length() > 80) {
            erros.put("fabricante", "Até 80 caracteres.");
        }
        if (obs.length() > 255) {
            erros.put("observacoes", "Até 255 caracteres.");
        }
        Map<String, Object> a = Sql.um("SELECT id_paciente, id_vacina, dose, data_hora, status FROM vacinas_agendadas WHERE id_agendamento = ?", idAgendamento);
        if (a == null) {
            erros.put("_", "Agendamento não encontrado.");
        } else if (!"agendada".equals(a.get("status"))) {
            erros.put("_", "Este agendamento não está mais pendente.");
        } else if (!Agenda.dataHora(a.get("data_hora")).toLocalDate().equals(Agenda.agora().toLocalDate())) {
            erros.put("_", "A aplicação é registrada no dia agendado.");
        }
        if (!erros.isEmpty()) {
            return erros;
        }
        int idPaciente = ((Number) a.get("id_paciente")).intValue();
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                int n = Sql.executar(conn, "UPDATE vacinas_agendadas SET status = 'aplicada' WHERE id_agendamento = ? AND status = 'agendada'", idAgendamento);
                if (n == 0) {
                    conn.rollback();
                    erros.put("_", "Este agendamento acabou de mudar. Atualize a tela.");
                    return erros;
                }
                long id = Sql.inserir(conn, "INSERT INTO vacinas_aplicadas (id_paciente, id_vacina, dose, lote, fabricante, id_enfermeiro, id_agendamento, observacoes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", idPaciente, a.get("id_vacina"), a.get("dose"), lote, fabricante.isEmpty() ? null : fabricante,
                        idEnfermeiro, idAgendamento, obs.isEmpty() ? null : obs);
                Auditoria.registrar(conn, idUsuario, "vacina.aplicada", "vacinas_aplicadas", id, idPaciente, null, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar aplicacao", e);
        }
        return erros;
    }

    public Agenda.Resultado ausente(int idAgendamento, int idUsuario) {
        Map<String, Object> a = Sql.um("SELECT id_paciente FROM vacinas_agendadas WHERE id_agendamento = ?", idAgendamento);
        int n = Sql.executar("UPDATE vacinas_agendadas SET status = 'ausente' WHERE id_agendamento = ? AND status = 'agendada' AND data_hora <= NOW()", idAgendamento);
        if (a == null || n == 0) {
            return Agenda.Resultado.falha("Só agendamentos pendentes cujo horário já passou podem ser marcados como falta.");
        }
        Auditoria.registrar(idUsuario, "vacina.ausente", "vacinas_agendadas", idAgendamento, ((Number) a.get("id_paciente")).intValue(), null, null);
        return new Agenda.Resultado(true, idAgendamento, null);
    }

    public List<Map<String, Object>> agendaDoDia(LocalDate dia) {
        return Sql.lista("SELECT a.id_agendamento, a.data_hora, a.dose, a.status, a.origem, a.id_paciente, p.nome AS paciente, p.data_nascimento, "
                + "v.nome AS vacina, v.doses FROM vacinas_agendadas a JOIN pacientes p ON p.id_paciente = a.id_paciente "
                + "JOIN vacinas v ON v.id_vacina = a.id_vacina WHERE a.data_hora >= ? AND a.data_hora < ? ORDER BY a.data_hora",
                dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
    }

    /** Cartao de vacinacao (aplicadas) e agendamentos do paciente. */
    public Map<String, Object> cartao(int idPaciente) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("aplicadas", Sql.lista("SELECT ap.id_aplicacao, ap.dose, ap.lote, ap.fabricante, ap.aplicada_em, v.id_vacina, v.nome AS vacina, v.doses, "
                + "pr.nome AS aplicador FROM vacinas_aplicadas ap JOIN vacinas v ON v.id_vacina = ap.id_vacina "
                + "LEFT JOIN profissionais pr ON pr.id_profissional = ap.id_enfermeiro WHERE ap.id_paciente = ? ORDER BY ap.aplicada_em DESC", idPaciente));
        d.put("agendadas", Sql.lista("SELECT a.id_agendamento, a.data_hora, a.dose, a.status, a.motivo_cancelamento, v.nome AS vacina "
                + "FROM vacinas_agendadas a JOIN vacinas v ON v.id_vacina = a.id_vacina WHERE a.id_paciente = ? AND a.status IN ('agendada', 'cancelada', 'ausente') "
                + "ORDER BY a.data_hora DESC LIMIT 50", idPaciente));
        return d;
    }

    private static String texto(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
