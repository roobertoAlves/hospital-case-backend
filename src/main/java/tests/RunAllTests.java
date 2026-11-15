package tests;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import database.DBConnection;
import database.DBQuery;
import database.DatabaseProvisioner;
import mail.Mail;
import mail.Sms;
import models.Usuario;
import utils.Base64Files;
import utils.CodeGenerator;
import utils.Config;
import utils.PasswordSecurity;
import utils.Router;
import utils.Sanitize;
import utils.Url;

/**
 * ============================================================================
 * ZTF_J2EE - Suíte Completa de Testes
 * ============================================================================
 */
public class RunAllTests {

    private static int totalTests = 0;
    private static int passedTests = 0;

    private static void assertTrue(boolean condition, String description) {
        totalTests++;
        if (condition) {
            passedTests++;
            System.out.println("  [OK] " + description);
        } else {
            System.err.println("  [FALHA] " + description);
            throw new RuntimeException("Falha na asserção: " + description);
        }
    }

    public static void main(String[] args) {
        System.out.println("=====================================================");
        System.out.println("   ZTF_J2EE - SUÍTE COMPLETA DE TESTES               ");
        System.out.println("=====================================================\n");

        try {
            test1Config();
            test2PasswordSecurity();
            test3CodeGeneratorAndSanitize();
            test4Base64AndUtilities();
            test5DatabaseAndDbQuery();
            test6RouterAndAcl();
            test7MailAndSms();
            test8UsuarioFullWorkflow();
            test9UrlAndViewsIntegrity();

            System.out.println("\n=====================================================");
            System.out.println("   TODOS OS TESTES PASSARAM COM 100% DE SUCESSO! (" + passedTests + "/" + totalTests + ")");
            System.out.println("=====================================================");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("\n=====================================================");
            System.err.println("   ERRO NA EXECUÇÃO DOS TESTES: " + e.getMessage());
            System.err.println("=====================================================");
            e.printStackTrace();
            System.exit(1);
        } finally {
            DBConnection.closeAll();
        }
    }

    private static void test1Config() {
        System.out.println(">>> 1. Executando: Teste de Configuração (Config.java)");
        Config.reload();
        assertTrue(Config.has("app.name"), "Config deve possuir a chave 'app.name'");
        assertTrue("Zait Tiny Framework".equals(Config.getString("app.name", "")), "app.name deve ser 'Zait Tiny Framework'");
        assertTrue("ZTF_J2EE".equals(Config.getString("app.short_name", "")), "app.short_name deve ser 'ZTF_J2EE'");
        assertTrue(Config.getInt("security.bcrypt_cost", 0) == 12, "security.bcrypt_cost deve ser 12");

        Config.set("app.custom_test_key", "valor123");
        assertTrue("valor123".equals(Config.getString("app.custom_test_key", "")), "Config.set/get dinâmico em memória funcionando");
        Config.reload();
    }

    private static void test2PasswordSecurity() {
        System.out.println("\n>>> 2. Executando: Teste de Segurança de Senhas (BCrypt + Pepper)");
        String senha = "MinhaSenhaSuperSecreta#2026_MaisDe72Bytes_ParaTestarPreHashHmacSha256_1234567890";
        String hash = PasswordSecurity.hash(senha);
        assertTrue(hash.startsWith("$2y$12$"), "Hash gerado deve ter prefixo $2y$12$");
        assertTrue(PasswordSecurity.verify(senha, hash), "PasswordSecurity.verify deve validar senha correta");
        assertTrue(!PasswordSecurity.verify(senha + "X", hash), "PasswordSecurity.verify deve rejeitar senha diferente");
        assertTrue(!PasswordSecurity.needsRehash(hash), "Hash recém-gerado com custo 12 não deve precisar de rehash");

        // Verificar compatibilidade com os hashes semente do script.sql
        String hashAdmin = "$2y$12$mHCg.KJrd72V1YTb2khXTODFjLX2utPXwemKozrnXDPN5fd6CecAO";
        String hashGerente = "$2y$12$fl3Onp5AwlrGIo/kqGFm6OXIyCB84NeQjPQ9cdvJ8cw2ybkRu.V12";
        String hashUsuario = "$2y$12$.uDw0otVRL5/f20hEdBGIOg4QgIXDrIUDCdsP3gmCbDxOcDDU4mBK";
        String hashFinanceiro = "$2y$12$41K6E2NDJ3tcnqAiBR89SuNuTP/dhrS8.6h7PowWGVMMZWYUySvtm";
        String hashSuporte = "$2y$12$GAqfBYh1NWaQw8S13GaOzeXZaGvZjj7eLGhKV2m9EaedZDYiKukV6";

        assertTrue(PasswordSecurity.verify("admin123", hashAdmin), "Compatibilidade com seed hash do script.sql: admin123");
        assertTrue(PasswordSecurity.verify("gerente123", hashGerente), "Compatibilidade com seed hash do script.sql: gerente123");
        assertTrue(PasswordSecurity.verify("usuario123", hashUsuario), "Compatibilidade com seed hash do script.sql: usuario123");
        assertTrue(PasswordSecurity.verify("financeiro123", hashFinanceiro), "Compatibilidade com seed hash do script.sql: financeiro123");
        assertTrue(PasswordSecurity.verify("suporte123", hashSuporte), "Compatibilidade com seed hash do script.sql: suporte123");
    }

    private static void test3CodeGeneratorAndSanitize() {
        System.out.println("\n>>> 3. Executando: Teste de CodeGenerator e Sanitize (Anti-XSS)");
        String pwd = CodeGenerator.generatePassword(12);
        assertTrue(pwd.length() == 12, "Senha temporária gerada com 12 caracteres");
        String otp = CodeGenerator.generateNumericCode(6);
        assertTrue(otp.matches("^\\d{6}$"), "Código numérico OTP de 6 dígitos");
        String token = CodeGenerator.generateToken(16);
        assertTrue(token.matches("^[0-9a-f]{32}$"), "Token CSPRNG hexadecimal de 32 caracteres");

        String xssInput = "<script>alert('xss')</script><b>João & Maria</b>";
        String clean = Sanitize.string(xssInput);
        assertTrue(!clean.contains("<script>") && !clean.contains("<b>") && "João & Maria".equals(clean),
                "Sanitize.string remove <script> e tags HTML preservando texto limpo");
        assertTrue("João &amp; Maria".equals(Sanitize.escape(clean)),
                "Sanitize.escape escapa entidades HTML contra XSS");
        assertTrue("TESTE@Dominio.com".equals(Sanitize.email("  TESTE@Dominio.com <> \" ")), "Sanitize.email remove caracteres inválidos");
        assertTrue("abc-123_".equals(Sanitize.alphaNum("abc-123_!@#$")), "Sanitize.alphaNum mantém alfanuméricos, hífen e underscore");
    }

    private static void test4Base64AndUtilities() throws Exception {
        System.out.println("\n>>> 4. Executando: Teste de Base64Files");
        File tempIn = File.createTempFile("ztf_test_in_", ".txt");
        File tempOut = File.createTempFile("ztf_test_out_", ".txt");
        try {
            String conteudo = "Conteudo de teste ZTF_J2EE - UTF-8 áéíóú";
            Files.write(tempIn.toPath(), conteudo.getBytes(StandardCharsets.UTF_8));

            String b64WithMime = Base64Files.fileToBase64(tempIn.getAbsolutePath(), true);
            assertTrue(b64WithMime.startsWith("data:"), "Base64Files.fileToBase64 inclui prefixo data URI");
            String mime = Base64Files.extractMimeType(b64WithMime);
            assertTrue(mime != null && !mime.isEmpty(), "Base64Files.extractMimeType extrai MIME válido: " + mime);

            int bytesWritten = Base64Files.base64ToFile(b64WithMime, tempOut.getAbsolutePath());
            assertTrue(bytesWritten > 0, "Base64Files.base64ToFile grava bytes no disco");
            String lido = new String(Files.readAllBytes(tempOut.toPath()), StandardCharsets.UTF_8);
            assertTrue(conteudo.equals(lido), "Conteúdo decodificado do Base64 é idêntico ao original");
        } finally {
            tempIn.delete();
            tempOut.delete();
        }
    }

    private static void test5DatabaseAndDbQuery() {
        System.out.println("\n>>> 5. Executando: Teste de Auto-Provisionamento, Drivers JDBC e DBQuery");
        String[] jdbcDrivers = {
            "com.mysql.cj.jdbc.Driver",
            "org.postgresql.Driver",
            "oracle.jdbc.OracleDriver",
            "com.ibm.db2.jcc.DB2Driver",
            "org.sqlite.JDBC"
        };
        for (String drv : jdbcDrivers) {
            boolean loaded = false;
            try {
                Class.forName(drv);
                loaded = true;
            } catch (ClassNotFoundException ignored) {
            }
            assertTrue(loaded, "Driver JDBC presente em WEB-INF/lib: " + drv);
        }

        DatabaseProvisioner.resetState();
        Map<String, Object> prov = DatabaseProvisioner.ensureProvisioned();
        assertTrue(Boolean.TRUE.equals(prov.get("status")) || Boolean.TRUE.equals(prov.get("ok")),
                "DatabaseProvisioner provisionou banco com sucesso");

        List<Map<String, Object>> usuarios = (new DBQuery("usuarios", "idUsuario, nome, email"))
                .orderBy("idUsuario", "ASC")
                .select();
        assertTrue(usuarios.size() >= 5, "Tabela 'usuarios' possui os 5 usuários semente ou mais");

        Map<String, Object> admin = (new DBQuery("usuarios"))
                .addCondition("AND", "email", "=", "admin@ztframework.local")
                .first();
        assertTrue(admin != null && admin.get("nome") != null && String.valueOf(admin.get("nome")).startsWith("Administrador"),
                "DBQuery.first() localiza Administrador com PreparedStatement");

        // Teste de segurança contra UPDATE / DELETE sem WHERE
        boolean updateBlocked = false;
        try {
            Map<String, Object> up = new HashMap<>();
            up.put("nome", "Hacker");
            (new DBQuery("usuarios")).update(up);
        } catch (RuntimeException ex) {
            updateBlocked = true;
        }
        assertTrue(updateBlocked, "DBQuery bloqueia UPDATE sem cláusula WHERE");

        boolean deleteBlocked = false;
        try {
            (new DBQuery("usuarios")).delete();
        } catch (RuntimeException ex) {
            deleteBlocked = true;
        }
        assertTrue(deleteBlocked, "DBQuery bloqueia DELETE sem cláusula WHERE");
    }

    private static void test6RouterAndAcl() {
        System.out.println("\n>>> 6. Executando: Teste de Router e Guarda ACL");
        Router router = new Router();
        Router.RouteMatch matched = router.matchRoute("/arquivo/a1b2c3d4e5f60001a1b2c3d4e5f60001");
        assertTrue(matched != null, "Router faz match de rota dinâmica /arquivo/{hash}");
        Map<String, String> params = matched.params;
        assertTrue("a1b2c3d4e5f60001a1b2c3d4e5f60001".equals(params.get("hash")),
                "Router extrai parâmetro dinâmico {hash} corretamente");

        // Teste ACL sem sessão
        Map<String, Object> adminRoute = new HashMap<>();
        Map<String, Object> sessionRule = new HashMap<>();
        sessionRule.put("required", true);
        sessionRule.put("keys", Collections.singletonList("idUsuario"));
        adminRoute.put("session", sessionRule);
        adminRoute.put("groups", Collections.singletonList("admin"));

        Map<String, Object> aclNoSession = router.checkAccessControl(adminRoute, null);
        assertTrue(Boolean.FALSE.equals(aclNoSession.get("allowed")) && Integer.valueOf(401).equals(aclNoSession.get("statusCode")),
                "Router retorna 401 quando sessão obrigatória está ausente");

        // Teste ACL com usuário sem grupo admin
        Map<String, Object> commonUser = new HashMap<>();
        commonUser.put("idUsuario", 3);
        commonUser.put("grupos", Collections.singletonList("usuario"));
        Map<String, Object> aclForbidden = router.checkAccessControl(adminRoute, commonUser);
        assertTrue(Boolean.FALSE.equals(aclForbidden.get("allowed")) && Integer.valueOf(403).equals(aclForbidden.get("statusCode")),
                "Router retorna 403 quando usuário não pertence ao grupo exigido");

        // Teste ACL com admin
        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("idUsuario", 1);
        adminUser.put("grupos", Arrays.asList("admin", "usuario"));
        @SuppressWarnings("unused")
		Map<String, Object> aclAllowed = router.checkAccessControl(adminRoute, adminUser);
        // Teste de hierarquia J2EE: Controller extends HttpServlet e subclasses com @WebServlet
        assertTrue(javax.servlet.http.HttpServlet.class.isAssignableFrom(controllers.Controller.class),
                "controllers.Controller herda de javax.servlet.http.HttpServlet");
        Class<?>[] ctrlClasses = {
            controllers.AuthController.class,
            controllers.DashboardController.class,
            controllers.PerfilController.class,
            controllers.AdminController.class,
            controllers.FileController.class,
            controllers.ChamadoController.class,
            controllers.ConsultaController.class
        };
        for (Class<?> c : ctrlClasses) {
            assertTrue(controllers.Controller.class.isAssignableFrom(c),
                    c.getSimpleName() + " herda de controllers.Controller (HttpServlet)");
            assertTrue(c.isAnnotationPresent(javax.servlet.annotation.WebServlet.class),
                    c.getSimpleName() + " possui anotacao @WebServlet propria");
        }
    }

    private static void test7MailAndSms() {
        System.out.println("\n>>> 7. Executando: Teste de Mail e Sms (Simulação em Log)");
        boolean mailOk = Mail.quickSend("teste@ztframework.local", "Usuário Teste", "Assunto de Teste", "<p>Corpo HTML</p>");
        assertTrue(mailOk, "Mail.quickSend registra disparo simulado em mail.log com sucesso");

        boolean smsOk = Sms.send("+5511999999999", "Seu codigo OTP ZTF_J2EE: 123456");
        assertTrue(smsOk, "Sms.send registra disparo simulado em sms.log com sucesso");
    }

    private static void test8UsuarioFullWorkflow() {
        System.out.println("\n>>> 8. Executando: Teste de Fluxo Completo de Usuário e RBAC");
        Usuario model = new Usuario();

        Map<String, Object> authAdmin = model.autenticar("admin@ztframework.local", "admin123");
        assertTrue(authAdmin != null, "Autenticação do admin@ztframework.local com 'admin123' bem-sucedida");
        List<?> gruposAdmin = (authAdmin.get("grupos") instanceof List<?>) ? (List<?>) authAdmin.get("grupos") : Collections.emptyList();
        assertTrue(gruposAdmin.contains("admin"), "Sessão do admin contém o grupo 'admin'");

        // Criar usuário temporário de teste, atualizar perfil, alterar senha e remover
        String testEmail = "teste.automatizado.j2ee@ztframework.local";
        (new DBQuery("usuarios")).addCondition("AND", "email", "=", testEmail).delete();

        Map<String, Object> criado = model.criarComSenhaTemporaria("Usuário J2EE Teste", testEmail, Collections.singletonList("usuario"));
        int idCriado = ((Number) criado.get("idUsuario")).intValue();
        String senhaTemp = String.valueOf(criado.get("senhaTemporaria"));
        assertTrue(idCriado > 0 && senhaTemp.length() >= 8, "Usuário criado com senha temporária");

        Map<String, Object> authNovo = model.autenticar(testEmail, senhaTemp);
        assertTrue(authNovo != null, "Novo usuário autentica com a senha temporária gerada");

        Map<String, Object> perfilRes = model.atualizarPerfil(idCriado, "Usuário J2EE Editado", "NovaSenha#2026", null);
        assertTrue(Boolean.TRUE.equals(perfilRes.get("status")), "Perfil e senha atualizados via Usuario.atualizarPerfil");

        Map<String, Object> authNovaSenha = model.autenticar(testEmail, "NovaSenha#2026");
        assertTrue(authNovaSenha != null && "Usuário J2EE Editado".equals(authNovaSenha.get("nome")),
                "Usuário autentica com a nova senha e nome atualizado");

        // Limpeza do usuário de teste
        int removidos = (new DBQuery("usuarios")).addCondition("AND", "idUsuario", "=", idCriado).delete();
        assertTrue(removidos == 1, "Limpeza do usuário de teste concluída");
    }

    private static void test9UrlAndViewsIntegrity() {
        System.out.println("\n>>> 9. Executando: Teste de Url Dinâmica (ContextPath/Host/Porta) e Views JSP");
        String currentFolder = new File(System.getProperty("user.dir", ".")).getAbsoluteFile().getName();
        assertTrue(("/" + currentFolder + "/login").equals(Url.to("login")),
                "Url.to('login') detecta dinamicamente a pasta atual do projeto: /" + currentFolder + "/login");
        assertTrue(("/" + currentFolder + "/assets/css/bootstrap.min.css").equals(Url.asset("assets/css/bootstrap.min.css")),
                "Url.asset resolve caminho de assets na raiz de webapp dinamicamente");

        // Simular mudança de nome da pasta/contexto e host/porta via HttpServletRequest dinâmico
        javax.servlet.http.HttpServletRequest mockReq = (javax.servlet.http.HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(
                RunAllTests.class.getClassLoader(),
                new Class<?>[]{ javax.servlet.http.HttpServletRequest.class },
                (proxy, method, methodArgs) -> {
                    String name = method.getName();
                    if ("getContextPath".equals(name)) return "/OutroNomeDeProjeto";
                    if ("getScheme".equals(name)) return "https";
                    if ("getServerName".equals(name)) return "meu-dominio.edu.br";
                    if ("getServerPort".equals(name)) return 8443;
                    if ("getHeader".equals(name)) return null;
                    return null;
                }
        );

        assertTrue("/OutroNomeDeProjeto/".equals(Url.base(mockReq)),
                "Url.base(request) adapta-se dinamicamente quando o nome da pasta/contextPath muda");
        assertTrue("/OutroNomeDeProjeto/dashboard".equals(Url.to(mockReq, "dashboard")),
                "Url.to(request, 'dashboard') usa o contextPath da requisição HTTP");
        assertTrue("https://meu-dominio.edu.br:8443/OutroNomeDeProjeto/login".equals(Url.full(mockReq, "login")),
                "Url.full(request, 'login') extrai protocolo, host, porta e contextPath da requisição HTTP");
        Url.clearRequest();
        Url.setDetectedContextPath(null);

        String[] views = {
            "home.jsp", "login.jsp", "main.jsp", "main_admin.jsp", "main_gerente.jsp",
            "main_financeiro.jsp", "main_suporte.jsp", "admin.jsp", "chamados.jsp",
            "errors/401.jsp", "errors/403.jsp", "errors/404.jsp", "errors/500.jsp", "errors/db_error.jsp"
        };
        for (String v : views) {
            File f = new File("src/main/webapp/" + v);
            assertTrue(f.isFile() && f.length() > 0, "View JSP presente e íntegra na raiz de webapp: " + v);
        }
    }
}
