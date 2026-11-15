package controllers;

import java.io.IOException;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import models.Usuario;
import utils.Sanitize;

/**
 * ZTF_J2EE - SessaoFilter
 *
 * Mantem os dados da sessao coerentes com o banco (RN-AUT-06). Antes, grupos, permissoes e
 * status ficavam congelados ate o proximo login: um usuario bloqueado continuava usando o
 * sistema. Quando o administrador altera grupos, permissoes ou status, Usuario.invalidarAcl()
 * incrementa uma versao global; aqui, cada sessao com versao antiga e recarregada uma vez
 * (ou encerrada, se o usuario foi inativado/removido).
 *
 * Fica no pacote controllers porque depende do model Usuario (utils nao depende de models).
 * A versao vive na memoria da JVM: com varios servidores, seria preciso compartilha-la.
 */
@WebFilter(filterName = "SessaoFilter", urlPatterns = {"/*"})
public class SessaoFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (req instanceof HttpServletRequest) {
            atualizarSessao((HttpServletRequest) req);
        }
        chain.doFilter(req, res);
    }

    public static void atualizarSessao(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        Object raw;
        try {
            raw = session.getAttribute("user");
        } catch (IllegalStateException invalidada) {
            return;
        }
        if (!(raw instanceof Map<?, ?>)) {
            return;
        }
        Map<?, ?> user = (Map<?, ?>) raw;
        long versaoSessao = user.get("acl_versao") instanceof Number ? ((Number) user.get("acl_versao")).longValue() : -1L;
        if (versaoSessao == Usuario.versaoAcl()) {
            return;
        }
        int idUsuario = Sanitize.intVal(user.get("id_usuario"), 0);
        Map<String, Object> atualizado = idUsuario > 0 ? new Usuario().carregarSessao(idUsuario) : null;
        if (atualizado == null) {
            session.invalidate();
        } else {
            session.setAttribute("user", atualizado);
        }
    }

    @Override
    public void destroy() {
    }
}
