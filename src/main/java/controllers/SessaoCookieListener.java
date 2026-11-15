package controllers;

import java.util.EnumSet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.SessionTrackingMode;
import javax.servlet.annotation.WebListener;

/**
 * ZTF_J2EE - SessaoCookieListener
 *
 * Mantem a sessao SO no cookie (HttpOnly, SameSite=Lax). Por padrao o Tomcat tambem aceita o
 * identificador na URL (";jsessionid=..."), que vaza em historico, logs e no cabecalho
 * Referer e facilita a fixacao de sessao. Sem web.xml, a configuracao e feita aqui, na
 * inicializacao da aplicacao.
 */
@WebListener
public class SessaoCookieListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
