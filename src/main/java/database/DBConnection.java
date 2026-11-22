package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import utils.Config;

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
            this.schema = String.valueOf(cfg.get("dbname") != null ? cfg.get("dbname") : "hospital");
            this.user = String.valueOf(cfg.get("user") != null ? cfg.get("user") : "root");
            this.password = String.valueOf(cfg.get("password") != null ? cfg.get("password") : "senha");
        } else {
            this.host = "localhost";
            this.port = "3306";
            this.schema = "hospital";
            this.user = "root";
            this.password = "senha";
        }
        this.doConnection();
    }

    public static final String DEFAULT_TIMEZONE = "-03:00";

    public static String mysqlUrl(String host, int port, String dbname, String timezone, boolean allowMultiQueries) {
        String tz = (timezone == null || timezone.trim().isEmpty()) ? DEFAULT_TIMEZONE : timezone.trim();
        String encodedTz = java.net.URLEncoder.encode(tz, java.nio.charset.StandardCharsets.UTF_8);
        return "jdbc:mysql://" + host + ":" + port + "/" + (dbname == null ? "" : dbname)
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&connectionTimeZone=" + encodedTz + "&forceConnectionTimeZoneToSession=true"
                + (allowMultiQueries ? "&allowMultiQueries=true" : "");
    }

    private void doConnection() {
        String url = mysqlUrl(this.host, Integer.parseInt(this.port), this.schema,
                Config.getString("database.connections.mysql.timezone", DEFAULT_TIMEZONE), false);
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

        Connection conn = createConnection(name);
        INSTANCES.put(name, conn);
        return conn;
    }

    public static Connection openConnection() {
        return openConnection(null);
    }

    public static Connection openConnection(String connectionName) {
        String defaultName = Config.getString("database.default", "mysql");
        String name = (connectionName != null && !connectionName.isEmpty()) ? connectionName : defaultName;
        return createConnection(name);
    }

    private static Connection createConnection(String name) {
        Object rawCfg = Config.get("database.connections." + name);
        if (!(rawCfg instanceof Map<?, ?>)) {
            throw new RuntimeException("Database connection settings '" + name + "' not found in config.json.");
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
                jdbcUrl = mysqlUrl(host, port, dbname,
                        config.get("timezone") != null ? String.valueOf(config.get("timezone")) : null, false);
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
                throw new RuntimeException("Database driver '" + driver + "' is not supported.");
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
            return borrow(jdbcUrl, user, password, "sqlite".equals(driver));
        } catch (Exception e) {
            throw new RuntimeException("Database connection failed [" + name + "]: " + e.getMessage(), e);
        }
    }

    public static synchronized void setConnection(String name, Connection conn) {
        if (name != null && conn != null) {
            INSTANCES.put(name, conn);
        }
    }

    private static final int MAX_IDLE = 8;
    private static final long VALIDATE_AFTER_MS = 60_000;
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedDeque<Idle>> POOL =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record Idle(Connection connection, long since) {
    }

    private static Connection borrow(String jdbcUrl, String user, String password, boolean noAuth) throws SQLException {
        String key = jdbcUrl + "|" + user;
        java.util.concurrent.ConcurrentLinkedDeque<Idle> idle = POOL.computeIfAbsent(key, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        Idle candidate;
        while ((candidate = idle.pollFirst()) != null) {
            Connection physical = candidate.connection();
            boolean stale = System.currentTimeMillis() - candidate.since() > VALIDATE_AFTER_MS;
            if (!physical.isClosed() && (!stale || physical.isValid(2))) {
                return pooled(physical, idle);
            }
            closeQuietly(physical);
        }
        Connection physical = noAuth ? DriverManager.getConnection(jdbcUrl) : DriverManager.getConnection(jdbcUrl, user, password);
        return pooled(physical, idle);
    }

    private static Connection pooled(Connection physical, java.util.concurrent.ConcurrentLinkedDeque<Idle> idle) {
        boolean[] closed = {false};
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "close":
                            if (!closed[0]) {
                                closed[0] = true;
                                release(physical, idle);
                            }
                            return null;
                        case "isClosed":
                            return closed[0] || physical.isClosed();
                        case "unwrap":
                            return physical.unwrap((Class<?>) args[0]);
                        case "isWrapperFor":
                            return physical.isWrapperFor((Class<?>) args[0]);
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "Pooled[" + physical + "]";
                        default:
                            if (closed[0]) {
                                throw new SQLException("Connection is closed.");
                            }
                            try {
                                return method.invoke(physical, args);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                throw e.getCause();
                            }
                    }
                });
    }

    private static void release(Connection physical, java.util.concurrent.ConcurrentLinkedDeque<Idle> idle) {
        try {
            if (physical.isClosed()) {
                return;
            }
            if (!physical.getAutoCommit()) {
                physical.rollback();
                physical.setAutoCommit(true);
            }
            if (physical.isReadOnly()) {
                physical.setReadOnly(false);
            }
            physical.clearWarnings();
            if (idle.size() < MAX_IDLE) {
                idle.offerFirst(new Idle(physical, System.currentTimeMillis()));
                return;
            }
        } catch (SQLException e) {
            closeQuietly(physical);
            return;
        }
        closeQuietly(physical);
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
        }
    }

    public static synchronized void closeAll() {
        for (java.util.concurrent.ConcurrentLinkedDeque<Idle> idle : POOL.values()) {
            Idle i;
            while ((i = idle.pollFirst()) != null) {
                closeQuietly(i.connection());
            }
        }
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
