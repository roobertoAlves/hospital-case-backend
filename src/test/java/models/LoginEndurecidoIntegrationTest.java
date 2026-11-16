package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.DBConnection;
import database.DBQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.FakeHttp;
import support.TestDatabase;

/** Fase 4: bloqueio por tentativas, troca de senha, recarga da sessao e auditoria. */
class LoginEndurecidoIntegrationTest {

    private Map<String, Object> usuario;
    private int id;
    private String email;
    private String senha;

    @BeforeAll
    static void exigirBanco() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
    }

    @BeforeEach
    void criar() {
        usuario = TestDatabase.criarUsuarioFicticio("login");
        id = ((Number) usuario.get("id_usuario")).intValue();
        email = String.valueOf(usuario.get("email"));
        senha = String.valueOf(usuario.get("senha"));
    }

    @AfterEach
    void remover() {
        TestDatabase.removerUsuario(id);
    }

    private Usuario.StatusLogin tentar(String s) {
        return new Usuario().autenticarDetalhado(email, s).status();
    }

    @Test
    void cincoFalhasBloqueiamMesmoComASenhaCorreta() {
        for (int i = 1; i <= SegurancaLogin.maxFalhas(); i++) {
            assertEquals(Usuario.StatusLogin.CREDENCIAIS_INVALIDAS, tentar("errada" + i));
            boolean bloqueou = SegurancaLogin.registrarFalha(id);
            assertEquals(i == SegurancaLogin.maxFalhas(), bloqueou, "bloqueia exatamente na falha " + SegurancaLogin.maxFalhas());
        }
        assertEquals(Usuario.StatusLogin.BLOQUEADO, tentar(senha));

        SegurancaLogin.desbloquear(id);
        assertEquals(Usuario.StatusLogin.SUCESSO, tentar(senha));
    }

    @Test
    void loginComSucessoZeraAsFalhas() {
        SegurancaLogin.registrarFalha(id);
        SegurancaLogin.registrarFalha(id);
        SegurancaLogin.registrarSucesso(id);
        for (int i = 1; i < SegurancaLogin.maxFalhas(); i++) {
            assertFalse(SegurancaLogin.registrarFalha(id), "contador deve ter recomecado do zero");
        }
    }

    @Test
    void emailInexistenteEUsuarioInativoTemOMesmoResultado() {
        Usuario.ResultadoLogin inexistente = new Usuario().autenticarDetalhado("ninguem." + System.nanoTime() + "@hospital.local", "x");
        assertEquals(Usuario.StatusLogin.CREDENCIAIS_INVALIDAS, inexistente.status());
        assertNull(inexistente.idUsuario());

        new Usuario().alternarStatus(id, 0);
        assertEquals(Usuario.StatusLogin.CREDENCIAIS_INVALIDAS, tentar(senha));
    }

    @Test
    void trocaDeSenhaExigeSenhaAtualEPoliticaEEncerraPrimeiroAcesso() throws Exception {
        assertEquals("Senha atual incorreta.", new Usuario().alterarSenha(id, "errada", "NovaSenha2026"));
        assertNotNull(new Usuario().alterarSenha(id, senha, "fraca"));
        // A senha temporaria (CodeGenerator, 10 caracteres com letras e digitos) cumpre a
        // politica; reutiliza-la deve ser recusado por ser igual a atual.
        assertEquals("A nova senha deve ser diferente da atual.", new Usuario().alterarSenha(id, senha, senha));

        String token = new RecuperacaoSenha().solicitar(email, "127.0.0.1").token();
        assertNull(new Usuario().alterarSenha(id, senha, "NovaSenha2026"));

        Map<String, Object> linha = new DBQuery("usuarios", "primeiro_acesso").addCondition("AND", "id_usuario", "=", id).first();
        assertEquals(0, ((Number) linha.get("primeiro_acesso")).intValue());
        assertEquals(Usuario.StatusLogin.SUCESSO, tentar("NovaSenha2026"));
        assertFalse(new RecuperacaoSenha().tokenValido(token), "troca de senha invalida pedidos de recuperacao pendentes");
    }

    @Test
    void sessaoComVersaoAntigaERecarregadaEUsuarioInativoPerdeASessao() throws Exception {
        Map<String, Object> dados = new Usuario().carregarSessao(id);
        assertEquals(Usuario.versaoAcl(), dados.get("acl_versao"));
        assertEquals(List.of("usuario"), dados.get("grupos"));

        // Admin adiciona o grupo "medico" e a ACL muda de versao
        new Usuario().sincronizarGruposUsuario(id, List.of(2, 8));
        Usuario.invalidarAcl();

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("user", dados);
        HttpSession sessao = FakeHttp.session(attrs);
        controllers.SessaoFilter.atualizarSessao(FakeHttp.request("GET", "/app/dashboard", null, null, sessao));
        @SuppressWarnings("unchecked")
        Map<String, Object> recarregado = (Map<String, Object>) attrs.get("user");
        assertTrue(((List<?>) recarregado.get("grupos")).contains("medico"), "grupos recarregados sem novo login");

        new Usuario().alternarStatus(id, 0);
        Usuario.invalidarAcl();
        controllers.SessaoFilter.atualizarSessao(FakeHttp.request("GET", "/app/dashboard", null, null, sessao));
        assertEquals(Boolean.TRUE, attrs.get("__invalidada__"), "usuario inativado perde a sessao na hora");
    }

    @Test
    void auditoriaGravaOperacaoSemInterromperEmCasoDeErro() throws Exception {
        // Transacao desfeita no final: a auditoria e somente-inclusao (sem DELETE), entao o
        // teste nao pode deixar linhas no banco de desenvolvimento.
        String acao = "teste.auditoria." + System.nanoTime();
        try (Connection conn = DBConnection.openConnection()) {
            conn.setAutoCommit(false);
            try {
                Auditoria.registrar(conn, id, acao, "usuarios", id, null, "detalhe de teste", "127.0.0.1");
                try (PreparedStatement ps = conn.prepareStatement("SELECT id_usuario, ip FROM auditoria WHERE acao = ?")) {
                    ps.setString(1, acao);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(id, rs.getInt(1));
                        assertEquals("127.0.0.1", rs.getString(2));
                    }
                }
                // acao nula viola NOT NULL: deve apenas registrar no log, sem lancar excecao
                Auditoria.registrar(conn, id, null, null, null, null, null, null);
            } finally {
                conn.rollback();
            }
        }
    }
}
