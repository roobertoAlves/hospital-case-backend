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
 * Converts between binary files on disk and Base64 (RFC 2045) payloads / data URIs.
 */
public class Base64Files {

    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:([a-zA-Z0-9/+_\\-.]+);base64,");

    /** Image data URI accepted as a photo: only png, jpeg, gif and webp in plain Base64. */
    private static final Pattern SAFE_IMAGE_URI = Pattern.compile(
            "^data:image/(png|jpeg|gif|webp);base64,([A-Za-z0-9+/=\r\n]+)$");
    private final File storageDir;

    public Base64Files() {
        this(null);
    }

    public Base64Files(String customStorageDir) {
        if (customStorageDir != null && !customStorageDir.isEmpty()) {
            this.storageDir = new File(customStorageDir);
        } else {
            this.storageDir = Config.getStorageDir("uploads");
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
            throw new RuntimeException("Empty Base64 content.");
        }

        String rawB64 = stripDataUriPrefix(base64Content.trim());
        byte[] decodedBinary;
        try {
            decodedBinary = Base64.getMimeDecoder().decode(rawB64);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid Base64 content.", e);
        }

        if (decodedBinary == null || decodedBinary.length == 0) {
            throw new RuntimeException("Invalid Base64 content.");
        }

        String hash = CodeGenerator.generateSecureToken(16);
        String ext = getExtension(originalName);
        String storedFilename = hash + "." + ext + ".base64";
        File targetFile = new File(this.storageDir, storedFilename);

        try {
            Files.writeString(targetFile.toPath(), rawB64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write the file to storage: " + e.getMessage(), e);
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
            throw new RuntimeException("File not found: " + filePath);
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
            throw new RuntimeException("Could not read the file: " + filePath, e);
        }
    }

    public static int base64ToFile(String base64String, String outputPath) {
        if (base64String == null || base64String.trim().isEmpty()) {
            throw new RuntimeException("Invalid Base64 content.");
        }
        String clean = stripDataUriPrefix(base64String.trim());
        byte[] binary;
        try {
            binary = Base64.getMimeDecoder().decode(clean);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid Base64 content.", e);
        }
        try {
            File out = new File(outputPath);
            if (out.getParentFile() != null && !out.getParentFile().exists()) {
                out.getParentFile().mkdirs();
            }
            Files.write(out.toPath(), binary);
            return binary.length;
        } catch (IOException e) {
            throw new RuntimeException("Could not write the file to: " + outputPath, e);
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

    /**
     * Validates a photo sent as a data URI: allow-listed type, valid Base64, decoded size up to
     * maxBytes and a binary signature (magic bytes) matching the declared type. Blocks, for
     * example, data:image/x" onerror=... or an SVG with a script.
     */
    public static boolean isSafeImageDataUri(String dataUri, int maxBytes) {
        if (dataUri == null) {
            return false;
        }
        Matcher m = SAFE_IMAGE_URI.matcher(dataUri.trim());
        if (!m.matches()) {
            return false;
        }
        byte[] bin;
        try {
            bin = Base64.getMimeDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (bin.length == 0 || bin.length > maxBytes) {
            return false;
        }
        switch (m.group(1)) {
            case "png":
                return startsWith(bin, 0x89, 'P', 'N', 'G');
            case "jpeg":
                return startsWith(bin, 0xFF, 0xD8, 0xFF);
            case "gif":
                return startsWith(bin, 'G', 'I', 'F', '8');
            case "webp":
                return bin.length >= 12 && startsWith(bin, 'R', 'I', 'F', 'F')
                        && bin[8] == 'W' && bin[9] == 'E' && bin[10] == 'B' && bin[11] == 'P';
            default:
                return false;
        }
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((data[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
