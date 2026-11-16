package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.Sql;
import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import support.TestDatabase;

/** Portal do paciente num banco temporario: cadastro, vinculo por codigo, dados proprios, pedidos e vacinas. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalIntegrationTest {

    private static final int ANA_USUARIO = 60;
    private static final int ANA = 1;
    private static final int RECEPCAO = 30;
    private static String banco;
    private static long pedido;
    private static long exameComPedido;

    @BeforeAll
    static void preparar() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        banco = TestDatabase.usarBancoTemporario("hospital_portal");
    }

    @AfterAll
    static void apagar() throws Exception {
        if (banco != null) {
            for (Map<String, Object> p : Sql.lista("SELECT arquivo FROM pedidos_medicos")) {
                new File(utils.Config.getStorageDir("pedidos"), String.valueOf(p.get("arquivo"))).delete();
            }
            TestDatabase.descartarBancoTemporario(banco);
        }
    }

    private static Map<String, Object> cadastro(String cpf, String email) {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", "Paula Portal Ficticia");
        d.put("cpf", cpf);
        d.put("data_nascimento", "1990-04-10");
        d.put("email", email);
        d.put("telefone", "11977776666");
        d.put("senha", "Senha123forte");
        d.put("confirmar_senha", "Senha123forte");
        d.put("aceite", "1");
        return d;
    }

    private static LocalDate diaUtil(int semanas) {
        LocalDate d = Agenda.agora().toLocalDate().plusWeeks(semanas);
        while (d.getDayOfWeek() != DayOfWeek.TUESDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    @Order(1)
    void cadastroDeCpfNovoCriaFichaEContaLigadas() {
        Map<String, Object> semAceite = cadastro("52998224725", "x@exemplo.test");
        semAceite.put("aceite", "0");
        assertTrue(new Portal().cadastrar(semAceite).resultado().erros().containsKey("aceite"));
        Map<String, Object> fraca = cadastro("52998224725", "x@exemplo.test");
        fraca.put("senha", "123");
        fraca.put("confirmar_senha", "123");
        assertTrue(new Portal().cadastrar(fraca).resultado().erros().containsKey("senha"));

        String cpfNovo = "48152637017";
        Portal.Cadastro c = new Portal().cadastrar(cadastro(cpfNovo, "paula.portal@exemplo.test"));
        assertTrue(c.resultado().ok(), () -> String.valueOf(c.resultado().erros()));
        assertNull(c.codigo(), "CPF novo nao precisa de codigo");
        int idUsuario = (int) c.resultado().id();
        Integer pac = Portal.pacienteDoUsuario(idUsuario);
        assertNotNull(pac);
        assertEquals("paciente", Sql.um("SELECT g.nome FROM usuarios_grupos ug JOIN grupos g ON g.id_grupo = ug.id_grupo WHERE ug.id_usuario = ?",
                idUsuario).get("nome"));
        assertTrue(new Portal().cadastrar(cadastro(cpfNovo, "outra@exemplo.test")).resultado().erros().containsKey("cpf"), "CPF ja com conta");
    }

    @Test
    @Order(2)
    void fichaExistenteSoLigaComOCodigoDoEmailDaFicha() {
        // Bruno (2) nao tem e-mail na ficha: so a recepcao libera.
        String cpfBruno = String.valueOf(Sql.um("SELECT cpf FROM pacientes WHERE id_paciente = 2").get("cpf"));
        assertTrue(new Portal().cadastrar(cadastro(cpfBruno, "bruno.portal@exemplo.test")).resultado().erros().get("cpf").contains("recepção"));

        // Elaine (5) tem e-mail: cria a conta e manda o codigo para o e-mail DA FICHA.
        String cpfElaine = String.valueOf(Sql.um("SELECT cpf FROM pacientes WHERE id_paciente = 5").get("cpf"));
        Portal.Cadastro c = new Portal().cadastrar(cadastro(cpfElaine, "elaine.nova@exemplo.test"));
        assertTrue(c.resultado().ok());
        assertEquals("elaine.ficticia@exemplo.test", c.emailFicha());
        int idUsuario = (int) c.resultado().id();
        assertNull(Portal.pacienteDoUsuario(idUsuario), "ainda nao ligada");
        assertEquals(c.codigo().length(), 6);
        assertFalse(Sql.um("SELECT codigo_hash FROM vinculos_portal WHERE id_usuario = ?", idUsuario).get("codigo_hash").equals(c.codigo()),
                "so o hash do codigo e guardado");

        String errado = c.codigo().equals("000000") ? "111111" : "000000";
        assertFalse(new Portal().vincular(idUsuario, errado).ok());
        assertEquals(1, ((Number) Sql.um("SELECT tentativas FROM vinculos_portal WHERE id_usuario = ?", idUsuario).get("tentativas")).intValue());
        assertTrue(new Portal().vincular(idUsuario, c.codigo()).ok());
        assertEquals(5, Portal.pacienteDoUsuario(idUsuario));
        assertFalse(new Portal().vincular(idUsuario, c.codigo()).ok(), "codigo de uso unico");
    }

    @Test
    @Order(3)
    void pacienteSoMexeNosProprioRegistros() {
        Portal p = new Portal();
        LocalDateTime terca = diaUtil(5).atTime(9, 0);
        Portal.Resultado r = p.agendarConsulta(ANA, 3, 1, terca, ANA_USUARIO);
        assertTrue(r.ok(), r.erro());
        assertTrue(Portal.consultaDoPaciente((int) r.id(), ANA));
        assertFalse(Portal.consultaDoPaciente((int) r.id(), 5), "outra paciente nao enxerga");
        assertFalse(p.confirmarConsulta((int) r.id(), 5, ANA_USUARIO).ok(), "confirmar a consulta de outra pessoa falha");
        assertTrue(p.confirmarConsulta((int) r.id(), ANA, ANA_USUARIO).ok());
        assertFalse(p.confirmarConsulta((int) r.id(), ANA, ANA_USUARIO).ok(), "ja confirmada");
        assertEquals("portal", Sql.um("SELECT origem FROM consultas WHERE id_consulta = ?", r.id()).get("origem"));
        assertNull(new Receita().paraImpressaoDoPaciente(
                ((Number) Sql.um("SELECT r.id_receita FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta WHERE c.id_paciente <> 1 LIMIT 1")
                        .get("id_receita")).intValue(), ANA), "receita de outra pessoa nao abre");
    }

    @Test
    @Order(4)
    void exameQueExigePedidoSoComAnexoOuSolicitacao() {
        Portal p = new Portal();
        LocalDateTime dia = diaUtil(6).atTime(9, 0);
        assertFalse(p.agendarExame(ANA, 4, dia, null, null, ANA_USUARIO).ok(), "raio-X exige pedido");
        assertTrue(p.agendarExame(ANA, 1, dia.withHour(8), null, null, ANA_USUARIO).ok(), "hemograma e livre");

        Map<String, Object> txt = new HashMap<>();
        txt.put("nome_arquivo", "pedido.pdf");
        txt.put("conteudo", "data:application/pdf;base64," + Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes()));
        assertTrue(new PedidoMedico().enviar(ANA, txt, ANA_USUARIO).erros().containsKey("conteudo"), "tipo decidido pelos bytes, nao pela extensao");

        Map<String, Object> pdf = new HashMap<>();
        pdf.put("nome_arquivo", "pedido raio-x.pdf");
        pdf.put("conteudo", "data:application/pdf;base64," + Base64.getEncoder().encodeToString("%PDF-1.4\n%fake\n".getBytes()));
        pdf.put("id_tipo_exame", "4");
        Portal.Resultado env = new PedidoMedico().enviar(ANA, pdf, ANA_USUARIO);
        assertTrue(env.ok(), () -> String.valueOf(env.erros()));
        pedido = env.id();
        assertNotNull(new PedidoMedico().arquivo((int) pedido, ANA));
        assertNull(new PedidoMedico().arquivo((int) pedido, 5), "outra paciente nao baixa o pedido");

        Portal.Resultado ag = p.agendarExame(ANA, 4, dia, null, (int) pedido, ANA_USUARIO);
        assertTrue(ag.ok(), ag.erro());
        exameComPedido = ag.id();
        assertFalse(p.agendarExame(5, 4, dia.plusHours(1), null, (int) pedido, ANA_USUARIO).ok(), "pedido de outra pessoa nao serve");
    }

    @Test
    @Order(5)
    void recusarPedidoCancelaOExame() {
        PedidoMedico pm = new PedidoMedico();
        assertFalse(pm.conferir((int) pedido, false, "", RECEPCAO).ok(), "recusa exige motivo");
        assertTrue(pm.conferir((int) pedido, false, "Pedido ilegível", RECEPCAO).ok());
        Map<String, Object> x = Sql.um("SELECT status, motivo_cancelamento FROM exames_agendados WHERE id_exame_agendado = ?", exameComPedido);
        assertEquals("cancelado", x.get("status"));
        assertTrue(String.valueOf(x.get("motivo_cancelamento")).contains("Pedido ilegível"));
        assertFalse(pm.conferir((int) pedido, true, "", RECEPCAO).ok(), "ja conferido");
    }

    @Test
    @Order(6)
    void vacinaSegueOEsquemaEAplicacaoEImutavel() throws Exception {
        Vacina v = new Vacina();
        LocalDate sabado = Agenda.agora().toLocalDate().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY));
        assertTrue(v.horarios(sabado).isEmpty(), "sala fechada no fim de semana");
        LocalDateTime quando = diaUtil(2).atTime(10, 0);
        Agenda.Resultado r = v.agendar(ANA, 2, quando, ANA_USUARIO, "portal");
        assertTrue(r.ok(), r.erro());
        assertEquals(3, ((Number) Sql.um("SELECT dose FROM vacinas_agendadas WHERE id_agendamento = ?", r.id()).get("dose")).intValue(),
                "Ana ja tem 2 doses de hepatite B");
        assertFalse(v.agendar(ANA, 2, quando.plusDays(1), ANA_USUARIO, "portal").ok(), "uma dose pendente por vacina");
        assertFalse(v.agendar(5, 1, quando, ANA_USUARIO, "portal").ok(), "horario ocupado na sala");
        assertFalse(v.cancelar((int) r.id(), "nao posso ir", ANA_USUARIO, 5).ok(), "outra paciente nao cancela");

        // Aplicacao no dia: agendamento de hoje (inserido direto) e registro pela enfermagem.
        long hoje;
        try (java.sql.Connection c = database.DBConnection.openConnection()) {
            hoje = Sql.inserir(c, "INSERT INTO vacinas_agendadas (id_paciente, id_vacina, dose, data_hora) VALUES (?, 1, 1, ?)",
                    5, Agenda.agora().withHour(23).withMinute(45));
        }
        Map<String, Object> d = new HashMap<>();
        assertTrue(v.aplicar((int) hoje, d, 2, 31).containsKey("lote"), "lote obrigatorio");
        d.put("lote", "FLU-TESTE-01");
        assertTrue(v.aplicar((int) hoje, d, 2, 31).isEmpty());
        assertEquals("aplicada", Sql.um("SELECT status FROM vacinas_agendadas WHERE id_agendamento = ?", hoje).get("status"));
        assertThrows(RuntimeException.class, () -> Sql.executar("UPDATE vacinas_aplicadas SET lote = 'X' WHERE id_agendamento = ?", hoje),
                "registro de aplicacao e imutavel");
        assertEquals(1, ((java.util.List<?>) v.cartao(5).get("aplicadas")).size());
    }

    @Test
    @Order(7)
    void recepcaoLigaContaSemEmailNaFicha() {
        // Bruno (2) nao tem e-mail na ficha: o portal recusa e a recepcao liga a conta pelo e-mail dela.
        assertFalse(new Portal().vincularNaRecepcao(2, "paula.portal@exemplo.test", RECEPCAO).ok(), "conta ja ligada a outra ficha");
        assertFalse(new Portal().vincularNaRecepcao(1, "qualquer@exemplo.test", RECEPCAO).ok(), "ficha da Ana ja tem conta");
        // Conta do portal ainda sem ficha (cadastro com o CPF da Gabriela fica pendente de codigo).
        String cpfGabi = String.valueOf(Sql.um("SELECT cpf FROM pacientes WHERE id_paciente = 7").get("cpf"));
        Portal.Cadastro c = new Portal().cadastrar(cadastro(cpfGabi, "conta.sem.ficha@exemplo.test"));
        assertTrue(c.resultado().ok());
        assertTrue(new Portal().vincularNaRecepcao(2, "conta.sem.ficha@exemplo.test", RECEPCAO).ok());
        assertEquals(2, Portal.pacienteDoUsuario((int) c.resultado().id()));
        assertEquals(1, Sql.numero("SELECT COUNT(*) FROM auditoria WHERE acao = 'portal.vinculo_recepcao' AND id_paciente = 2"));
    }
}
