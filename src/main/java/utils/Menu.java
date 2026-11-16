package utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ZTF_J2EE - Menu
 *
 * Menu lateral do sistema hospitalar, montado a partir das PERMISSOES da sessao. O menu so
 * esconde o que o usuario nao pode usar; quem garante o acesso e o backend (routes.json +
 * controllers), entao esconder um item nunca substitui a verificacao no servidor.
 *
 * Itens com disponivel = false sao modulos previstos para as proximas entregas (aparecem
 * desabilitados, com "em breve").
 */
public final class Menu {

    /** permissao null = qualquer usuario autenticado; icone = id do simbolo em assets/icons/icons.svg. */
    public record Item(String secao, String rotulo, String url, String permissao, String icone, boolean disponivel) {
    }

    private static final List<Item> ITENS = List.of(
            new Item("", "Início", "dashboard", null, "i-home", true),
            new Item("Cadastro e agenda", "Pacientes", "pacientes", "pacientes.view", "i-users", true),
            new Item("Cadastro e agenda", "Agenda de consultas", "agenda", "agenda.view", "i-calendar", true),
            new Item("Cadastro e agenda", "Agenda de exames", "exames", "exames.agenda.view", "i-flask", true),
            new Item("Assistência", "Minha agenda", "agenda", "agenda.view.propria", "i-calendar", true),
            new Item("Assistência", "Fila de triagem", "triagem", "triagem.fila", "i-pulse", true),
            new Item("Assistência", "Fila de atendimento", "atendimento", "atendimento.fila", "i-stethoscope", true),
            new Item("Administração", "Usuários e permissões", "admin", "usuarios.view", "i-shield", true),
            new Item("Administração", "Profissionais", "profissionais", "profissionais.manage", "i-id-card", true),
            new Item("Administração", "Especialidades", "especialidades", "especialidades.manage", "i-list", true),
            new Item("Administração", "Auditoria", "auditoria", "auditoria.view", "i-file-text", true)
    );

    /** Atalhos da tela inicial (como o "Seus atalhos"): so acoes que existem e que o perfil pode usar. */
    private static final List<Item> ATALHOS = List.of(
            new Item("", "Cadastrar paciente", "pacientes/novo", "pacientes.create", "i-user-plus", true),
            new Item("", "Buscar paciente", "pacientes", "pacientes.view", "i-search", true),
            new Item("", "Agendar consulta", "agenda/nova", "consultas.agendar", "i-calendar-plus", true),
            new Item("", "Agendar exame", "exames/novo", "exames.agendar", "i-flask", true),
            new Item("", "Agenda do dia", "agenda", "agenda.view", "i-calendar", true),
            new Item("", "Minha agenda", "agenda", "agenda.view.propria", "i-calendar", true),
            new Item("", "Exames do dia", "exames", "exames.agenda.view", "i-clipboard", true),
            new Item("", "Fila de triagem", "triagem", "triagem.fila", "i-pulse", true),
            new Item("", "Fila de atendimento", "atendimento", "atendimento.fila", "i-stethoscope", true),
            new Item("", "Usuários e permissões", "admin", "usuarios.view", "i-shield", true),
            new Item("", "Profissionais", "profissionais", "profissionais.manage", "i-id-card", true),
            new Item("", "Auditoria", "auditoria", "auditoria.view", "i-file-text", true),
            new Item("", "Trocar senha", "trocar-senha", null, "i-key", true)
    );

    private Menu() {
    }

    /** Itens visiveis para as permissoes informadas, agrupados por secao (ordem preservada). */
    public static Map<String, List<Item>> visiveis(List<String> permissoes) {
        Map<String, List<Item>> porSecao = new LinkedHashMap<>();
        for (Item item : ITENS) {
            if (permitido(item, permissoes)) {
                porSecao.computeIfAbsent(item.secao(), k -> new ArrayList<>()).add(item);
            }
        }
        return porSecao;
    }

    public static List<Item> atalhos(List<String> permissoes) {
        List<Item> lista = new ArrayList<>();
        for (Item item : ATALHOS) {
            if (permitido(item, permissoes)) {
                lista.add(item);
            }
        }
        return lista;
    }

    private static boolean permitido(Item item, List<String> permissoes) {
        return item.permissao() == null || (permissoes != null && permissoes.contains(item.permissao()));
    }

    /** O item corresponde a rota atual (mesmo caminho ou subcaminho, ex.: pacientes/12). */
    public static boolean ativo(Item item, String rotaAtual) {
        if (rotaAtual == null) {
            return false;
        }
        String r = rotaAtual.replaceAll("^/+", "");
        if ("dashboard".equals(item.url())) {
            return r.equals("painel") || r.startsWith("dashboard");
        }
        return r.equals(item.url()) || r.startsWith(item.url() + "/");
    }

    /** Nome amigavel do perfil principal do usuario (para o cabecalho). */
    public static String perfilPrincipal(List<String> grupos) {
        if (grupos == null) {
            return "Usuário";
        }
        if (grupos.contains("medico")) {
            return "Médico";
        }
        if (grupos.contains("enfermeiro")) {
            return "Enfermagem";
        }
        if (grupos.contains("recepcionista")) {
            return "Recepção";
        }
        if (grupos.contains("admin")) {
            return "Administrador";
        }
        return "Usuário";
    }

    /** Iniciais para o avatar (ex.: "Marina Medica" -> "MM"). */
    public static String iniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "?";
        }
        String[] partes = nome.replaceAll("\\(.*?\\)", "").trim().split("\\s+");
        String primeira = partes[0].substring(0, 1);
        String ultima = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (primeira + ultima).toUpperCase();
    }

    /** Primeiro nome, para a saudacao no menu lateral. */
    public static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }
}
