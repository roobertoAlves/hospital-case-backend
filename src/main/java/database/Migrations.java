package database;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import utils.Config;
import utils.PasswordSecurity;

public final class Migrations {

    private static final Logger LOG = Logger.getLogger(Migrations.class.getName());
    private static final Pattern VALID_NAME = Pattern.compile("^(\\d{3,})_[A-Za-z0-9_\\-]+\\.sql$");

    private Migrations() {
    }

    public record Result(List<String> applied, String versionWithError, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    static final String DDL_CONTROL = "CREATE TABLE IF NOT EXISTS `schema_migracoes` ("
            + "`versao` VARCHAR(20) NOT NULL PRIMARY KEY, "
            + "`arquivo` VARCHAR(200) NOT NULL, "
            + "`checksum` CHAR(64) NOT NULL, "
            + "`aplicada_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    public static Result applyPending(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(DDL_CONTROL);
        }
        Map<String, String> applied = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT versao, checksum FROM schema_migracoes")) {
            while (rs.next()) {
                applied.put(rs.getString(1), rs.getString(2));
            }
        }

        List<String> newValue = new ArrayList<>();
        for (Map.Entry<String, File> e : listFiles().entrySet()) {
            String version = e.getKey();
            File file = e.getValue();
            String content;
            try {
                content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (java.io.IOException io) {
                return new Result(newValue, version, "Nao foi possivel ler " + file.getName());
            }
            String normalized = content.replace("\r\n", "\n");
            String checksum = PasswordSecurity.sha256Hex(normalized);

            if (applied.containsKey(version)) {
                String recorded = applied.get(version);
                boolean equal = checksum.equals(recorded)
                        || PasswordSecurity.sha256Hex(normalized.replace("\n", "\r\n")).equals(recorded);
                if (!equal) {
                    LOG.warning("Migration " + file.getName() + " was changed after it was applied; "
                            + "create a new migration instead of editing the old one.");
                }
                continue;
            }

            try {
                if (hasCommands(content)) {
                    String sql = isMariaDb(conn) ? content : withoutAddColumnIfNotExists(content, (t, c) -> columnExists(conn, t, c));
                    try (Statement st = conn.createStatement()) {
                        st.execute(sql);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO schema_migracoes (versao, arquivo, checksum) VALUES (?, ?, ?)")) {
                    ps.setString(1, version);
                    ps.setString(2, file.getName());
                    ps.setString(3, checksum);
                    ps.executeUpdate();
                }
                newValue.add(version);
                LOG.info("Migration applied: " + file.getName());
            } catch (SQLException ex) {
                return new Result(newValue, version, ex.getMessage());
            }
        }
        return new Result(newValue, null, null);
    }

    static Map<String, File> listFiles() {
        Map<String, File> sorted = new TreeMap<>((a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
        File folder = migrationsFolder();
        File[] files = folder.listFiles();
        if (files == null) {
            return sorted;
        }
        for (File f : files) {
            java.util.regex.Matcher m = VALID_NAME.matcher(f.getName());
            if (f.isFile() && m.matches()) {
                sorted.put(String.valueOf(Long.parseLong(m.group(1))), f);
            }
        }
        return sorted;
    }

    static File migrationsFolder() {
        String configured = Config.getString("paths.migrations", "WEB-INF/etc/migrations");
        File folder = new File(configured);
        return folder.isAbsolute() ? folder : new File(Config.getWebappRoot(), configured);
    }

    private static final Pattern ALTER_ADD_IF_NOT_EXISTS =
            Pattern.compile("(?i)ALTER\\s+TABLE\\s+`?(\\w+)`?\\s+(?=ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\b)");
    private static final Pattern ADD_COLUMN_IF_NOT_EXISTS =
            Pattern.compile("(?is)\\s*ADD\\s+COLUMN\\s+IF\\s+NOT\\s+EXISTS\\s+`?(\\w+)`?(.*)");

    static boolean isMariaDb(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
            return rs.next() && rs.getString(1).toLowerCase().contains("mariadb");
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static String withoutAddColumnIfNotExists(String sql, java.util.function.BiPredicate<String, String> columnExists) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = ALTER_ADD_IF_NOT_EXISTS.matcher(sql);
        int pos = 0;
        while (m.find(pos)) {
            String table = m.group(1);
            int end = statementEnd(sql, m.end());
            List<String> kept = new ArrayList<>();
            for (String clause : splitTopLevel(sql.substring(m.end(), end))) {
                java.util.regex.Matcher c = ADD_COLUMN_IF_NOT_EXISTS.matcher(clause);
                if (!c.matches()) {
                    kept.add(clause.trim());
                } else if (!columnExists.test(table, c.group(1))) {
                    kept.add("ADD COLUMN `" + c.group(1) + "`" + c.group(2).stripTrailing());
                }
            }
            out.append(sql, pos, m.start());
            if (kept.isEmpty()) {
                end = Math.min(end + 1, sql.length());
            } else {
                out.append("ALTER TABLE `").append(table).append("`\n    ").append(String.join(",\n    ", kept));
            }
            pos = end;
        }
        return out.append(sql.substring(pos)).toString();
    }

    private static int statementEnd(String sql, int from) {
        int depth = 0;
        char quote = 0;
        for (int i = from; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ';' && depth == 0) {
                return i;
            }
        }
        return sql.length();
    }

    private static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
            } else if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    static boolean hasCommands(String sql) {
        String withoutComments = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*--.*$", "");
        return !withoutComments.trim().isEmpty();
    }
}
