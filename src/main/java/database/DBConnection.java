package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import utils.Config;

/**
 * ZTF_J2EE - DBConnection
 *
 * Gerenciador de conexoes JDBC multi-banco (MySQL, PostgreSQL, Oracle, IBM DB2, SQLite)
 * com pool Multiton por nome de conexao e retrocompatibilidade total com construtores J2EE.
 */
public class DBConnection {

    private static final Map<String, Connection> INSTANCES = new ConcurrentHashMap<>();

    private String host;
    private String port;
    private String schema;
    private String user;
    private String password;
    private Connection connection = null;

    public DBConnection(String host, String port, String schema, String user, String password) {
        this.host = host;
        this.port = port;
        this.schema = schema;
        this.user = user;
        this.password = password;
        this.doConnection();
    }

    public DBConnection() {
        String defaultConn = "mysql";
        Map<?, ?> cfg = null;
        try {
            defaultConn = Config.getString("database.default", "mysql");
            Object raw = Config.get("database.connections." + defaultConn);
            if (raw instanceof Map<?, ?>) {
                cfg = (Map<?, ?>) raw;
            }
        } catch (Exception ignored) {
        }

        if (cfg != null) {
            this.host = String.valueOf(cfg.get("host") != null ? cfg.get("host") : "localhost");
            Object p = cfg.get("port") != null ? cfg.get("port") : 3306;
            this.port = p instanceof Number ? String.valueOf(((Number) p).intValue()) : String.valueOf(p);
            this.schema = String.valueOf(cfg.get("dbname") != null ? cfg.get("dbname") : "ztframework");
            this.user = String.valueOf(cfg.get("user") != null ? cfg.get("user") : "root");
            this.password = String.valueOf(cfg.get("password") != null ? cfg.get("password") : "senha");
        } else {
            this.host = "localhost";
            this.port = "3306";
            this.schema = "ztframework";
            this.user = "root";
            this.password = "senha";
        }
        this.doConnection();
    }

    private void doConnection() {
        String params = "?useUnicode=true&characterEncoding=UTF-8&useTimezone=true&serverTimezone=UTC&allowMultiQueries=true";
        String url = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.schema + params;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(url, this.user, this.password);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized Connection getStaticConnection() {
        return getStaticConnection(null);
    }

    public static synchronized Connection getStaticConnection(String connectionName) {
        String defaultName = Config.getString("database.default", "mysql");
        String name = (connectionName != null && !connectionName.isEmpty()) ? connectionName : defaultName;

        Connection existing = INSTANCES.get(name);
        try {
            if (existing != null && !existing.isClosed() && existing.isValid(2)) {
                return existing;
            }
        } catch (SQLException ignored) {
        }

        Object rawCfg = Config.get("database.connections." + name);
        if (!(rawCfg instanceof Map<?, ?>)) {
            throw new RuntimeException("Configuracoes para a conexao de banco '" + name + "' nao encontradas no config.json.");
        }
        Map<?, ?> config = (Map<?, ?>) rawCfg;

        String driver   = String.valueOf(config.get("driver") != null ? config.get("driver") : "mysql").toLowerCase();
        String host     = String.valueOf(config.get("host") != null ? config.get("host") : "127.0.0.1");
        Object portObj  = config.get("port");
        int port        = (portObj instanceof Number)
                ? ((Number) portObj).intValue()
                : ("pgsql".equals(driver) ? 5432 : 3306);
        Object dbVal    = config.get("dbname") != null ? config.get("dbname") : config.get("database");
        String dbname   = String.valueOf(dbVal != null ? dbVal : "");
        Object usrVal   = config.get("user") != null ? config.get("user") : config.get("username");
        String user     = String.valueOf(usrVal != null ? usrVal : "root");
        String password = String.valueOf(config.get("password") != null ? config.get("password") : "");

        String jdbcUrl;
        switch (driver) {
            case "mysql":
                jdbcUrl = String.format(
                        "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&useTimezone=true&serverTimezone=UTC&allowMultiQueries=true",
                        host, port, dbname
                );
                break;
            case "pgsql":
            case "postgresql":
                jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbname);
                break;
            case "oci":
            case "oracle":
                jdbcUrl = String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, dbname);
                break;
            case "ibm":
            case "db2":
                jdbcUrl = String.format("jdbc:db2://%s:%d/%s", host, port, dbname);
                break;
            case "sqlite":
                jdbcUrl = "jdbc:sqlite:" + dbname;
                break;
            default:
                throw new RuntimeException("Driver de banco de dados '" + driver + "' nao suportado.");
        }

        try {
            switch (driver) {
                case "mysql":
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    break;
                case "pgsql":
                case "postgresql":
                    Class.forName("org.postgresql.Driver");
                    break;
                case "oci":
                case "oracle":
                    Class.forName("oracle.jdbc.OracleDriver");
                    break;
                case "ibm":
                case "db2":
                    Class.forName("com.ibm.db2.jcc.DB2Driver");
                    break;
                case "sqlite":
                    Class.forName("org.sqlite.JDBC");
                    break;
                default:
                    break;
            }
            Connection conn = "sqlite".equals(driver)
                    ? DriverManager.getConnection(jdbcUrl)
                    : DriverManager.getConnection(jdbcUrl, user, password);
            INSTANCES.put(name, conn);
            return conn;
        } catch (Exception e) {
            throw new RuntimeException("Falha na conexao com o banco de dados [" + name + "]: " + e.getMessage(), e);
        }
    }

    public static synchronized void setConnection(String name, Connection conn) {
        if (name != null && conn != null) {
            INSTANCES.put(name, conn);
        }
    }

    public static synchronized void closeAll() {
        for (Connection conn : INSTANCES.values()) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ignored) {
            }
        }
        INSTANCES.clear();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = (host == null || host.isEmpty()) ? "localhost" : host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = (port == null || port.isEmpty()) ? "3306" : port;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Connection getConnection() {
        if (this.connection != null) {
            return this.connection;
        }
        try {
            return getStaticConnection();
        } catch (Exception e) {
            return null;
        }
    }
}
