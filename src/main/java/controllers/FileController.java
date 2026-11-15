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

/**
 * ZTF_J2EE - FileController
 *
 * Gerencia uploads de arquivos codificados em Base64 e controla o acesso seguro (ACL) em 5 camadas.
 */
@WebServlet(
    name = "FileController",
    urlPatterns = {
        "/api/upload",
        "/arquivo/*",
        "/api/arquivos/listar"
    }
)
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,      // 1 MB
    maxFileSize = 25 * 1024 * 1024,       // 25 MB
    maxRequestSize = 50 * 1024 * 1024     // 50 MB
)
public class FileController extends Controller {

    private static final long serialVersionUID = 1L;

    private static final Gson GSON = new Gson();
    private final File storageUploadsDir;

    public FileController() {
        String root = Config.getWebappRoot();
        String relStorage = Config.getString("paths.storage", "WEB-INF/storage");
        this.storageUploadsDir = new File(new File(root, relStorage), "uploads");
        if (!this.storageUploadsDir.exists()) {
            this.storageUploadsDir.mkdirs();
        }
    }

    public void upload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        if (idUsuario <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Autenticação necessária para realizar uploads.");
            return;
        }

        String base64Data   = RequestHelper.getParam(request, "arquivo_base64", "");
        String nomeOriginal = Sanitize.string(RequestHelper.getParam(request, "nome_original", "arquivo_sem_nome"));
        if (nomeOriginal.isEmpty()) {
            nomeOriginal = "arquivo_sem_nome";
        }
        int publico         = Sanitize.intVal(RequestHelper.getParam(request, "publico", "0"), 0);
        String gruposRaw    = RequestHelper.getParam(request, "grupos_permitidos", "");
        String usuariosRaw  = RequestHelper.getParam(request, "usuarios_permitidos", "");

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

        String ext = Base64Files.getExtension(nomeOriginal);
        String hash = CodeGenerator.generateSecureToken(16);
        String storedFilename = hash + "." + ext + ".base64";
        File targetFile = new File(this.storageUploadsDir, storedFilename);

        try {
            Files.writeString(targetFile.toPath(), cleanBase64, StandardCharsets.UTF_8);
        } catch (IOException e) {
            JsonResponse.error(response, 500, "ERR_STORAGE_WRITE", "Falha ao gravar arquivo no disco.");
            return;
        }

        String gruposJson = normalizeJsonArray(gruposRaw);
        String usuariosJson = normalizeJsonArray(usuariosRaw);

        Map<String, Object> insertData = new LinkedHashMap<>();
        insertData.put("hash", hash);
        insertData.put("nome_original", nomeOriginal);
        insertData.put("mime_type", mimeType);
        insertData.put("tamanho_bytes", decodedBinary.length);
        insertData.put("id_dono", idUsuario);
        insertData.put("publico", publico);
        insertData.put("grupos_permitidos", gruposJson);
        insertData.put("usuarios_permitidos", usuariosJson);
        insertData.put("caminho_fisico", storedFilename);

        new DBQuery("arquivos_upload").insert(insertData);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hash", hash);
        data.put("nome_original", nomeOriginal);
        data.put("url", "arquivo/" + hash);
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

        Map<String, Object> arquivo = new DBQuery("arquivos_upload", "*")
                .addCondition("AND", "hash", "=", hash)
                .first();

        if (arquivo == null) {
            JsonResponse.error(response, 404, "ERR_FILE_NOT_FOUND", "Arquivo não encontrado.");
            return;
        }

        boolean temAcesso = false;
        int idUsuario = RequestHelper.getSessionUserId(request);
        List<String> gruposUser = RequestHelper.getSessionUserGroups(request);

        if (Sanitize.intVal(arquivo.get("publico"), 0) == 1) {
            temAcesso = true;
        }

        if (idUsuario > 0 && Sanitize.intVal(arquivo.get("id_dono"), -1) == idUsuario) {
            temAcesso = true;
        }

        if (gruposUser.contains("admin")) {
            temAcesso = true;
        }

        if (!temAcesso && idUsuario > 0 && arquivo.get("grupos_permitidos") != null) {
            String gpRaw = String.valueOf(arquivo.get("grupos_permitidos"));
            if (!gpRaw.isEmpty()) {
                try {
                    Type type = new TypeToken<List<String>>() {}.getType();
                    List<String> gruposPerm = GSON.fromJson(gpRaw, type);
                    if (gruposPerm != null) {
                        for (String g : gruposPerm) {
                            if (gruposUser.contains(g)) {
                                temAcesso = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!temAcesso && idUsuario > 0 && arquivo.get("usuarios_permitidos") != null) {
            String upRaw = String.valueOf(arquivo.get("usuarios_permitidos"));
            if (!upRaw.isEmpty()) {
                try {
                    Type type = new TypeToken<List<Object>>() {}.getType();
                    List<Object> usersPerm = GSON.fromJson(upRaw, type);
                    if (usersPerm != null) {
                        for (Object u : usersPerm) {
                            if (Sanitize.intVal(u, -1) == idUsuario) {
                                temAcesso = true;
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!temAcesso) {
            JsonResponse.error(response, 403, "ERR_ACCESS_DENIED", "Acesso negado: Você não possui permissão para visualizar este arquivo.");
            return;
        }

        String safeFileName = new File(String.valueOf(arquivo.get("caminho_fisico"))).getName();
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
        String mimeType = String.valueOf(arquivo.getOrDefault("mime_type", "application/octet-stream"));
        String disposition = safeMimesInline.contains(mimeType.toLowerCase()) ? "inline" : "attachment";
        String nomeOriginal = String.valueOf(arquivo.getOrDefault("nome_original", "arquivo")).replace("\"", "");

        response.setContentType(mimeType);
        response.setContentLength(binary.length);
        response.setHeader("Content-Disposition", disposition + "; filename=\"" + nomeOriginal + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Cache-Control", "private, max-age=3600");

        response.getOutputStream().write(binary);
        response.getOutputStream().flush();
    }

    public void listar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int idUsuario = RequestHelper.getSessionUserId(request);
        if (idUsuario <= 0) {
            JsonResponse.error(response, 401, "ERR_UNAUTHORIZED", "Autenticação necessária.");
            return;
        }

        boolean isAdmin = RequestHelper.getSessionUserGroups(request).contains("admin");
        List<Map<String, Object>> arquivos = new ArrayList<>();

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
                    arquivos = readResultSet(rs);
                }
            } else {
                sql = "SELECT a.*, u.nome AS nome_dono, u.email AS email_dono "
                        + "FROM arquivos_upload a "
                        + "INNER JOIN usuarios u ON u.id_usuario = a.id_dono "
                        + "WHERE a.id_dono = ? OR a.publico = 1 "
                        + "ORDER BY a.id_arquivo DESC";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, idUsuario);
                    try (ResultSet rs = ps.executeQuery()) {
                        arquivos = readResultSet(rs);
                    }
                }
            }
            JsonResponse.success(response, arquivos, "Lista de arquivos recuperada com sucesso.");
        } catch (Exception e) {
            JsonResponse.error(response, 500, "ERR_DB_ERROR", "Erro ao listar arquivos: " + e.getMessage());
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
