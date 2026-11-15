package controllers;

import database.DBConnection;
import database.DBQuery;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import utils.JsonResponse;

/**
 * ZTF_J2EE - ConsultaController
 *
 * Demonstra consulta SQL direta via JDBC vs consulta fluente via DBQuery com JOIN.
 */
@WebServlet(
    name = "ConsultaController",
    urlPatterns = {
        "/api/consulta-direta",
        "/api/consulta-sql",
        "/api/consulta-dbquery"
    }
)
public class ConsultaController extends Controller {

    private static final long serialVersionUID = 1L;

    public void consultaDireta(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> dados = new ArrayList<>();
        try {
            Connection conn = DBConnection.getStaticConnection();
            String sql = "SELECT id_usuario, nome, email, status, criado_em FROM usuarios ORDER BY id_usuario ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    dados.add(row);
                }
            }
            JsonResponse.success(response, dados, "Consulta direta SQL executada com sucesso.");
        } catch (Exception e) {
            JsonResponse.error(response, 500, "ERR_SQL_QUERY", "Erro ao executar consulta direta: " + e.getMessage());
        }
    }

    public void consultaDbQuery(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> dados = new DBQuery("usuarios u", "u.id_usuario, u.nome, u.email, u.status, u.criado_em, g.nome AS grupo")
                    .joinRaw("LEFT JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario")
                    .joinRaw("LEFT JOIN grupos g ON g.id_grupo = ug.id_grupo")
                    .orderBy("u.id_usuario", "ASC")
                    .select();

            JsonResponse.success(response, dados, "Consulta DBQuery com JOIN executada com sucesso.");
        } catch (Exception e) {
            JsonResponse.error(response, 500, "ERR_DBQUERY", "Erro ao executar consulta DBQuery: " + e.getMessage());
        }
    }
}
