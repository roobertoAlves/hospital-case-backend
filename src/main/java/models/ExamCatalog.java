package models;

import database.Sql;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;

public class ExamCatalog {

    public record Result(long id, Map<String, String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public List<Map<String, Object>> all() {
        return Sql.list("SELECT t.id_tipo_exame, t.codigo, t.nome, t.categoria, t.duracao_min, t.preparo, t.descricao, t.exige_pedido, "
                + "t.ativo, t.id_local, l.nome AS local, "
                + "(SELECT COUNT(*) FROM exames_agendados a WHERE a.id_tipo_exame = t.id_tipo_exame) AS agendamentos "
                + "FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local ORDER BY t.ativo DESC, t.categoria, t.nome");
    }

    public Map<String, Object> options() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("locations", Sql.list("SELECT id_local, nome FROM locais_exame WHERE ativo = 1 ORDER BY nome"));
        d.put("categories", Sql.list("SELECT categoria, COUNT(*) AS total FROM tipos_exame GROUP BY categoria ORDER BY categoria"));
        return d;
    }

    public Result save(int id, Map<String, Object> data, int userId) {
        Map<String, String> errors = new LinkedHashMap<>();
        String name = Sanitize.string(data.get("nome")).trim();
        String code = Sanitize.string(data.get("codigo")).trim().toUpperCase();
        String category = Sanitize.string(data.get("categoria")).trim();
        int location = Sanitize.intVal(data.get("id_local"), 0);
        int duration = Sanitize.intVal(data.get("duracao_min"), 0);
        String prep = Sanitize.string(data.get("preparo")).trim();
        String description = Sanitize.string(data.get("descricao")).trim();
        boolean order = List.of("1", "true", "on").contains(String.valueOf(data.get("exige_pedido")).toLowerCase());

        if (name.length() < 3 || name.length() > 150) {
            errors.put("nome", "Informe o nome do exame (3 a 150 caracteres).");
        } else if (Sql.number("SELECT COUNT(*) FROM tipos_exame WHERE nome = ? AND id_tipo_exame <> ?", name, id) > 0) {
            errors.put("nome", "Já existe um exame com este nome.");
        }
        if (!code.matches("^[A-Z0-9][A-Z0-9.\\-]{1,19}$")) {
            errors.put("codigo", "Código com 2 a 20 letras, números, ponto ou hífen (ex.: LAB-049).");
        } else if (Sql.number("SELECT COUNT(*) FROM tipos_exame WHERE codigo = ? AND id_tipo_exame <> ?", code, id) > 0) {
            errors.put("codigo", "Este código já pertence a outro exame.");
        }
        if (category.length() < 3 || category.length() > 40) {
            errors.put("categoria", "Informe a categoria.");
        }
        if (Sql.one("SELECT 1 FROM locais_exame WHERE id_local = ? AND ativo = 1", location) == null) {
            errors.put("id_local", "Escolha o local onde o exame é feito.");
        }
        if (duration < 5 || duration > 240) {
            errors.put("duracao_min", "Duração entre 5 e 240 minutos.");
        }
        if (prep.length() > 500) {
            errors.put("preparo", "Preparo com no máximo 500 caracteres.");
        }
        if (description.length() > 300) {
            errors.put("descricao", "Descrição com no máximo 300 caracteres.");
        }
        if (!errors.isEmpty()) {
            return new Result(0, errors);
        }
        Object prepValue = prep.isEmpty() ? null : prep;
        Object descriptionValue = description.isEmpty() ? null : description;
        long saved;
        if (id > 0) {
            Sql.execute("UPDATE tipos_exame SET nome = ?, codigo = ?, categoria = ?, id_local = ?, duracao_min = ?, preparo = ?, "
                    + "descricao = ?, exige_pedido = ? WHERE id_tipo_exame = ?", name, code, category, location, duration,
                    prepValue, descriptionValue, order ? 1 : 0, id);
            saved = id;
        } else {
            try (java.sql.Connection conn = database.DBConnection.openConnection()) {
                saved = Sql.insert(conn, "INSERT INTO tipos_exame (nome, codigo, categoria, id_local, duracao_min, preparo, descricao, exige_pedido) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", name, code, category, location, duration, prepValue, descriptionValue, order ? 1 : 0);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Could not create the exam type", e);
            }
        }
        AuditLog.record(userId, id > 0 ? "catalogo_exame.alterado" : "catalogo_exame.criado", "tipos_exame", saved, null, code, null);
        return new Result(saved, errors);
    }

    public boolean exists(int id) {
        return Sql.one("SELECT 1 FROM tipos_exame WHERE id_tipo_exame = ?", id) != null;
    }

    public void setActive(int id, boolean active, int userId) {
        Sql.execute("UPDATE tipos_exame SET ativo = ? WHERE id_tipo_exame = ?", active ? 1 : 0, id);
        AuditLog.record(userId, active ? "catalogo_exame.reativado" : "catalogo_exame.desativado", "tipos_exame", id, null, null, null);
    }
}
