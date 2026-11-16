package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import utils.CodeGenerator;
import utils.Config;
import utils.PasswordSecurity;

/**
 * ZTF_J2EE - Model RecuperacaoSenha
 *
 * Recuperacao de senha por token de uso unico com expiracao:
 * 1. solicitar(): gera um token aleatorio (CSPRNG), grava apenas o SHA-256 dele e invalida
 *    pedidos anteriores ainda pendentes do mesmo usuario.
 * 2. redefinir(): consome o token (uma unica vez, dentro do prazo) e troca a senha.
 *
 * A senha atual NUNCA e alterada no momento do pedido: quem nao tem acesso ao e-mail do
 * usuario nao consegue trocar a senha dele. Expiracao usa NOW() do banco para nao depender
 * do fuso horario da JVM. Operacoes com mais de uma escrita rodam em transacao numa
 * conexao exclusiva (DBConnection.openConnection()).
 */
public class PasswordRecovery {

    /** Dados necessarios para montar o e-mail do pedido. */
    public record ResetRequest(String token, String name, String email) {
    }

    private static final int TOKEN_BYTES = 32;
    private static final String TOKEN_PATTERN = "^[0-9a-f]{64}$";

    public static int ttlMinutes() {
        int ttl = Config.getInt("security.password_reset_ttl_minutes", 30);
        return ttl > 0 ? ttl : 30;
    }

    /**
     * Cria um pedido de recuperacao. Retorna null quando o e-mail nao pertence a um usuario
     * ativo; o controller deve responder da mesma forma nos dois casos (anti-enumeracao).
     */
    public ResetRequest request(String email, String requesterIp) throws SQLException {
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer userId = null;
                String name = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id_usuario, nome FROM usuarios WHERE email = ? AND status = 1")) {
                    ps.setString(1, email);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userId = rs.getInt("id_usuario");
                            name = rs.getString("nome");
                        }
                    }
                }
                if (userId == null) {
                    conn.rollback();
                    return null;
                }

                invalidatePending(conn, userId);

                String token = CodeGenerator.generateSecureToken(TOKEN_BYTES);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO recuperacoes_senha (id_usuario, token_hash, expira_em, ip_solicitante) "
                                + "VALUES (?, ?, NOW() + INTERVAL ? MINUTE, ?)")) {
                    ps.setInt(1, userId);
                    ps.setString(2, PasswordSecurity.sha256Hex(token));
                    ps.setInt(3, ttlMinutes());
                    ps.setString(4, truncate(requesterIp, 45));
                    ps.executeUpdate();
                }
                conn.commit();
                return new ResetRequest(token, name, email);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Indica se o token existe, nao foi usado e nao expirou (nao consome o token). */
    public boolean validToken(String token) throws SQLException {
        if (!validFormat(token)) {
            return false;
        }
        try (Connection conn = DBConnection.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM recuperacoes_senha WHERE token_hash = ? AND usado_em IS NULL AND expira_em > NOW()")) {
            ps.setString(1, PasswordSecurity.sha256Hex(token));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Consome o token e grava a nova senha. Retorna o id do usuario, ou 0 se o token for
     * invalido, ja usado, expirado ou se o usuario estiver inativo. A senha ja deve ter
     * passado pela politica.
     */
    public int reset(String token, String newPassword) throws SQLException {
        if (!validFormat(token)) {
            return 0;
        }
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                int userId = -1;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id_usuario FROM recuperacoes_senha "
                                + "WHERE token_hash = ? AND usado_em IS NULL AND expira_em > NOW() FOR UPDATE")) {
                    ps.setString(1, PasswordSecurity.sha256Hex(token));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userId = rs.getInt("id_usuario");
                        }
                    }
                }
                if (userId <= 0) {
                    conn.rollback();
                    return 0;
                }

                int updated;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE usuarios SET senha_hash = ?, primeiro_acesso = 0 WHERE id_usuario = ? AND status = 1")) {
                    ps.setString(1, PasswordSecurity.hash(newPassword));
                    ps.setInt(2, userId);
                    updated = ps.executeUpdate();
                }
                if (updated != 1) {
                    conn.rollback();
                    return 0;
                }

                invalidatePending(conn, userId);
                conn.commit();
                return userId;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Invalida pedidos pendentes do usuario (ex.: apos trocar a senha logado). */
    public static void invalidatePending(int userId) throws SQLException {
        try (Connection conn = DBConnection.openConnection()) {
            invalidatePending(conn, userId);
        }
    }

    private static void invalidatePending(Connection conn, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE recuperacoes_senha SET usado_em = NOW() WHERE id_usuario = ? AND usado_em IS NULL")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private static boolean validFormat(String token) {
        return token != null && token.matches(TOKEN_PATTERN);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
