package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import utils.Config;

/**
 * ZTF_J2EE - LoginSecurity model
 *
 * Per-user login attempt control (RN-AUT-02), in the usuarios_seguranca table: after
 * security.login_max_failures consecutive failures (default 5), login is locked for
 * security.login_lock_minutes (default 15). The database clock (NOW()) is used.
 */
public final class LoginSecurity {

    private LoginSecurity() {
    }

    public static int maxFailures() {
        int v = Config.getInt("security.login_max_failures", 5);
        return v > 0 ? v : 5;
    }

    public static int lockoutMinutes() {
        int v = Config.getInt("security.login_lock_minutes", 15);
        return v > 0 ? v : 15;
    }

    public static boolean isBlocked(int userId) {
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM usuarios_seguranca WHERE id_usuario = ? AND bloqueado_ate > NOW()")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read the login lockout", e);
        }
    }

    /**
     * Counts a failure. On reaching the limit, locks the account and resets the counter.
     * @return true if this failure locked the user
     */
    public static boolean recordFailure(int userId) {
        String sql = "INSERT INTO usuarios_seguranca (id_usuario, falhas_consecutivas, ultima_falha_em) "
                + "VALUES (?, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE falhas_consecutivas = falhas_consecutivas + 1, ultima_falha_em = NOW()";
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                int locked;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE usuarios_seguranca SET bloqueado_ate = NOW() + INTERVAL ? MINUTE, falhas_consecutivas = 0 "
                                + "WHERE id_usuario = ? AND falhas_consecutivas >= ?")) {
                    ps.setInt(1, lockoutMinutes());
                    ps.setInt(2, userId);
                    ps.setInt(3, maxFailures());
                    locked = ps.executeUpdate();
                }
                conn.commit();
                return locked > 0;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not record the login attempt", e);
        }
    }

    /** Successful login: resets failures and removes the lock. */
    public static void recordSuccess(int userId) {
        execute("INSERT INTO usuarios_seguranca (id_usuario, falhas_consecutivas, ultimo_login_em) VALUES (?, 0, NOW()) "
                + "ON DUPLICATE KEY UPDATE falhas_consecutivas = 0, bloqueado_ate = NULL, ultimo_login_em = NOW()", userId);
    }

    /** Manual unlock by the administrator, or after a password reset by e-mail. */
    public static void unlock(int userId) {
        execute("UPDATE usuarios_seguranca SET falhas_consecutivas = 0, bloqueado_ate = NULL WHERE id_usuario = ?", userId);
    }

    private static void execute(String sql, int userId) {
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update the login security state", e);
        }
    }
}
