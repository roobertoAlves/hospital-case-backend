package controllers;

import database.DatabaseProvisioner;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import utils.Config;

@WebListener
public class DatabaseWarmup implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(DatabaseWarmup.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Config.bindServletContext(sce.getServletContext());
        long started = System.currentTimeMillis();
        Map<String, Object> status = DatabaseProvisioner.checkAndProvision();
        LOG.info("Database warm-up: " + status.get("action") + " " + status.getOrDefault("message", "")
                + " (" + (System.currentTimeMillis() - started) + " ms)");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
