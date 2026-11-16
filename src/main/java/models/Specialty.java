package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

/**
 * ZTF_J2EE - Model Especialidade
 *
 * Cadastro de especialidades medicas (RF-PRO-04). Nome unico; exclusao so logica (ativo),
 * porque consultas antigas apontam para a especialidade. Inativa, ela some do agendamento
 * e do cadastro de medicos, mas o historico continua mostrando o nome.
 */
public class Specialty {

    /** Lista com quantos medicos ativos atendem e quantas consultas futuras existem. */
    public List<Map<String, Object>> list() {
        return Sql.list("SELECT e.id_especialidade, e.nome, e.descricao, e.ativo, "
                + "(SELECT COUNT(*) FROM medico_especialidades me JOIN profissionais p ON p.id_profissional = me.id_profissional "
                + " WHERE me.id_especialidade = e.id_especialidade AND p.ativo = 1) AS medicos, "
                + "(SELECT COUNT(*) FROM consultas c WHERE c.id_especialidade = e.id_especialidade "
                + " AND c.status = 'agendada' AND c.data_hora >= NOW()) AS consultas_futuras "
                + "FROM especialidades e ORDER BY e.ativo DESC, e.nome");
    }

    public List<Map<String, Object>> active() {
        return Sql.list("SELECT id_especialidade, nome FROM especialidades WHERE ativo = 1 ORDER BY nome");
    }

    public Map<String, Object> byId(int id) {
        return Sql.one("SELECT * FROM especialidades WHERE id_especialidade = ?", id);
    }

    /** Cria (id = 0) ou atualiza. Devolve os erros por campo; vazio quando gravou. */
    public Map<String, String> save(int id, Map<String, Object> data, int loggedInUserId, long[] generatedId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = text(data.get("nome"));
        String description = text(data.get("descricao"));
        if (name.length() < 3 || name.length() > 100) {
            errors.put("nome", "Nome entre 3 e 100 caracteres.");
        }
        if (description.length() > 255) {
            errors.put("descricao", "Descrição com no máximo 255 caracteres.");
        }
        if (errors.isEmpty() && Sql.number("SELECT COUNT(*) FROM especialidades WHERE nome = ? AND id_especialidade <> ?", name, id) > 0) {
            errors.put("nome", "Já existe uma especialidade com este nome.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        try (Connection conn = DBConnection.openConnection()) {
            if (id > 0) {
                Sql.execute(conn, "UPDATE especialidades SET nome = ?, descricao = ? WHERE id_especialidade = ?",
                        name, description.isEmpty() ? null : description, id);
                AuditLog.record(conn, loggedInUserId, "especialidade.atualizada", "especialidades", id, null, null, null);
                generatedId[0] = id;
            } else {
                long newForm = Sql.insert(conn, "INSERT INTO especialidades (nome, descricao) VALUES (?, ?)",
                        name, description.isEmpty() ? null : description);
                AuditLog.record(conn, loggedInUserId, "especialidade.criada", "especialidades", newForm, null, null, null);
                generatedId[0] = newForm;
            }
        } catch (SQLException e) {
            if (Sql.isDuplicate(e)) {
                errors.put("nome", "Já existe uma especialidade com este nome.");
                return errors;
            }
            throw new RuntimeException("Falha ao salvar especialidade", e);
        }
        return errors;
    }

    /**
     * Ativa/inativa. Inativar e bloqueado quando ha consultas futuras agendadas nela.
     * Devolve a mensagem de bloqueio, ou null quando gravou.
     */
    public String setActive(int id, boolean active) {
        if (!active) {
            long future = Sql.number("SELECT COUNT(*) FROM consultas WHERE id_especialidade = ? AND status = 'agendada' "
                    + "AND data_hora >= NOW()", id);
            if (future > 0) {
                return "Há " + future + (future == 1 ? " consulta agendada" : " consultas agendadas")
                        + " nesta especialidade. Remarque ou cancele antes de inativar.";
            }
        }
        Sql.execute("UPDATE especialidades SET ativo = ? WHERE id_especialidade = ?", active ? 1 : 0, id);
        return null;
    }

    private static String text(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
