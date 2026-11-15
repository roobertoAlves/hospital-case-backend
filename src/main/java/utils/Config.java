package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletContext;

/**
 * ZTF_J2EE - Config
 *
 * Centraliza parametros de infraestrutura fora do codigo-fonte usando notacao de ponto (dot notation).
 * Suporta leitura direta via ServletContext (/WEB-INF/etc/config.json) sem depender de getRealPath,
 * alem de fallback para execucao standalone via CLI.
 */
public class Config {

    private static final String LOCAL_CONFIG_FILE = "config.local.json";

    private static Map<String, Object> data = null;
    private static String configPath = null;
    private static String webappRoot = null;
    private static ServletContext servletContext = null;

    public static synchronized void bindServletContext(ServletContext ctx) {
        if (ctx != null) {
            servletContext = ctx;
        }
    }

    public static synchronized ServletContext getServletContext() {
        return servletContext;
    }

    public static synchronized void setWebappRoot(String rootPath) {
        webappRoot = rootPath;
    }

    public static synchronized String getWebappRoot() {
        if (webappRoot != null && !webappRoot.isEmpty()) {
            return webappRoot;
        }
        String userDir = System.getProperty("user.dir", ".");
        File candidate = new File(userDir, "src/main/webapp");
        if (candidate.isDirectory()) {
            return candidate.getAbsolutePath();
        }
        return userDir;
    }

    public static synchronized void setConfigPath(String path) {
        configPath = path;
        data = null;
    }

    /**
     * Le um recurso de texto da aplicacao web (ex: "WEB-INF/etc/config.json")
     * priorizando ServletContext.getResourceAsStream() e fazendo fallback para o disco.
     */
    public static String readResourceText(String relativePath) throws IOException {
        String cleanRel = (relativePath == null ? "" : relativePath.replaceAll("^/+", ""));
        if (servletContext != null) {
            try (InputStream is = servletContext.getResourceAsStream("/" + cleanRel)) {
                if (is != null) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        File file = new File(getWebappRoot(), cleanRel);
        if (file.isFile()) {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        throw new IOException("Recurso nao encontrado: /" + cleanRel);
    }

    public static synchronized Map<String, Object> load() {
        if (data != null) {
            return data;
        }

        try {
            String content;
            if (configPath != null && !configPath.isEmpty()) {
                content = Files.readString(new File(configPath).toPath(), StandardCharsets.UTF_8);
            } else {
                content = readResourceText("WEB-INF/etc/config.json");
            }
            Gson gson = new Gson();
            Type type = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();
            Map<String, Object> decoded = gson.fromJson(content, type);
            if (decoded == null) {
                throw new RuntimeException("Erro ao decodificar config.json: conteudo vazio ou invalido.");
            }
            String localContent = readLocalOverride();
            if (localContent != null && !localContent.trim().isEmpty()) {
                Map<String, Object> local = gson.fromJson(localContent, type);
                if (local != null) {
                    deepMerge(decoded, local);
                }
            }
            data = decoded;
            return data;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar config.json: " + e.getMessage(), e);
        }
    }

    /**
     * Le o arquivo opcional config.local.json (fora do controle de versao), que sobrepoe
     * chaves do config.json com valores da maquina local (senha do banco, pepper, SMTP).
     * Retorna null quando o arquivo nao existe.
     */
    private static String readLocalOverride() throws IOException {
        if (configPath != null && !configPath.isEmpty()) {
            File local = new File(new File(configPath).getAbsoluteFile().getParentFile(), LOCAL_CONFIG_FILE);
            return local.isFile() ? Files.readString(local.toPath(), StandardCharsets.UTF_8) : null;
        }
        try {
            return readResourceText("WEB-INF/etc/" + LOCAL_CONFIG_FILE);
        } catch (IOException notFound) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void deepMerge(Map<String, Object> base, Map<String, Object> override) {
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object current = base.get(entry.getKey());
            Object incoming = entry.getValue();
            if (current instanceof Map<?, ?> && incoming instanceof Map<?, ?>) {
                deepMerge((Map<String, Object>) current, (Map<String, Object>) incoming);
            } else {
                base.put(entry.getKey(), incoming);
            }
        }
    }

    public static Object get() {
        return get(null, null);
    }

    public static Object get(String key) {
        return get(key, null);
    }

    public static synchronized Object get(String key, Object defaultValue) {
        Map<String, Object> config = load();
        if (key == null || key.isEmpty()) {
            return config;
        }

        String[] segments = key.split("\\.");
        Object current = config;

        for (String segment : segments) {
            if (!(current instanceof Map<?, ?>)) {
                return defaultValue;
            }
            Map<?, ?> map = (Map<?, ?>) current;
            if (!map.containsKey(segment)) {
                return defaultValue;
            }
            current = map.get(segment);
        }

        if (current instanceof Double) {
            Double d = (Double) current;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    return d.intValue();
                }
                return d.longValue();
            }
        }

        return current != null ? current : defaultValue;
    }

    public static String getString(String key, String defaultValue) {
        Object val = get(key, defaultValue);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        Object val = get(key, defaultValue);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        Object val = get(key, defaultValue);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue() != 0;
        }
        if (val != null) {
            String s = String.valueOf(val).trim().toLowerCase();
            if ("true".equals(s) || "1".equals(s)) return true;
            if ("false".equals(s) || "0".equals(s)) return false;
        }
        return defaultValue;
    }

    public static synchronized boolean has(String key) {
        Map<String, Object> config = load();
        String[] segments = key.split("\\.");
        Object current = config;

        for (String segment : segments) {
            if (!(current instanceof Map<?, ?>)) {
                return false;
            }
            Map<?, ?> map = (Map<?, ?>) current;
            if (!map.containsKey(segment)) {
                return false;
            }
            current = map.get(segment);
        }
        return true;
    }

    public static synchronized void set(String key, Object value) {
        load();
        String[] segments = key.split("\\.");
        Map<String, Object> current = data;

        for (int i = 0; i < segments.length - 1; i++) {
            String seg = segments[i];
            Object next = current.get(seg);
            Map<String, Object> child = new LinkedHashMap<>();
            if (next instanceof Map<?, ?>) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) next).entrySet()) {
                    if (e.getKey() != null) {
                        child.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            }
            current.put(seg, child);
            current = child;
        }
        current.put(segments[segments.length - 1], value);
    }

    public static synchronized void reset() {
        data = null;
        configPath = null;
    }

    public static synchronized Map<String, Object> reload() {
        data = null;
        return load();
    }

    public static String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(load());
    }
}
