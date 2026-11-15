package utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ZTF_J2EE - Base64Files
 *
 * Manipula conversoes entre arquivos binarios no sistema de arquivos e payloads Base64 RFC 2045 / Data URIs.
 */
public class Base64Files {

    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:([a-zA-Z0-9/+_\\-.]+);base64,");
    private final File storageDir;

    public Base64Files() {
        this(null);
    }

    public Base64Files(String customStorageDir) {
        if (customStorageDir != null && !customStorageDir.isEmpty()) {
            this.storageDir = new File(customStorageDir);
        } else {
            String root = Config.getWebappRoot();
            String relStorage = Config.getString("paths.storage", "WEB-INF/storage");
            this.storageDir = new File(new File(root, relStorage), "uploads");
        }
        if (!this.storageDir.exists()) {
            this.storageDir.mkdirs();
        }
    }

    public File getStorageDir() {
        return this.storageDir;
    }

    public Map<String, Object> saveFromBase64(String base64Content, String originalName) {
        if (base64Content == null || base64Content.trim().isEmpty()) {
            throw new RuntimeException("Conteudo Base64 vazio.");
        }

        String rawB64 = stripDataUriPrefix(base64Content.trim());
        byte[] decodedBinary;
        try {
            decodedBinary = Base64.getMimeDecoder().decode(rawB64);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Conteudo Base64 invalido.", e);
        }

        if (decodedBinary == null || decodedBinary.length == 0) {
            throw new RuntimeException("Conteudo Base64 invalido.");
        }

        String hash = CodeGenerator.generateSecureToken(16);
        String ext = getExtension(originalName);
        String storedFilename = hash + "." + ext + ".base64";
        File targetFile = new File(this.storageDir, storedFilename);

        try {
            Files.writeString(targetFile.toPath(), rawB64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gravar arquivo em storage: " + e.getMessage(), e);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("hash", hash);
        meta.put("originalName", originalName);
        meta.put("extension", ext);
        meta.put("size", decodedBinary.length);
        meta.put("storedFile", storedFilename);
        meta.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return meta;
    }

    public byte[] readBinary(String storedFilename) {
        if (storedFilename == null || storedFilename.isEmpty()) {
            return null;
        }
        String safeName = new File(storedFilename).getName();
        File file = new File(this.storageDir, safeName);
        if (!file.isFile()) {
            return null;
        }
        try {
            String b64 = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            return Base64.getMimeDecoder().decode(stripDataUriPrefix(b64));
        } catch (Exception e) {
            return null;
        }
    }

    public static String fileToBase64(String filePath, boolean withMimePrefix) {
        File file = new File(filePath);
        if (!file.isFile()) {
            throw new RuntimeException("Arquivo nao encontrado: " + filePath);
        }
        try {
            byte[] binary = Files.readAllBytes(file.toPath());
            String b64 = Base64.getEncoder().encodeToString(binary);
            if (withMimePrefix) {
                String mime = Files.probeContentType(file.toPath());
                if (mime == null || mime.isEmpty()) {
                    mime = "application/octet-stream";
                }
                return "data:" + mime + ";base64," + b64;
            }
            return b64;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler arquivo: " + filePath, e);
        }
    }

    public static int base64ToFile(String base64String, String outputPath) {
        if (base64String == null || base64String.trim().isEmpty()) {
            throw new RuntimeException("Conteudo Base64 invalido.");
        }
        String clean = stripDataUriPrefix(base64String.trim());
        byte[] binary;
        try {
            binary = Base64.getMimeDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Conteudo Base64 invalido.", e);
        }
        try {
            File out = new File(outputPath);
            if (out.getParentFile() != null && !out.getParentFile().exists()) {
                out.getParentFile().mkdirs();
            }
            Files.write(out.toPath(), binary);
            return binary.length;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gravar arquivo em: " + outputPath, e);
        }
    }

    public static String extractMimeType(String base64DataUri) {
        if (base64DataUri == null) {
            return null;
        }
        Matcher m = DATA_URI_PATTERN.matcher(base64DataUri.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String stripDataUriPrefix(String base64DataUri) {
        if (base64DataUri == null) {
            return "";
        }
        Matcher m = DATA_URI_PATTERN.matcher(base64DataUri);
        if (m.find()) {
            return base64DataUri.substring(m.end());
        }
        return base64DataUri;
    }

    public static String getExtension(String filename) {
        if (filename == null) {
            return "bin";
        }
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            return filename.substring(dot + 1).toLowerCase();
        }
        return "bin";
    }
}
