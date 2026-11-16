package models;

import database.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ZTF_J2EE - Model VinculoClinico
 *
 * Regra de acesso a dados clinicos por vinculo profissional-paciente (Fase 2):
 *
 * RN-ACE-03 (decisao P2) - MEDICO: acessa o historico clinico de um paciente se tiver com ele
 *   ao menos uma consulta encaminhada (aguardando_atendimento), em andamento ou realizada.
 *
 * RN-ACE-04 (decisao P3) - ENFERMEIRO: acessa triagens e alergias de pacientes que estao na
 *   fila de triagem (aguardando ou em triagem) ou que ele mesmo ja triou. Nao le o prontuario.
 *
 * Os controllers clinicos (Fase 5) devem chamar estes metodos DEPOIS de carregar o registro
 * pelo ID recebido, para que trocar o ID na URL nao de acesso (RN-ACE-06).
 * Os metodos com Connection permitem usar a mesma transacao (e os testes com rollback).
 */
public final class VinculoClinico {

    private VinculoClinico() {
    }

    public static boolean medicoPodeAcessarPaciente(int idMedico, int idPaciente) {
        try (Connection conn = DBConnection.openConnection()) {
            return medicoPodeAcessarPaciente(conn, idMedico, idPaciente);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao verificar vinculo do medico", e);
        }
    }

    public static boolean medicoPodeAcessarPaciente(Connection conn, int idMedico, int idPaciente) throws SQLException {
        return existe(conn,
                "SELECT 1 FROM consultas WHERE id_medico = ? AND id_paciente = ? "
                        + "AND status IN ('aguardando_atendimento', 'em_atendimento', 'realizada') LIMIT 1",
                idMedico, idPaciente);
    }

    public static boolean enfermeiroPodeAcessarPaciente(int idEnfermeiro, int idPaciente) {
        try (Connection conn = DBConnection.openConnection()) {
            return enfermeiroPodeAcessarPaciente(conn, idEnfermeiro, idPaciente);
        } catch (SQLException e) {
            throw new RuntimeException("Falha ao verificar vinculo do enfermeiro", e);
        }
    }

    public static boolean enfermeiroPodeAcessarPaciente(Connection conn, int idEnfermeiro, int idPaciente) throws SQLException {
        return existe(conn,
                "SELECT 1 FROM consultas WHERE id_paciente = ? AND status IN ('aguardando_triagem', 'em_triagem') "
                        + "UNION ALL "
                        + "SELECT 1 FROM triagens t JOIN consultas c ON c.id_consulta = t.id_consulta "
                        + "WHERE c.id_paciente = ? AND t.id_enfermeiro = ? LIMIT 1",
                idPaciente, idPaciente, idEnfermeiro);
    }

    private static boolean existe(Connection conn, String sql, int... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setInt(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
