package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import utils.Config;

/**
 * ZTF_J2EE - Model SegurancaLogin
 *
 * Controle de tentativas de login por usuario (RN-AUT-02), na tabela usuarios_seguranca:
 * apos security.login_max_failures falhas seguidas (padrao 5), o login fica bloqueado por
 * security.login_lock_minutes (padrao 15). O relogio usado e o do banco (NOW()).
 */
public final class SegurancaLogin {

    private SegurancaLogin() {
    }

    public static int maxFalhas() {
        int v = Config.getInt("security.login_max_failures", 5);
        return v > 0 ? v : 5;
    }

    public static int minutosBloqueio() {
        int v = Config.getInt("security.login_lock_minutes", 15);
        return v > 0 ? v : 15;
    }

    public static boolean estaBloqueado(int idUsuario) {
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM usuarios_seguranca WHERE id_usuario = ? AND bloqueado_ate > NOW()")) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao consultar bloqueio de login", e);
        }
    }

    /**
     * Conta uma falha. Ao atingir o limite, bloqueia e zera o contador.
     * @return true se esta falha bloqueou o usuario
     */
    public static boolean registrarFalha(int idUsuario) {
        String sql = "INSERT INTO usuarios_seguranca (id_usuario, falhas_consecutivas, ultima_falha_em) "
                + "VALUES (?, 1, NOW()) "
                + "ON DUPLICATE KEY UPDATE falhas_consecutivas = falhas_consecutivas + 1, ultima_falha_em = NOW()";
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, idUsuario);
                    ps.executeUpdate();
                }
                int bloqueou;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE usuarios_seguranca SET bloqueado_ate = NOW() + INTERVAL ? MINUTE, falhas_consecutivas = 0 "
                                + "WHERE id_usuario = ? AND falhas_consecutivas >= ?")) {
                    ps.setInt(1, minutosBloqueio());
                    ps.setInt(2, idUsuario);
                    ps.setInt(3, maxFalhas());
                    bloqueou = ps.executeUpdate();
                }
                conn.commit();
                return bloqueou > 0;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao registrar tentativa de login", e);
        }
    }

    /** Login bem-sucedido: zera falhas e remove bloqueio. */
    public static void registrarSucesso(int idUsuario) {
        executar("INSERT INTO usuarios_seguranca (id_usuario, falhas_consecutivas, ultimo_login_em) VALUES (?, 0, NOW()) "
                + "ON DUPLICATE KEY UPDATE falhas_consecutivas = 0, bloqueado_ate = NULL, ultimo_login_em = NOW()", idUsuario);
    }

    /** Desbloqueio manual pelo administrador ou apos redefinir a senha por e-mail. */
    public static void desbloquear(int idUsuario) {
        executar("UPDATE usuarios_seguranca SET falhas_consecutivas = 0, bloqueado_ate = NULL WHERE id_usuario = ?", idUsuario);
    }

    private static void executar(String sql, int idUsuario) {
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao atualizar seguranca de login", e);
        }
    }
}
