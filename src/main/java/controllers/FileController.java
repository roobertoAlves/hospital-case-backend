package controllers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import database.DBConnection;
import database.DBQuery;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.Base64Files;
import utils.CodeGenerator;
import utils.Config;
import utils.JsonResponse;
import utils.RequestHelper;
import utils.Sanitize;

@WebServlet(name = "FileController", urlPatterns = {"/api/files", "/api/files/*"})
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 25 * 1024 * 1024,
    maxRequestSize = 50 * 1024 * 1024
)
public class FileController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final Gson GSON = new Gson();
    private final File storageUploadsDir;

    public FileController() {
        this.storageUploadsDir = Config.getStorageDir("uploads");
    }

    public void upload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        if (userId <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Autenticação necessária para realizar uploads.");
            return;
        }

        String base64Data   = RequestHelper.getParam(request, "file_base64", "");
        String originalName = Sanitize.string(RequestHelper.getParam(request, "nome_original", "arquivo_sem_nome"));
        if (originalName.isEmpty()) {
            originalName = "arquivo_sem_nome";
        }
        int publicCatalog         = Sanitize.intVal(RequestHelper.getParam(request, "public", "0"), 0);
        String groupsRaw    = RequestHelper.getParam(request, "allowed_groups", "");
        String usersRaw  = RequestHelper.getParam(request, "allowed_users", "");

        if (base64Data == null || base64Data.trim().isEmpty()) {
            JsonResponse.error(response, 400, "ERR_EMPTY_FILE", "Nenhum conteúdo Base64 foi fornecido.");
            return;
        }

        String mimeType = Base64Files.extractMimeType(base64Data);
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "application/octet-stream";
        }
        String cleanBase64 = Base64Files.stripDataUriPrefix(base64Data.trim());

        byte[] decodedBinary;
        try {
            decodedBinary = Base64.getMimeDecoder().decode(cleanBase64);
        } catch (IllegalArgumentException e) {
            JsonResponse.error(response, 400, "ERR_INVALID_BASE64", "Conteúdo Base64 corrompido ou inválido.");
            return;
        }

        if (decodedBinary == null) {
            JsonResponse.error(response, 400, "ERR_INVALID_BASE64", "Conteúdo Base64 corrompido ou inválido.");
            return;
        }

        String ext = Base64Files.getExtension(originalName);
        String hash = CodeGenerator.generateSecureToken(16);
        String storedFilename = hash + "." + ext + ".base64";
        File targetFile = new File(this.storageUploadsDir, storedFilename);

        try {
            Files.writeString(targetFile.toPath(), cleanBase64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            JsonResponse.error(response, 500, "ERR_STORAGE_WRITE", "Falha ao gravar arquivo no disco.");
            return;
        }

        String groupsJson = normalizeJsonArray(groupsRaw);
        String usersJson = normalizeJsonArray(usersRaw);

        Map<String, Object> insertData = new LinkedHashMap<>();
        insertData.put("hash", hash);
        insertData.put("nome_original", originalName);
        insertData.put("mime_type", mimeType);
        insertData.put("tamanho_bytes", decodedBinary.length);
        insertData.put("id_dono", userId);
        insertData.put("publico", publicCatalog);
        insertData.put("grupos_permitidos", groupsJson);
        insertData.put("usuarios_permitidos", usersJson);
        insertData.put("caminho_fisico", storedFilename);

        new DBQuery("arquivos_upload").insert(insertData);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hash", hash);
        data.put("nome_original", originalName);
        data.put("url", "api/files/" + hash);
        data.put("tamanho_bytes", decodedBinary.length);

        JsonResponse.success(response, data, "Upload em Base64 realizado com sucesso e regras de ACL gravadas!");
    }

    public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String hash = Sanitize.string(RequestHelper.getParam(request, "hash", ""));
        if (hash.isEmpty() && request.getRequestURI() != null) {
            String[] parts = request.getRequestURI().replaceAll("/+$", "").split("/");
            if (parts.length > 0) {
                hash = Sanitize.string(parts[parts.length - 1]);
            }
        }

        Map<String, Object> file = new DBQuery("arquivos_upload", "*")
                .addCondition("AND", "hash", "=", hash)
                .first();

        if (file == null) {
            JsonResponse.error(response, 404, "ERR_FILE_NOT_FOUND", "Arquivo não encontrado.");
            return;
        }

        boolean hasAccess = false;
        int userId = RequestHelper.getSessionUserId(request);
        List<String> groupsUser = RequestHelper.getSessionUserGroups(request);

        if (Sanitize.intVal(file.get("publico"), 0) == 1) {
            hasAccess = true;
        }

        if (userId > 0 && Sanitize.intVal(file.get("id_dono"), -1) == userId) {
            hasAccess = true;
        }

        if (groupsUser.contains("admin")) {
            hasAccess = true;
        }

        if (!hasAccess && userId > 0 && file.get("grupos_permitidos") != null) {
            String gpRaw = String.valueOf(file.get("grupos_permitidos"));
            if (!gpRaw.isEmpty()) {
                try {
                    Type type = new TypeToken<List<String>>() {}.getType();
                    List<String> groupsPerm = GSON.fromJson(gpRaw, type);
                    if (groupsPerm != null) {
                        for (String g : groupsPerm) {
                            if (groupsUser.contains(g)) {
                                hasAccess = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!hasAccess && userId > 0 && file.get("usuarios_permitidos") != null) {
            String upRaw = String.valueOf(file.get("usuarios_permitidos"));
            if (!upRaw.isEmpty()) {
                try {
                    Type type = new TypeToken<List<Object>>() {}.getType();
                    List<Object> usersPerm = GSON.fromJson(upRaw, type);
                    if (usersPerm != null) {
                        for (Object u : usersPerm) {
                            if (Sanitize.intVal(u, -1) == userId) {
                                hasAccess = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!hasAccess) {
            JsonResponse.error(response, 403, "ERR_ACCESS_DENIED", "Acesso negado: Você não possui permissão para visualizar este arquivo.");
            return;
        }

        String safeFileName = new File(String.valueOf(file.get("caminho_fisico"))).getName();
        File physicalFile = new File(this.storageUploadsDir, safeFileName);

        if (!physicalFile.isFile()) {
            JsonResponse.error(response, 404, "ERR_FILE_CORRUPT", "Arquivo físico ausente no storage.");
            return;
        }

        String base64 = Files.readString(physicalFile.toPath(), StandardCharsets.UTF_8).trim();
        byte[] binary = Base64.getMimeDecoder().decode(Base64Files.stripDataUriPrefix(base64));

        List<String> safeMimesInline = Arrays.asList(
                "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf", "text/plain"
        );
        String mimeType = String.valueOf(file.getOrDefault("mime_type", "application/octet-stream"));
        String disposition = safeMimesInline.contains(mimeType.toLowerCase()) ? "inline" : "attachment";
        String originalName = String.valueOf(file.getOrDefault("nome_original", "arquivo")).replace("\"", "");

        response.setContentType(mimeType);
        response.setContentLength(binary.length);
        response.setHeader("Content-Disposition", disposition + "; filename=\"" + originalName + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Cache-Control", "private, max-age=3600");

        response.getOutputStream().write(binary);
        response.getOutputStream().flush();
    }

    public void list(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int userId = RequestHelper.getSessionUserId(request);
        if (userId <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Autenticação necessária.");
            return;
        }

        boolean isAdmin = RequestHelper.getSessionUserGroups(request).contains("admin");
        List<Map<String, Object>> files = new ArrayList<>();

        try {
            Connection conn = DBConnection.getStaticConnection();
            String sql;
            if (isAdmin) {
                sql = "SELECT a.*, u.nome AS nome_dono, u.email AS email_dono "
                        + "FROM arquivos_upload a "
                        + "INNER JOIN usuarios u ON u.id_usuario = a.id_dono "
                        + "ORDER BY a.id_arquivo DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    files = readResultSet(rs);
                }
            } else {
                sql = "SELECT a.*, u.nome AS nome_dono, u.email AS email_dono "
                        + "FROM arquivos_upload a "
                        + "INNER JOIN usuarios u ON u.id_usuario = a.id_dono "
                        + "WHERE a.id_dono = ? OR a.publico = 1 "
                        + "ORDER BY a.id_arquivo DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        files = readResultSet(rs);
                    }
                }
            }
            JsonResponse.success(response, files, "Lista de arquivos recuperada com sucesso.");
        } catch (Exception e) {
            JsonResponse.serverError(response, "Erro ao listar arquivos", e);
        }
    }

    private List<Map<String, Object>> readResultSet(ResultSet rs) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    private String normalizeJsonArray(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equals("[]")) {
            return null;
        }
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        List<String> items = new ArrayList<>();
        for (String p : trimmed.split(",")) {
            if (!p.trim().isEmpty()) {
                items.add(p.trim());
            }
        }
        return items.isEmpty() ? null : GSON.toJson(items);
    }
}
