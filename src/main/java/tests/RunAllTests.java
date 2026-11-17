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
import models.User;
import utils.Base64Files;
import utils.CodeGenerator;
import utils.Config;
import utils.PasswordSecurity;
import utils.Router;
import utils.Sanitize;
import utils.Url;

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
            test8UserFullWorkflow();
            test9Url();

            System.out.println("\n=====================================================");
            System.out.println("   ALL TESTS PASSED (" + passedTests + "/" + totalTests + ")");
            System.out.println("=====================================================");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("\n=====================================================");
            System.err.println("   TEST RUN FAILED: " + e.getMessage());
            System.err.println("=====================================================");
            e.printStackTrace();
            System.exit(1);
        } finally {
            DBConnection.closeAll();
        }
    }

    private static void test1Config() {
        System.out.println(">>> 1. Running: configuration test (Config.java)");
        Config.reload();
        assertTrue(Config.has("app.name"), "Config must have the key 'app.name'");
        assertTrue("Zait Tiny Framework".equals(Config.getString("app.name", "")), "app.name must be 'Zait Tiny Framework'");
        assertTrue("ZTF_J2EE".equals(Config.getString("app.short_name", "")), "app.short_name must be 'ZTF_J2EE'");
        assertTrue(Config.getInt("security.bcrypt_cost", 0) == 12, "security.bcrypt_cost must be 12");

        Config.set("app.custom_test_key", "valor123");
        assertTrue("valor123".equals(Config.getString("app.custom_test_key", "")), "Config.set/get dinâmico em memória funcionando");
        Config.reload();
    }

    private static void test2PasswordSecurity() {
        System.out.println("\n>>> 2. Running: password security test (BCrypt + pepper)");
        String password = "MinhaSenhaSuperSecreta#2026_MaisDe72Bytes_ParaTestarPreHashHmacSha256_1234567890";
        String hash = PasswordSecurity.hash(password);
        assertTrue(hash.startsWith("$2y$12$"), "Generated hash must start with $2y$12$");
        assertTrue(PasswordSecurity.verify(password, hash), "PasswordSecurity.verify must accept the right password");
        assertTrue(!PasswordSecurity.verify(password + "X", hash), "PasswordSecurity.verify must reject a different password");
        assertTrue(!PasswordSecurity.needsRehash(hash), "A fresh cost-12 hash must not need a rehash");

        String hashAdmin = "$2y$12$mHCg.KJrd72V1YTb2khXTODFjLX2utPXwemKozrnXDPN5fd6CecAO";
        String managerHash = "$2y$12$fl3Onp5AwlrGIo/kqGFm6OXIyCB84NeQjPQ9cdvJ8cw2ybkRu.V12";
        String userHash = "$2y$12$.uDw0otVRL5/f20hEdBGIOg4QgIXDrIUDCdsP3gmCbDxOcDDU4mBK";
        String financeHash = "$2y$12$41K6E2NDJ3tcnqAiBR89SuNuTP/dhrS8.6h7PowWGVMMZWYUySvtm";
        String supportHash = "$2y$12$GAqfBYh1NWaQw8S13GaOzeXZaGvZjj7eLGhKV2m9EaedZDYiKukV6";

        assertTrue(PasswordSecurity.verify("admin123", hashAdmin), "Compatible with the script.sql seed hash: admin123");
        assertTrue(PasswordSecurity.verify("gerente123", managerHash), "Compatible with the script.sql seed hash: gerente123");
        assertTrue(PasswordSecurity.verify("usuario123", userHash), "Compatible with the script.sql seed hash: usuario123");
        assertTrue(PasswordSecurity.verify("financeiro123", financeHash), "Compatible with the script.sql seed hash: financeiro123");
        assertTrue(PasswordSecurity.verify("suporte123", supportHash), "Compatible with the script.sql seed hash: suporte123");
    }

    private static void test3CodeGeneratorAndSanitize() {
        System.out.println("\n>>> 3. Running: CodeGenerator and Sanitize test (anti-XSS)");
        String pwd = CodeGenerator.generatePassword(12);
        assertTrue(pwd.length() == 12, "Temporary password generated with 12 characters");
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
        System.out.println("\n>>> 4. Running: Base64Files test");
        File tempIn = File.createTempFile("ztf_test_in_", ".txt");
        File tempOut = File.createTempFile("ztf_test_out_", ".txt");
        try {
            String content = "Conteudo de teste ZTF_J2EE - UTF-8 áéíóú";
            Files.write(tempIn.toPath(), content.getBytes(StandardCharsets.UTF_8));

            String b64WithMime = Base64Files.fileToBase64(tempIn.getAbsolutePath(), true);
            assertTrue(b64WithMime.startsWith("data:"), "Base64Files.fileToBase64 inclui prefixo data URI");
            String mime = Base64Files.extractMimeType(b64WithMime);
            assertTrue(mime != null && !mime.isEmpty(), "Base64Files.extractMimeType extrai MIME válido: " + mime);

            int bytesWritten = Base64Files.base64ToFile(b64WithMime, tempOut.getAbsolutePath());
            assertTrue(bytesWritten > 0, "Base64Files.base64ToFile writes the bytes to disk");
            String read = new String(Files.readAllBytes(tempOut.toPath()), StandardCharsets.UTF_8);
            assertTrue(content.equals(read), "Conteúdo decodificado do Base64 é idêntico ao original");
        } finally {
            tempIn.delete();
            tempOut.delete();
        }
    }

    private static void test5DatabaseAndDbQuery() {
        System.out.println("\n>>> 5. Running: auto-provisioning, JDBC drivers and DBQuery test");
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
                "DatabaseProvisioner provisioned the database");

        List<Map<String, Object>> users = (new DBQuery("usuarios", "id_usuario, nome, email"))
                .orderBy("id_usuario", "ASC")
                .select();
        assertTrue(users.size() >= 5, "Tabela 'usuarios' possui os 5 usuários semente ou mais");

        Map<String, Object> admin = (new DBQuery("usuarios"))
                .addCondition("AND", "email", "=", "admin@hospital.local")
                .first();
        assertTrue(admin != null && admin.get("nome") != null && String.valueOf(admin.get("nome")).startsWith("Administrador"),
                "DBQuery.first() finds the administrator with a PreparedStatement");

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
        System.out.println("\n>>> 6. Running: Router and ACL guard test");
        Router router = new Router();
        Router.RouteMatch matched = router.matchRoute("/api/files/a1b2c3d4e5f60001a1b2c3d4e5f60001");
        assertTrue(matched != null, "Router matches the dynamic route /api/files/{hash}");
        Map<String, String> params = matched.params;
        assertTrue("a1b2c3d4e5f60001a1b2c3d4e5f60001".equals(params.get("hash")),
                "Router extrai parâmetro dinâmico {hash} corretamente");

        Map<String, Object> adminRoute = new HashMap<>();
        Map<String, Object> sessionRule = new HashMap<>();
        sessionRule.put("required", true);
        sessionRule.put("keys", Collections.singletonList("id_usuario"));
        adminRoute.put("session", sessionRule);
        adminRoute.put("groups", Collections.singletonList("admin"));

        Map<String, Object> aclNoSession = router.checkAccessControl(adminRoute, null);
        assertTrue(Boolean.FALSE.equals(aclNoSession.get("allowed")) && Integer.valueOf(401).equals(aclNoSession.get("statusCode")),
                "Router retorna 401 quando sessão obrigatória está ausente");

        Map<String, Object> commonUser = new HashMap<>();
        commonUser.put("id_usuario", 3);
        commonUser.put("grupos", Collections.singletonList("usuario"));
        Map<String, Object> aclForbidden = router.checkAccessControl(adminRoute, commonUser);
        assertTrue(Boolean.FALSE.equals(aclForbidden.get("allowed")) && Integer.valueOf(403).equals(aclForbidden.get("statusCode")),
                "Router returns 403 when the user is not in the required group");

        Map<String, Object> adminUser = new HashMap<>();
        adminUser.put("id_usuario", 1);
        adminUser.put("grupos", Arrays.asList("admin", "usuario"));
        @SuppressWarnings("unused")
		Map<String, Object> aclAllowed = router.checkAccessControl(adminRoute, adminUser);
        assertTrue(javax.servlet.http.HttpServlet.class.isAssignableFrom(controllers.Controller.class),
                "controllers.Controller herda de javax.servlet.http.HttpServlet");
        Class<?>[] ctrlClasses = {
            controllers.AuthController.class,
            controllers.DashboardController.class,
            controllers.ProfileController.class,
            controllers.AdminController.class,
            controllers.FileController.class,
            controllers.TicketController.class,
            controllers.QueryExampleController.class
        };
        for (Class<?> c : ctrlClasses) {
            assertTrue(controllers.Controller.class.isAssignableFrom(c),
                    c.getSimpleName() + " herda de controllers.Controller (HttpServlet)");
            assertTrue(c.isAnnotationPresent(javax.servlet.annotation.WebServlet.class),
                    c.getSimpleName() + " possui anotacao @WebServlet propria");
        }
    }

    private static void test7MailAndSms() {
        System.out.println("\n>>> 7. Running: Mail and Sms test (simulated in the log)");
        boolean mailOk = Mail.quickSend("teste@hospital.local", "Usuário Teste", "Assunto de Teste", "<p>Corpo HTML</p>");
        assertTrue(mailOk, "Mail.quickSend writes the simulated delivery to mail.log");

        boolean smsOk = Sms.send("+5511999999999", "Seu codigo OTP ZTF_J2EE: 123456");
        assertTrue(smsOk, "Sms.send writes the simulated delivery to sms.log");
    }

    private static void test8UserFullWorkflow() {
        System.out.println("\n>>> 8. Running: full user and RBAC flow test");
        User model = new User();

        Map<String, Object> authAdmin = model.authenticate("admin@hospital.local", "admin123");
        assertTrue(authAdmin != null, "admin@hospital.local signs in with 'admin123'");
        List<?> adminGroups = (authAdmin.get("grupos") instanceof List<?>) ? (List<?>) authAdmin.get("grupos") : Collections.emptyList();
        assertTrue(adminGroups.contains("admin"), "Sessão do admin contém o grupo 'admin'");

        String testEmail = "teste.automatizado.j2ee@hospital.local";
        (new DBQuery("usuarios")).addCondition("AND", "email", "=", testEmail).delete();

        Map<String, Object> created = model.createWithTemporaryPassword("Usuário J2EE Teste", testEmail, Collections.singletonList("usuario"));
        int createdId = ((Number) created.get("id_usuario")).intValue();
        String tempPassword = String.valueOf(created.get("temporaryPassword"));
        assertTrue(createdId > 0 && tempPassword.length() >= 8, "User created with a temporary password");

        Map<String, Object> authNew = model.authenticate(testEmail, tempPassword);
        assertTrue(authNew != null, "New user signs in with the generated temporary password");

        Map<String, Object> profileRes = model.updateProfile(createdId, "Usuário J2EE Editado", "NovaSenha#2026", null);
        assertTrue(Boolean.TRUE.equals(profileRes.get("status")), "Profile and password updated through User.updateProfile");

        Map<String, Object> authNewPassword = model.authenticate(testEmail, "NovaSenha#2026");
        assertTrue(authNewPassword != null && "Usuário J2EE Editado".equals(authNewPassword.get("nome")),
                "User signs in with the new password and updated name");

        int removed = (new DBQuery("usuarios")).addCondition("AND", "id_usuario", "=", createdId).delete();
        assertTrue(removed == 1, "Test user removed");
    }

    private static void test9Url() {
        System.out.println("\n>>> 9. Running: dynamic Url test (context path / host / port)");
        String currentFolder = new File(System.getProperty("user.dir", ".")).getAbsoluteFile().getName();
        assertTrue(("/" + currentFolder + "/login").equals(Url.to("login")),
                "Url.to('login') detecta dinamicamente a pasta atual do projeto: /" + currentFolder + "/login");

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
    }
}
