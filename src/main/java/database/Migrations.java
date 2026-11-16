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

/**
 * ZTF_J2EE - Migrations
 *
 * Complements the DatabaseProvisioner: script.sql is the BASE (it creates whatever is missing,
 * idempotently), and later changes to existing tables (ALTER TABLE, new indexes, data fixes)
 * are numbered files in WEB-INF/etc/migrations:
 *
 *   001_description.sql, 002_another_change.sql, ...
 *
 * Each file runs ONCE, in order, and is recorded in schema_migracoes with a checksum. Editing
 * a file that was already applied logs a warning (it is not re-applied): fix it with a new
 * migration.
 *
 * Note: in MySQL, DDL commits implicitly; so each migration should be small and, if one fails,
 * the application stops at the failing migration (the previous ones stay applied).
 */
public final class Migrations {

    private static final Logger LOG = Logger.getLogger(Migrations.class.getName());
    private static final Pattern VALID_NAME = Pattern.compile("^(\\d{3,})_[A-Za-z0-9_\\-]+\\.sql$");

    private Migrations() {
    }

    /** Result: versions applied now; error is set if one of them failed. */
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

    /** Applies the pending migrations using a connection with allowMultiQueries. */
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
            // Git on Windows (core.autocrlf) turns LF into CRLF on checkout: the checksum uses the
            // text with normalized line breaks and accepts the recorded one in either format.
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
                    try (Statement st = conn.createStatement()) {
                        st.execute(content);
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

    /** NNN_name.sql files of the migrations folder, sorted by numeric version. */
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

    /** Skips files that only contain comments (e.g. the 000_base.sql marker). */
    static boolean hasCommands(String sql) {
        String withoutComments = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*--.*$", "");
        return !withoutComments.trim().isEmpty();
    }
}
