package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.DBQuery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestDatabase;
import utils.PasswordSecurity;

/** Integracao com MySQL/MariaDB: fluxo completo de recuperacao de senha por token. */
class RecuperacaoSenhaIntegrationTest {

    private final RecuperacaoSenha recuperacao = new RecuperacaoSenha();
    private Map<String, Object> usuario;

    @BeforeAll
    static void exigirBanco() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel: testes de integracao ignorados");
    }

    @BeforeEach
    void criarUsuario() {
        usuario = TestDatabase.criarUsuarioFicticio("recuperacao");
    }

    @AfterEach
    void removerUsuario() {
        if (usuario != null) {
            TestDatabase.removerUsuario(usuario.get("id_usuario"));
        }
    }

    private String email() {
        return String.valueOf(usuario.get("email"));
    }

    @Test
    void emailDesconhecidoNaoGeraPedido() throws Exception {
        assertNull(recuperacao.solicitar("ninguem." + System.nanoTime() + "@hospital.local", "127.0.0.1"));
    }

    @Test
    void pedidoNaoAlteraASenhaAtual() throws Exception {
        assertNotNull(recuperacao.solicitar(email(), "127.0.0.1"));
        assertNotNull(new Usuario().autenticar(email(), String.valueOf(usuario.get("senha"))),
                "a senha antiga continua valendo ate o token ser usado");
    }

    @Test
    void bancoGuardaSomenteOHashDoToken() throws Exception {
        RecuperacaoSenha.Solicitacao pedido = recuperacao.solicitar(email(), "127.0.0.1");
        List<Map<String, Object>> linhas = new DBQuery("recuperacoes_senha", "token_hash")
                .addCondition("AND", "id_usuario", "=", usuario.get("id_usuario"))
                .select();
        assertEquals(1, linhas.size());
        String armazenado = String.valueOf(linhas.get(0).get("token_hash"));
        assertFalse(armazenado.equals(pedido.token()), "token em claro nao pode ser persistido");
        assertEquals(PasswordSecurity.sha256Hex(pedido.token()), armazenado);
    }

    @Test
    void tokenRedefineASenhaUmaUnicaVez() throws Exception {
        String token = recuperacao.solicitar(email(), "127.0.0.1").token();
        assertTrue(recuperacao.tokenValido(token));

        assertTrue(recuperacao.redefinir(token, "NovaSenha2026"));
        assertNotNull(new Usuario().autenticar(email(), "NovaSenha2026"));
        assertNull(new Usuario().autenticar(email(), String.valueOf(usuario.get("senha"))));

        assertFalse(recuperacao.tokenValido(token), "token consumido nao vale mais");
        assertFalse(recuperacao.redefinir(token, "OutraSenha2026"), "token nao pode ser reutilizado");
    }

    @Test
    void novoPedidoInvalidaOAnterior() throws Exception {
        String primeiro = recuperacao.solicitar(email(), "127.0.0.1").token();
        String segundo = recuperacao.solicitar(email(), "127.0.0.1").token();
        assertFalse(recuperacao.tokenValido(primeiro));
        assertTrue(recuperacao.tokenValido(segundo));
    }

    @Test
    void tokenExpiradoERecusado() throws Exception {
        String token = recuperacao.solicitar(email(), "127.0.0.1").token();
        try (var conn = database.DBConnection.openConnection();
             var ps = conn.prepareStatement(
                     "UPDATE recuperacoes_senha SET expira_em = NOW() - INTERVAL 1 MINUTE WHERE id_usuario = ?")) {
            ps.setObject(1, usuario.get("id_usuario"));
            ps.executeUpdate();
        }
        assertFalse(recuperacao.tokenValido(token));
        assertFalse(recuperacao.redefinir(token, "NovaSenha2026"));
    }

    @Test
    void usuarioInativoNaoRecebeToken() throws Exception {
        new Usuario().alternarStatus(((Number) usuario.get("id_usuario")).intValue(), 0);
        assertNull(recuperacao.solicitar(email(), "127.0.0.1"));
    }

    @Test
    void tokensForaDoFormatoSaoRecusadosSemConsultarOBanco() throws Exception {
        assertFalse(recuperacao.tokenValido("' OR '1'='1"));
        assertFalse(recuperacao.redefinir("' OR '1'='1", "NovaSenha2026"));
        assertFalse(recuperacao.tokenValido(null));
    }
}
