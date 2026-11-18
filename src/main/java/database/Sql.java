package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Sql {

    private Sql() {
    }

    public static List<Map<String, Object>> list(String sql, Object... params) {
        try (Connection conn = DBConnection.openConnection()) {
            return list(conn, sql, params);
        } catch (SQLException e) {
            throw new RuntimeException("Query failed", e);
        }
    }

    public static List<Map<String, Object>> list(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public static Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> rows = list(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static Map<String, Object> one(Connection conn, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = list(conn, sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static long number(String sql, Object... params) {
        Map<String, Object> row = one(sql, params);
        if (row == null || row.isEmpty()) {
            return 0L;
        }
        Object v = row.values().iterator().next();
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    public static int execute(String sql, Object... params) {
        try (Connection conn = DBConnection.openConnection()) {
            return execute(conn, sql, params);
        } catch (SQLException e) {
            throw new RuntimeException("Write failed", e);
        }
    }

    public static int execute(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public static long insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public static boolean isDuplicate(SQLException e) {
        return e instanceof java.sql.SQLIntegrityConstraintViolationException
                && e.getErrorCode() == 1062;
    }
}
