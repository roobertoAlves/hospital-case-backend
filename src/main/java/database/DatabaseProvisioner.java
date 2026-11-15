package database;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import utils.Config;

/**
 * ZTF_J2EE - DatabaseProvisioner
 *
 * Verifica e auto-provisiona o banco de dados e suas tabelas essenciais (script.sql) no boot.
 */
public class DatabaseProvisioner {

    private static final Map<String, Map<String, Object>> CACHED_STATUS = new ConcurrentHashMap<>();

    public static final List<String> REQUIRED_TABLES = Arrays.asList(
            "usuarios",
            "grupos",
            "permissoes",
            "usuarios_grupos",
            "grupos_permissoes",
            "arquivos_upload",
            "chamados",
            "recuperacoes_senha",
            // Dominio hospitalar (Fase 3)
            "usuarios_seguranca",
            "pacientes",
            "paciente_alergias",
            "profissionais",
            "medicos",
            "enfermeiros",
            "especialidades",
            "medico_especialidades",
            "disponibilidades_medico",
            "classificacoes_prioridade",
            "consultas",
            "triagens",
            "prontuarios",
            "prontuario_versoes",
            "evolucoes_clinicas",
            "receitas",
            "receita_itens",
            "exames",
            "auditoria",
            "notificacoes"
    );

    public static Map<String, Object> checkAndProvision() {
        return checkAndProvision(null, false);
    }

    public static synchronized Map<String, Object> checkAndProvision(String connectionName, boolean forceRecheck) {
        String defaultName = Config.getString("database.default", "mysql");
        String name = (connectionName != null && !connectionName.isEmpty()) ? connectionName : defaultName;

        if (!forceRecheck && CACHED_STATUS.containsKey(name)) {
            return CACHED_STATUS.get(name);
        }

        Object rawCfg = Config.get("database.connections." + name);
        if (!(rawCfg instanceof Map<?, ?>)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("status", false);
            result.put("error", "ERR_DATABASE_CONFIG_MISSING");
            result.put("message", "Configuracao da conexao '" + name + "' nao encontrada no arquivo config.json.");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("connection", name);
            result.put("details", details);
            CACHED_STATUS.put(name, result);
            return result;
        }

        Map<?, ?> config = (Map<?, ?>) rawCfg;
        String driver   = String.valueOf(config.get("driver") != null ? config.get("driver") : "mysql").toLowerCase();
        String host     = String.valueOf(config.get("host") != null ? config.get("host") : "127.0.0.1");
        Object portObj  = config.get("port");
        int port        = (portObj instanceof Number) ? ((Number) portObj).intValue() : 3306;
        Object dbVal    = config.get("dbname") != null ? config.get("dbname") : config.get("database");
        String dbname   = String.valueOf(dbVal != null ? dbVal : "hospital");
        Object usrVal   = config.get("user") != null ? config.get("user") : config.get("username");
        String user     = String.valueOf(usrVal != null ? usrVal : "root");
        String password = String.valueOf(config.get("password") != null ? config.get("password") : "");

        if (!"mysql".equals(driver)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("status", true);
            result.put("action", "skipped_non_mysql");
            result.put("message", "Driver '" + driver + "' nao necessita de auto-provisionamento MySQL.");
            CACHED_STATUS.put(name, result);
            return result;
        }

        String timezone = config.get("timezone") != null ? String.valueOf(config.get("timezone")) : null;
        String serverUrl = DBConnection.mysqlUrl(host, port, "", timezone, true);

        List<String> actionsPerformed = new ArrayList<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("status", false);
            result.put("error", "ERR_JDBC_DRIVER_MISSING");
            result.put("message", "Driver JDBC do MySQL nao encontrado no classpath.");
            return result;
        }

        // 1 & 2. Conectar ao servidor MySQL e conferir/criar o schema
        try (Connection serverConn = DriverManager.getConnection(serverUrl, user, password)) {
            boolean dbExists = false;
            try (PreparedStatement ps = serverConn.prepareStatement(
                    "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?")) {
                ps.setString(1, dbname);
                try (ResultSet rs = ps.executeQuery()) {
                    dbExists = rs.next();
                }
            }

            if (!dbExists) {
                try (Statement stmt = serverConn.createStatement()) {
                    stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbname + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    actionsPerformed.add("created_database");
                }
            }
        } catch (Exception e) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("host", host);
            details.put("port", port);
            details.put("user", user);
            details.put("dbname", dbname);
            details.put("jdbc_error", e.getMessage());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("status", false);
            result.put("error", "ERR_DATABASE_SERVER_CONNECTION");
            result.put("message", "Nao foi possivel conectar ao servidor MySQL em '" + host + ":" + port + "' com o usuario '" + user + "'. Verifique se o MySQL esta rodando e as credenciais no config.json.");
            result.put("details", details);
            CACHED_STATUS.put(name, result);
            return result;
        }

        // 3 & 4. Conectar na base de dados e inspecionar as tabelas
        String dbUrl = DBConnection.mysqlUrl(host, port, dbname, timezone, true);

        // Conexao com multiplos comandos usada SO para o script e fechada no final; a
        // aplicacao abre conexoes proprias, sem allowMultiQueries.
        try (Connection dbConn = DriverManager.getConnection(dbUrl, user, password)) {
            List<String> existingTables = listTables(dbConn, dbname);
            List<String> missingTables = new ArrayList<>();
            for (String req : REQUIRED_TABLES) {
                if (!containsIgnoreCase(existingTables, req)) {
                    missingTables.add(req);
                }
            }

            if (!missingTables.isEmpty() || actionsPerformed.contains("created_database")) {
                String sqlContent = null;
                try {
                    sqlContent = Config.readResourceText("WEB-INF/etc/script.sql");
                } catch (Exception ignored) {
                }
                if (sqlContent != null && !sqlContent.isEmpty()) {
                    if (!"hospital".equals(dbname)) {
                        sqlContent = sqlContent.replace("`hospital`", "`" + dbname + "`");
                    }
                    try (Statement stmt = dbConn.createStatement()) {
                        stmt.execute(sqlContent);
                    }
                    actionsPerformed.add("executed_script_sql");
                    existingTables = listTables(dbConn, dbname);
                    missingTables.clear();
                    for (String req : REQUIRED_TABLES) {
                        if (!containsIgnoreCase(existingTables, req)) {
                            missingTables.add(req);
                        }
                    }
                }
            }


            String actionSummary = actionsPerformed.isEmpty() ? "database_ready" : String.join(", ", actionsPerformed);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("status", true);
            result.put("action", actionSummary);
            result.put("message", "Base de dados '" + dbname + "' pronta para operacao.");
            result.put("database", dbname);
            result.put("existingTables", existingTables);
            result.put("missingTables", missingTables);

            CACHED_STATUS.put(name, result);
            return result;

        } catch (Exception e) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("host", host);
            details.put("port", port);
            details.put("user", user);
            details.put("dbname", dbname);
            details.put("jdbc_error", e.getMessage());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("status", false);
            result.put("error", "ERR_DATABASE_PROVISIONING_FAILED");
            result.put("message", "Falha ao provisionar tabelas e dados iniciais no banco '" + dbname + "': " + e.getMessage());
            result.put("details", details);
            CACHED_STATUS.put(name, result);
            return result;
        }
    }

    private static List<String> listTables(Connection conn, String dbname) throws Exception {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?")) {
            ps.setString(1, dbname);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        }
        return list;
    }

    private static boolean containsIgnoreCase(List<String> list, String target) {
        for (String item : list) {
            if (item != null && item.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void clearCache() {
        CACHED_STATUS.clear();
    }

    public static Map<String, Object> ensureProvisioned() {
        return checkAndProvision(null, false);
    }

    public static Map<String, Object> ensureProvisioned(String connectionName, boolean forceRecheck) {
        return checkAndProvision(connectionName, forceRecheck);
    }

    public static void resetState() {
        clearCache();
    }
}
