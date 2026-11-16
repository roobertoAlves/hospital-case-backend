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
public class Especialidade {

    /** Lista com quantos medicos ativos atendem e quantas consultas futuras existem. */
    public List<Map<String, Object>> listar() {
        return Sql.lista("SELECT e.id_especialidade, e.nome, e.descricao, e.ativo, "
                + "(SELECT COUNT(*) FROM medico_especialidades me JOIN profissionais p ON p.id_profissional = me.id_profissional "
                + " WHERE me.id_especialidade = e.id_especialidade AND p.ativo = 1) AS medicos, "
                + "(SELECT COUNT(*) FROM consultas c WHERE c.id_especialidade = e.id_especialidade "
                + " AND c.status = 'agendada' AND c.data_hora >= NOW()) AS consultas_futuras "
                + "FROM especialidades e ORDER BY e.ativo DESC, e.nome");
    }

    public List<Map<String, Object>> ativas() {
        return Sql.lista("SELECT id_especialidade, nome FROM especialidades WHERE ativo = 1 ORDER BY nome");
    }

    public Map<String, Object> porId(int id) {
        return Sql.um("SELECT * FROM especialidades WHERE id_especialidade = ?", id);
    }

    /** Cria (id = 0) ou atualiza. Devolve os erros por campo; vazio quando gravou. */
    public Map<String, String> salvar(int id, Map<String, Object> dados, int idUsuarioLogado, long[] idGerado) {
        Map<String, String> erros = new LinkedHashMap<>();
        String nome = texto(dados.get("nome"));
        String descricao = texto(dados.get("descricao"));
        if (nome.length() < 3 || nome.length() > 100) {
            erros.put("nome", "Nome entre 3 e 100 caracteres.");
        }
        if (descricao.length() > 255) {
            erros.put("descricao", "Descrição com no máximo 255 caracteres.");
        }
        if (erros.isEmpty() && Sql.numero("SELECT COUNT(*) FROM especialidades WHERE nome = ? AND id_especialidade <> ?", nome, id) > 0) {
            erros.put("nome", "Já existe uma especialidade com este nome.");
        }
        if (!erros.isEmpty()) {
            return erros;
        }
        try (Connection conn = DBConnection.openConnection()) {
            if (id > 0) {
                Sql.executar(conn, "UPDATE especialidades SET nome = ?, descricao = ? WHERE id_especialidade = ?",
                        nome, descricao.isEmpty() ? null : descricao, id);
                Auditoria.registrar(conn, idUsuarioLogado, "especialidade.atualizada", "especialidades", id, null, null, null);
                idGerado[0] = id;
            } else {
                long novo = Sql.inserir(conn, "INSERT INTO especialidades (nome, descricao) VALUES (?, ?)",
                        nome, descricao.isEmpty() ? null : descricao);
                Auditoria.registrar(conn, idUsuarioLogado, "especialidade.criada", "especialidades", novo, null, null, null);
                idGerado[0] = novo;
            }
        } catch (SQLException e) {
            if (Sql.ehDuplicidade(e)) {
                erros.put("nome", "Já existe uma especialidade com este nome.");
                return erros;
            }
            throw new RuntimeException("Falha ao salvar especialidade", e);
        }
        return erros;
    }

    /**
     * Ativa/inativa. Inativar e bloqueado quando ha consultas futuras agendadas nela.
     * Devolve a mensagem de bloqueio, ou null quando gravou.
     */
    public String definirAtivo(int id, boolean ativo) {
        if (!ativo) {
            long futuras = Sql.numero("SELECT COUNT(*) FROM consultas WHERE id_especialidade = ? AND status = 'agendada' "
                    + "AND data_hora >= NOW()", id);
            if (futuras > 0) {
                return "Há " + futuras + (futuras == 1 ? " consulta agendada" : " consultas agendadas")
                        + " nesta especialidade. Remarque ou cancele antes de inativar.";
            }
        }
        Sql.executar("UPDATE especialidades SET ativo = ? WHERE id_especialidade = ?", ativo ? 1 : 0, id);
        return null;
    }

    private static String texto(Object v) {
        return v == null ? "" : Sanitize.string(v).trim();
    }
}
