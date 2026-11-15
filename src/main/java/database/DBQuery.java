package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ZTF_J2EE - DBQuery
 *
 * Query Builder Fluente com Prepared Statements e travas contra UPDATE/DELETE sem WHERE.
 */
public class DBQuery {

    private String tableName = "";
    private String fields = "*";
    private final List<Condition> conditions = new ArrayList<>();
    private final List<String> joins = new ArrayList<>();
    private final List<Object> bindings = new ArrayList<>();
    private String orderByClause = "";
    private String limitClause = "";
    private Connection db = null;

    private static class Condition {
        final String type; // "basic" ou "raw"
        final String logical;
        final String column;
        final String operator;
        final String rawSql;

        Condition(String logical, String column, String operator) {
            this.type = "basic";
            this.logical = logical;
            this.column = column;
            this.operator = operator;
            this.rawSql = null;
        }

        Condition(String logical, String rawSql) {
            this.type = "raw";
            this.logical = logical;
            this.column = null;
            this.operator = null;
            this.rawSql = rawSql;
        }
    }

    // =========================================================================
    // CONSTRUTORES
    // =========================================================================

    public DBQuery(String table) {
        this(table, "*", (Connection) null);
    }

    public DBQuery(String table, String fields) {
        this(table, fields, (Connection) null);
    }

    public DBQuery(String table, String fields, Connection db) {
        this.tableName = table != null ? table.trim() : "";
        String cleanFields = (fields == null || fields.trim().isEmpty()) ? "*" : fields.trim();
        this.fields = cleanFields;
        this.db = db;
    }

    private Connection getDb() {
        if (this.db != null) {
            try {
                if (!this.db.isClosed()) {
                    return this.db;
                }
            } catch (SQLException ignored) {
            }
        }
        this.db = DBConnection.getStaticConnection();
        return this.db;
    }

    // =========================================================================
    // FLUENT QUERY BUILDER (ZTF_J2EE)
    // =========================================================================

    public DBQuery addCondition(String logical, String column, String operator, Object value) {
        String normLogical = (logical == null) ? "AND" : logical.trim().toUpperCase();
        if (!"AND".equals(normLogical) && !"OR".equals(normLogical)) {
            normLogical = "AND";
        }
        this.conditions.add(new Condition(normLogical, column.trim(), operator.trim()));
        this.bindings.add(value);
        return this;
    }

    public DBQuery addIn(String logical, String column, Object[] values) {
        return addIn(logical, column, values != null ? Arrays.asList(values) : new ArrayList<>());
    }

    public DBQuery addIn(String logical, String column, Collection<?> values) {
        String normLogical = (logical == null) ? "AND" : logical.trim().toUpperCase();
        if (!"AND".equals(normLogical) && !"OR".equals(normLogical)) {
            normLogical = "AND";
        }

        if (values == null || values.isEmpty()) {
            this.conditions.add(new Condition(normLogical, "1 = 0"));
            return this;
        }

        List<String> placeholders = new ArrayList<>();
        for (Object val : values) {
            placeholders.add("?");
            this.bindings.add(val);
        }

        String rawIn = column.trim() + " IN (" + String.join(", ", placeholders) + ")";
        this.conditions.add(new Condition(normLogical, rawIn));
        return this;
    }

    public DBQuery addJoin(String type, String tableOrAlias, String key, String operator, String foreignTable, String foreignKey) {
        String normType = (type == null) ? "INNER" : type.trim().toUpperCase();
        if (!Arrays.asList("INNER", "LEFT", "RIGHT", "FULL").contains(normType)) {
            normType = "INNER";
        }

        String[] parts = foreignTable.trim().split("(?i)\\s+(?:AS\\s+)?");
        String foreignAlias = parts.length > 0 ? parts[parts.length - 1] : foreignTable.trim();

        String joinSql = String.format(
                "%s JOIN %s ON %s.%s %s %s.%s",
                normType,
                foreignTable.trim(),
                tableOrAlias.trim(),
                key.trim(),
                operator.trim(),
                foreignAlias,
                foreignKey.trim()
        );
        this.joins.add(joinSql);
        return this;
    }

    public DBQuery joinRaw(String joinClause) {
        if (joinClause != null && !joinClause.trim().isEmpty()) {
            this.joins.add(joinClause.trim());
        }
        return this;
    }

    public DBQuery orderBy(String column) {
        return orderBy(column, "ASC");
    }

    public DBQuery orderBy(String column, String direction) {
        String dir = (direction != null && "DESC".equalsIgnoreCase(direction.trim())) ? "DESC" : "ASC";
        this.orderByClause = "ORDER BY " + column.trim() + " " + dir;
        return this;
    }

    public DBQuery limit(int limit) {
        return limit(limit, 0);
    }

    public DBQuery limit(int limit, int offset) {
        if (offset > 0) {
            this.limitClause = "LIMIT " + limit + " OFFSET " + offset;
        } else {
            this.limitClause = "LIMIT " + limit;
        }
        return this;
    }

    public String buildWhere() {
        if (this.conditions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("WHERE ");
        for (int i = 0; i < this.conditions.size(); i++) {
            Condition c = this.conditions.get(i);
            if (i > 0) {
                sb.append(" ").append(c.logical).append(" ");
            }
            if ("basic".equals(c.type)) {
                sb.append(c.column).append(" ").append(c.operator).append(" ?");
            } else {
                sb.append(c.rawSql);
            }
        }
        return sb.toString();
    }

    public String getSelectSql() {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(this.fields)
                .append(" FROM ")
                .append(this.tableName);

        if (!this.joins.isEmpty()) {
            sql.append(" ").append(String.join(" ", this.joins));
        }

        String where = buildWhere();
        if (!where.isEmpty()) {
            sql.append(" ").append(where);
        }

        if (!this.orderByClause.isEmpty()) {
            sql.append(" ").append(this.orderByClause);
        }

        if (!this.limitClause.isEmpty()) {
            sql.append(" ").append(this.limitClause);
        }

        return sql.toString();
    }

    public List<Object> getBindings() {
        return new ArrayList<>(this.bindings);
    }

    public List<Map<String, Object>> select() {
        String sql = getSelectSql();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = getDb().prepareStatement(sql)) {
            bindParameters(ps, this.bindings);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        String label = meta.getColumnLabel(i);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(i);
                        }
                        row.put(label, rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar SELECT em DBQuery: " + e.getMessage(), e);
        }
        return rows;
    }

    public List<Map<String, Object>> selectList() {
        return select();
    }

    public Map<String, Object> first() {
        String previousLimit = this.limitClause;
        this.limit(1);
        try {
            List<Map<String, Object>> rows = this.select();
            return rows.isEmpty() ? null : rows.get(0);
        } finally {
            this.limitClause = previousLimit;
        }
    }

    public long insert(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Dados vazios para insercao.");
        }

        List<String> columns = new ArrayList<>(data.keySet());
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (String col : columns) {
            placeholders.add("?");
            values.add(data.get(col));
        }

        String sql = String.format(
                "INSERT INTO %s (%s) VALUES (%s)",
                this.tableName,
                String.join(", ", columns),
                String.join(", ", placeholders)
        );

        try (PreparedStatement ps = getDb().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindParameters(ps, values);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return 0L;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar INSERT em DBQuery: " + e.getMessage(), e);
        }
    }

    public int update(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("Dados vazios para atualizacao.");
        }

        String where = buildWhere();
        if (where.isEmpty()) {
            throw new RuntimeException("UPDATE sem clausula WHERE nao e permitido por seguranca.");
        }

        List<String> setParts = new ArrayList<>();
        List<Object> allBindings = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            setParts.add(entry.getKey() + " = ?");
            allBindings.add(entry.getValue());
        }
        allBindings.addAll(this.bindings);

        String sql = String.format(
                "UPDATE %s SET %s %s",
                this.tableName,
                String.join(", ", setParts),
                where
        );

        try (PreparedStatement ps = getDb().prepareStatement(sql)) {
            bindParameters(ps, allBindings);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar UPDATE em DBQuery: " + e.getMessage(), e);
        }
    }

    public int delete() {
        String where = buildWhere();
        if (where.isEmpty()) {
            throw new RuntimeException("DELETE sem clausula WHERE nao e permitido por seguranca.");
        }

        String sql = String.format("DELETE FROM %s %s", this.tableName, where);
        try (PreparedStatement ps = getDb().prepareStatement(sql)) {
            bindParameters(ps, this.bindings);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar DELETE em DBQuery: " + e.getMessage(), e);
        }
    }

    private void bindParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }
}
