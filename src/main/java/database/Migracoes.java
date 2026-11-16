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
 * ZTF_J2EE - Migracoes
 *
 * Complementa o DatabaseProvisioner: o script.sql e a BASE (cria o que falta, de forma
 * idempotente), e as mudancas posteriores em tabelas existentes (ALTER TABLE, novos
 * indices, ajustes de dados) entram como arquivos numerados em WEB-INF/etc/migrations:
 *
 *   001_descricao.sql, 002_outra_mudanca.sql, ...
 *
 * Cada arquivo e executado UMA vez, em ordem, e registrado em schema_migracoes com um
 * checksum. Arquivo ja aplicado que foi editado gera aviso no log (nao e reaplicado):
 * corrija com uma nova migracao.
 *
 * Observacao: no MySQL, DDL faz commit implicito; por isso cada migracao deve ser pequena
 * e, se falhar, a aplicacao para na migracao com erro (as anteriores continuam aplicadas).
 */
public final class Migracoes {

    private static final Logger LOG = Logger.getLogger(Migracoes.class.getName());
    private static final Pattern NOME_VALIDO = Pattern.compile("^(\\d{3,})_[A-Za-z0-9_\\-]+\\.sql$");

    private Migracoes() {
    }

    /** Resultado: versoes aplicadas agora; erro preenchido se alguma falhou. */
    public record Resultado(List<String> aplicadas, String versaoComErro, String erro) {
        public boolean ok() {
            return erro == null;
        }
    }

    static final String DDL_CONTROLE = "CREATE TABLE IF NOT EXISTS `schema_migracoes` ("
            + "`versao` VARCHAR(20) NOT NULL PRIMARY KEY, "
            + "`arquivo` VARCHAR(200) NOT NULL, "
            + "`checksum` CHAR(64) NOT NULL, "
            + "`aplicada_em` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    /** Aplica as migracoes pendentes usando uma conexao com allowMultiQueries. */
    public static Resultado aplicarPendentes(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(DDL_CONTROLE);
        }
        Map<String, String> aplicadas = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT versao, checksum FROM schema_migracoes")) {
            while (rs.next()) {
                aplicadas.put(rs.getString(1), rs.getString(2));
            }
        }

        List<String> novas = new ArrayList<>();
        for (Map.Entry<String, File> e : listarArquivos().entrySet()) {
            String versao = e.getKey();
            File arquivo = e.getValue();
            String conteudo;
            try {
                conteudo = Files.readString(arquivo.toPath(), StandardCharsets.UTF_8);
            } catch (java.io.IOException io) {
                return new Resultado(novas, versao, "Nao foi possivel ler " + arquivo.getName());
            }
            String checksum = PasswordSecurity.sha256Hex(conteudo);

            if (aplicadas.containsKey(versao)) {
                if (!checksum.equals(aplicadas.get(versao))) {
                    LOG.warning("Migracao " + arquivo.getName() + " foi alterada depois de aplicada; "
                            + "crie uma nova migracao em vez de editar a antiga.");
                }
                continue;
            }

            try {
                if (temComandos(conteudo)) {
                    try (Statement st = conn.createStatement()) {
                        st.execute(conteudo);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO schema_migracoes (versao, arquivo, checksum) VALUES (?, ?, ?)")) {
                    ps.setString(1, versao);
                    ps.setString(2, arquivo.getName());
                    ps.setString(3, checksum);
                    ps.executeUpdate();
                }
                novas.add(versao);
                LOG.info("Migracao aplicada: " + arquivo.getName());
            } catch (SQLException ex) {
                return new Resultado(novas, versao, ex.getMessage());
            }
        }
        return new Resultado(novas, null, null);
    }

    /** Arquivos NNN_nome.sql da pasta de migracoes, ordenados pela versao numerica. */
    static Map<String, File> listarArquivos() {
        Map<String, File> ordenados = new TreeMap<>((a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
        File pasta = pastaMigracoes();
        File[] arquivos = pasta.listFiles();
        if (arquivos == null) {
            return ordenados;
        }
        for (File f : arquivos) {
            java.util.regex.Matcher m = NOME_VALIDO.matcher(f.getName());
            if (f.isFile() && m.matches()) {
                ordenados.put(String.valueOf(Long.parseLong(m.group(1))), f);
            }
        }
        return ordenados;
    }

    static File pastaMigracoes() {
        String configurada = Config.getString("paths.migrations", "WEB-INF/etc/migrations");
        File pasta = new File(configurada);
        return pasta.isAbsolute() ? pasta : new File(Config.getWebappRoot(), configurada);
    }

    /** Ignora arquivos so com comentarios (ex.: o marco 000_base.sql). */
    static boolean temComandos(String sql) {
        String semComentarios = sql.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*--.*$", "");
        return !semComentarios.trim().isEmpty();
    }
}
