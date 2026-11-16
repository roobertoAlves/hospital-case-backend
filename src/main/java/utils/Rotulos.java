package utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * ZTF_J2EE - Rotulos
 *
 * Textos e classes de exibicao usados pelas JSPs (status como pilula, datas por extenso),
 * para que a regra de apresentacao fique num lugar so e fora das paginas.
 */
public final class Rotulos {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private Rotulos() {
    }

    /** Situacao da consulta: [texto, classe da pilula]. */
    private static final Map<String, String[]> CONSULTA = Map.of(
            "agendada", new String[]{"Agendada", "neutral"},
            "aguardando_triagem", new String[]{"Aguardando triagem", "warning"},
            "em_triagem", new String[]{"Em triagem", "warning"},
            "aguardando_atendimento", new String[]{"Aguardando médico", "accent"},
            "em_atendimento", new String[]{"Em atendimento", "accent"},
            "realizada", new String[]{"Realizada", "positive"},
            "cancelada", new String[]{"Cancelada", "negative"},
            "ausente", new String[]{"Faltou", "neutral"});

    private static final Map<String, String[]> EXAME = Map.of(
            "agendado", new String[]{"Agendado", "neutral"},
            "realizado", new String[]{"Realizado", "positive"},
            "cancelado", new String[]{"Cancelado", "negative"},
            "ausente", new String[]{"Faltou", "neutral"});

    public static String pilulaConsulta(Object status) {
        return pilula(CONSULTA, status);
    }

    public static String pilulaExame(Object status) {
        return pilula(EXAME, status);
    }

    private static String pilula(Map<String, String[]> mapa, Object status) {
        String[] r = mapa.getOrDefault(String.valueOf(status), new String[]{String.valueOf(status), "neutral"});
        return "<span class=\"hc-pill " + r[1] + "\">" + Sanitize.escapeHtml(r[0]) + "</span>";
    }

    /** "sábado, 15 de novembro" */
    public static String diaPorExtenso(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR));
    }

    /** "15/11/2026" */
    public static String data(Object valor) {
        if (valor == null) {
            return "—";
        }
        LocalDate d = valor instanceof LocalDate ? (LocalDate) valor
                : valor instanceof java.sql.Date ? ((java.sql.Date) valor).toLocalDate()
                : valor instanceof java.time.LocalDateTime ? ((java.time.LocalDateTime) valor).toLocalDate()
                : LocalDate.parse(String.valueOf(valor).substring(0, 10));
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /** "15/11/2026 14:30" */
    public static String dataHora(Object valor) {
        if (valor == null) {
            return "—";
        }
        java.time.LocalDateTime dt = valor instanceof java.time.LocalDateTime ? (java.time.LocalDateTime) valor
                : java.time.LocalDateTime.parse(String.valueOf(valor).replace(' ', 'T'));
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /** "14:30" a partir de uma data-hora; travessao se ausente. */
    public static String hora(Object valor) {
        if (valor == null) {
            return "—";
        }
        java.time.LocalDateTime dt = valor instanceof java.time.LocalDateTime ? (java.time.LocalDateTime) valor
                : java.time.LocalDateTime.parse(String.valueOf(valor).replace(' ', 'T'));
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }

    /** Minuto do dia (0-1439) de um horario TIME/LocalTime/texto HH:mm. */
    public static int minutosDoDia(Object hora) {
        java.time.LocalTime t = hora instanceof java.time.LocalTime ? (java.time.LocalTime) hora
                : hora instanceof java.sql.Time ? ((java.sql.Time) hora).toLocalTime()
                : java.time.LocalTime.parse(String.valueOf(hora));
        return t.getHour() * 60 + t.getMinute();
    }

    /** Percentual para posicionar barras na linha do dia (limitado a 0-100). */
    public static String pct(double v) {
        return String.format(Locale.ROOT, "%.3f%%", Math.max(0, Math.min(100, v)));
    }

    /** 1..7 (ISO, segunda = 1) -> "Segunda"... */
    public static String diaDaSemana(Object dia) {
        String[] nomes = {"Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"};
        int d = Sanitize.intVal(dia, 0);
        return d >= 1 && d <= 7 ? nomes[d - 1] : "—";
    }

    /** Valor ou travessao cinza para ausente. */
    public static String ou(Object valor) {
        return valor == null || String.valueOf(valor).isBlank() ? "—" : Sanitize.escapeHtml(String.valueOf(valor));
    }

    public static String sexo(Object v) {
        if (v == null) {
            return "—";
        }
        switch (String.valueOf(v)) {
            case "feminino": return "Feminino";
            case "masculino": return "Masculino";
            case "outro": return "Outro";
            default: return "Não informado";
        }
    }
}
