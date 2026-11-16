package controllers;

import java.util.EnumSet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.SessionTrackingMode;
import javax.servlet.annotation.WebListener;

/**
 * ZTF_J2EE - SessionCookieListener
 *
 * Keeps the session ONLY in the cookie (HttpOnly, SameSite=Lax). By default Tomcat also accepts
 * the id in the URL (";jsessionid=..."), which leaks through history, logs and the Referer
 * header and makes session fixation easier. Without a web.xml, this is configured here, when
 * the application starts.
 */
@WebListener
public class SessionCookieListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
