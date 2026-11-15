package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZTF_J2EE - Model Profissional
 *
 * Consulta o profissional vinculado a um usuario (RN-PRO-03: no maximo um). O cadastro
 * completo de profissionais entra na Fase 5.
 */
public class Profissional {

    /**
     * Dados do profissional ATIVO vinculado ao usuario, com registro (CRM/COREN) quando houver;
     * null se o usuario nao for profissional ou estiver inativo.
     */
    public Map<String, Object> porUsuario(int idUsuario) {
        try (Connection conn = DBConnection.openConnection()) {
            return porUsuario(conn, idUsuario);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao consultar profissional do usuario", e);
        }
    }

    public Map<String, Object> porUsuario(Connection conn, int idUsuario) throws SQLException {
        String sql = "SELECT p.id_profissional, p.tipo, p.nome, p.cargo, "
                + "m.crm, m.crm_uf, e.coren, e.coren_uf "
                + "FROM profissionais p "
                + "LEFT JOIN medicos m ON m.id_profissional = p.id_profissional "
                + "LEFT JOIN enfermeiros e ON e.id_profissional = p.id_profissional "
                + "WHERE p.id_usuario = ? AND p.ativo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id_profissional", rs.getInt("id_profissional"));
                p.put("tipo", rs.getString("tipo"));
                p.put("nome", rs.getString("nome"));
                p.put("cargo", rs.getString("cargo"));
                if (rs.getString("crm") != null) {
                    p.put("registro", "CRM " + rs.getString("crm") + "/" + rs.getString("crm_uf"));
                } else if (rs.getString("coren") != null) {
                    p.put("registro", "COREN " + rs.getString("coren") + "/" + rs.getString("coren_uf"));
                }
                return p;
            }
        }
    }
}
