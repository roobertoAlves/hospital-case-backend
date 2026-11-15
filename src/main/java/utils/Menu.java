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
 * Itens com disponivel = false sao modulos planejados para a Fase 5 (aparecem desabilitados).
 */
public final class Menu {

    /** permissao null = qualquer usuario autenticado. */
    public record Item(String secao, String rotulo, String url, String permissao, boolean disponivel) {
    }

    private static final List<Item> ITENS = List.of(
            new Item("Geral", "Painel", "dashboard", null, true),
            new Item("Recepção", "Pacientes", "pacientes", "pacientes.view", false),
            new Item("Recepção", "Agenda", "agenda", "agenda.view", false),
            new Item("Enfermagem", "Fila de triagem", "triagem", "triagem.fila", false),
            new Item("Atendimento", "Minha agenda", "minha-agenda", "agenda.view.propria", false),
            new Item("Atendimento", "Fila de atendimento", "atendimento", "atendimento.fila", false),
            new Item("Administração", "Usuários e permissões", "admin", "usuarios.view", true),
            new Item("Administração", "Profissionais", "profissionais", "profissionais.manage", false),
            new Item("Administração", "Especialidades", "especialidades", "especialidades.manage", false),
            new Item("Administração", "Auditoria", "auditoria", "auditoria.view", false),
            new Item("Conta", "Trocar senha", "trocar-senha", null, true),
            new Item("Conta", "Sair", "logout", null, true)
    );

    private Menu() {
    }

    /** Itens visiveis para as permissoes informadas, agrupados por secao (ordem preservada). */
    public static Map<String, List<Item>> visiveis(List<String> permissoes) {
        Map<String, List<Item>> porSecao = new LinkedHashMap<>();
        for (Item item : ITENS) {
            if (item.permissao() == null || (permissoes != null && permissoes.contains(item.permissao()))) {
                porSecao.computeIfAbsent(item.secao(), k -> new ArrayList<>()).add(item);
            }
        }
        return porSecao;
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
}
