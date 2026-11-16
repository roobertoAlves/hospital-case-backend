package models;

import database.DBConnection;
import database.Sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import utils.Sanitize;
import utils.Validacao;

/**
 * ZTF_J2EE - Model Paciente
 *
 * Cadastro de pacientes (RF-PAC-01..06): busca por nome ou CPF com paginacao, criacao e
 * edicao com validacao no servidor, exclusao logica (ativo) e o historico administrativo
 * (consultas e exames agendados). Nenhum dado clinico sai deste model.
 */
public class Paciente {

    /** Campos editaveis do formulario, na ordem da tela. */
    public static final List<String> CAMPOS = List.of(
            "nome", "cpf", "data_nascimento", "sexo", "telefone", "email",
            "cep", "logradouro", "numero", "complemento", "bairro", "cidade", "uf",
            "contato_emergencia_nome", "contato_emergencia_telefone", "contato_emergencia_parentesco");

    private static final List<String> SEXOS = List.of("feminino", "masculino", "outro", "nao_informado");

    public record Pagina(List<Map<String, Object>> itens, long total, int pagina, int porPagina) {
        public int totalPaginas() {
            return (int) Math.max(1, (total + porPagina - 1) / porPagina);
        }
    }

    /** Resultado de criar/atualizar: o id gravado ou os erros por campo. */
    public record Resultado(long id, Map<String, String> erros) {
        public boolean ok() {
            return erros.isEmpty();
        }
    }

    public static LocalDate hoje() {
        return LocalDate.now(ZoneOffset.ofHours(-3));
    }

    /** Busca por nome (parte) ou CPF (digitos), com paginacao. Sem termo, lista por nome. */
    public Pagina buscar(String termo, boolean incluirInativos, int pagina, int porPagina) {
        String t = termo == null ? "" : termo.trim();
        String digitos = Validacao.digitos(t);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!incluirInativos) {
            where.append(" AND ativo = 1");
        }
        if (!t.isEmpty()) {
            if (!digitos.isEmpty() && digitos.length() == t.replaceAll("[.\\-\\s]", "").length()) {
                where.append(" AND cpf LIKE ?");
                params.add(digitos + "%");
            } else {
                where.append(" AND nome LIKE ?");
                params.add("%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
        }
        long total = Sql.numero("SELECT COUNT(*) FROM pacientes" + where, params.toArray());
        int p = Math.max(1, pagina);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(porPagina);
        pageParams.add((p - 1) * porPagina);
        List<Map<String, Object>> itens = Sql.lista(
                "SELECT id_paciente, nome, cpf, data_nascimento, telefone, ativo FROM pacientes" + where
                        + " ORDER BY nome LIMIT ? OFFSET ?", pageParams.toArray());
        return new Pagina(itens, total, p, porPagina);
    }

    public Map<String, Object> porId(int id) {
        return Sql.um("SELECT * FROM pacientes WHERE id_paciente = ?", id);
    }

    public Resultado criar(Map<String, Object> dados, int idUsuario) {
        Map<String, Object> limpos = new LinkedHashMap<>();
        Map<String, String> erros = validar(dados, limpos, 0);
        if (!erros.isEmpty()) {
            return new Resultado(0, erros);
        }
        try (Connection conn = DBConnection.openConnection()) {
            StringBuilder cols = new StringBuilder();
            StringBuilder marks = new StringBuilder();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> e : limpos.entrySet()) {
                cols.append(e.getKey()).append(", ");
                marks.append("?, ");
                params.add(e.getValue());
            }
            params.add(idUsuario);
            params.add(idUsuario);
            long id = Sql.inserir(conn, "INSERT INTO pacientes (" + cols + "criado_por, atualizado_por) VALUES ("
                    + marks + "?, ?)", params.toArray());
            return new Resultado(id, erros);
        } catch (SQLException e) {
            if (Sql.ehDuplicidade(e)) {
                erros.put("cpf", "Já existe um paciente com este CPF.");
                return new Resultado(0, erros);
            }
            throw new RuntimeException("Falha ao cadastrar paciente", e);
        }
    }

    public Resultado atualizar(int id, Map<String, Object> dados, int idUsuario) {
        Map<String, Object> limpos = new LinkedHashMap<>();
        Map<String, String> erros = validar(dados, limpos, id);
        if (!erros.isEmpty()) {
            return new Resultado(id, erros);
        }
        StringBuilder set = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : limpos.entrySet()) {
            set.append(e.getKey()).append(" = ?, ");
            params.add(e.getValue());
        }
        params.add(idUsuario);
        params.add(id);
        try (Connection conn = DBConnection.openConnection()) {
            Sql.executar(conn, "UPDATE pacientes SET " + set + "atualizado_por = ? WHERE id_paciente = ?", params.toArray());
            return new Resultado(id, erros);
        } catch (SQLException e) {
            if (Sql.ehDuplicidade(e)) {
                erros.put("cpf", "Já existe um paciente com este CPF.");
                return new Resultado(id, erros);
            }
            throw new RuntimeException("Falha ao atualizar paciente", e);
        }
    }

    public void definirAtivo(int id, boolean ativo, int idUsuario) {
        Sql.executar("UPDATE pacientes SET ativo = ?, atualizado_por = ? WHERE id_paciente = ?", ativo ? 1 : 0, idUsuario, id);
    }

    /**
     * Valida e normaliza os campos. Os nomes de coluna vem da lista fixa CAMPOS, nunca da
     * requisicao. Preenche "limpos" so com valores prontos para gravar.
     */
    Map<String, String> validar(Map<String, Object> dados, Map<String, Object> limpos, int idAtual) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (String campo : CAMPOS) {
            Object v = dados.get(campo);
            String s = v == null ? "" : Sanitize.string(v).trim();
            limpos.put(campo, s.isEmpty() ? null : s);
        }

        String nome = (String) limpos.get("nome");
        if (nome == null || nome.length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        } else if (nome.length() > 150) {
            erros.put("nome", "Nome com no máximo 150 caracteres.");
        }

        String cpf = Validacao.digitos((String) limpos.get("cpf"));
        if (!Validacao.cpfValido(cpf)) {
            erros.put("cpf", "CPF inválido.");
        } else {
            limpos.put("cpf", cpf);
            if (Sql.numero("SELECT COUNT(*) FROM pacientes WHERE cpf = ? AND id_paciente <> ?", cpf, idAtual) > 0) {
                erros.put("cpf", "Já existe um paciente com este CPF.");
            }
        }

        LocalDate nascimento = Validacao.data((String) limpos.get("data_nascimento"));
        if (!Validacao.nascimentoValido(nascimento, hoje())) {
            erros.put("data_nascimento", "Data de nascimento inválida.");
        } else {
            limpos.put("data_nascimento", nascimento);
        }

        String sexo = (String) limpos.get("sexo");
        if (sexo != null && !SEXOS.contains(sexo)) {
            erros.put("sexo", "Opção inválida.");
        }

        normalizarTelefone(limpos, "telefone", erros);
        normalizarTelefone(limpos, "contato_emergencia_telefone", erros);

        String email = (String) limpos.get("email");
        if (email != null && !Validacao.emailValido(email)) {
            erros.put("email", "E-mail inválido.");
        }
        String cep = (String) limpos.get("cep");
        if (cep != null) {
            if (!Validacao.cepValido(cep)) {
                erros.put("cep", "CEP com 8 dígitos.");
            } else {
                limpos.put("cep", Validacao.digitos(cep));
            }
        }
        String uf = (String) limpos.get("uf");
        if (uf != null) {
            if (!Validacao.ufValida(uf)) {
                erros.put("uf", "UF inválida.");
            } else {
                limpos.put("uf", uf.toUpperCase());
            }
        }
        return erros;
    }

    private static void normalizarTelefone(Map<String, Object> limpos, String campo, Map<String, String> erros) {
        String tel = (String) limpos.get(campo);
        if (tel == null) {
            return;
        }
        if (!Validacao.telefoneValido(tel)) {
            erros.put(campo, "Telefone com DDD (10 ou 11 dígitos).");
            return;
        }
        String d = Validacao.digitos(tel);
        limpos.put(campo, d.length() == 11
                ? "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7)
                : "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6));
    }

    /** Consultas e exames do paciente em ordem cronologica inversa (dados administrativos). */
    public List<Map<String, Object>> historicoAdministrativo(int idPaciente) {
        return Sql.lista(
                "SELECT 'consulta' AS tipo, c.id_consulta AS id, c.data_hora, c.status, "
                        + "CONCAT(e.nome, ' · ', p.nome) AS descricao, c.motivo_cancelamento "
                        + "FROM consultas c JOIN especialidades e ON e.id_especialidade = c.id_especialidade "
                        + "JOIN profissionais p ON p.id_profissional = c.id_medico WHERE c.id_paciente = ? "
                        + "UNION ALL "
                        + "SELECT 'exame', x.id_exame_agendado, x.data_hora, x.status, "
                        + "CONCAT(t.nome, ' · ', l.nome), x.motivo_cancelamento "
                        + "FROM exames_agendados x JOIN tipos_exame t ON t.id_tipo_exame = x.id_tipo_exame "
                        + "JOIN locais_exame l ON l.id_local = x.id_local WHERE x.id_paciente = ? "
                        + "ORDER BY data_hora DESC LIMIT 50", idPaciente, idPaciente);
    }
}
