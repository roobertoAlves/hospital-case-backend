package controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.Sql;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.Atendimento;
import models.Receita;
import models.SolicitacaoExame;
import models.Triagem;
import models.VinculoClinico;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import support.TestDatabase;

/**
 * Fluxo clinico completo num banco temporario: chegada -> triagem (com retificacao) ->
 * atendimento (rascunho, versoes, finalizacao imutavel, adendo) -> receita -> exame -> historico.
 * Inclui as tentativas de acesso de quem nao tem vinculo (outro medico).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FluxoClinicoIntegrationTest {

    private static final int USUARIO_ENFERMEIRO = 31;
    private static final int ENFERMEIRO = 2;
    private static final int USUARIO_MEDICO = 32;
    private static final int MARINA = 3;          // medica da consulta
    private static final int PAULO = 4;           // outro medico, sem vinculo
    private static final int PACIENTE = 6;

    private static String banco;
    private static int consulta;

    @BeforeAll
    static void prepararChegada() throws Exception {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        banco = TestDatabase.usarBancoTemporario("hospital_fluxo");
        // A recepcao ja registrou a chegada: consulta de hoje aguardando triagem.
        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            consulta = (int) Sql.inserir(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, status, chegada_em) "
                    + "VALUES (?, ?, 1, DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), 'aguardando_triagem', NOW())", PACIENTE, MARINA);
        }
    }

    @AfterAll
    static void apagarBanco() throws Exception {
        if (banco != null) {
            TestDatabase.descartarBancoTemporario(banco);
        }
    }

    private static Map<String, Object> sinais() {
        Map<String, Object> d = new HashMap<>();
        d.put("pa_sistolica", "130");
        d.put("pa_diastolica", "85");
        d.put("frequencia_cardiaca", "88");
        d.put("temperatura", "37,8");
        d.put("saturacao_o2", "97");
        d.put("queixa_principal", "Dor de garganta e febre há dois dias");
        d.put("id_classificacao", "3");
        return d;
    }

    @Test
    @Order(1)
    void medicoNaoAtendeAntesDaTriagem() {
        Atendimento.Resultado r = new Atendimento().iniciar(consulta, MARINA, USUARIO_MEDICO);
        assertFalse(r.ok());
        assertFalse(VinculoClinico.medicoPodeAcessarPaciente(MARINA, PACIENTE), "sem triagem ainda nao ha vinculo do medico");
        assertTrue(VinculoClinico.enfermeiroPodeAcessarPaciente(ENFERMEIRO, PACIENTE), "paciente na fila de triagem");
    }

    @Test
    @Order(2)
    void soUmEnfermeiroIniciaETriagemValidaFaixas() {
        Triagem t = new Triagem();
        assertTrue(t.iniciar(consulta, USUARIO_ENFERMEIRO));
        assertFalse(t.iniciar(consulta, USUARIO_ENFERMEIRO), "segunda tentativa ve a triagem ja em andamento");

        Map<String, Object> ruim = sinais();
        ruim.put("saturacao_o2", "150");
        ruim.put("pa_diastolica", "");
        ruim.remove("id_classificacao");
        Triagem.Resultado erros = t.registrar(consulta, ENFERMEIRO, ruim, USUARIO_ENFERMEIRO);
        assertFalse(erros.ok());
        assertTrue(erros.erros().containsKey("saturacao_o2"), String.valueOf(erros.erros()));
        assertTrue(erros.erros().containsKey("id_classificacao"), String.valueOf(erros.erros()));

        Triagem.Resultado ok = t.registrar(consulta, ENFERMEIRO, sinais(), USUARIO_ENFERMEIRO);
        assertTrue(ok.ok(), String.valueOf(ok.erros()));
        assertEquals("aguardando_atendimento", status());
        assertEquals("37.8", new java.math.BigDecimal(String.valueOf(t.atual(consulta).get("temperatura"))).stripTrailingZeros().toPlainString());
        assertFalse(t.registrar(consulta, ENFERMEIRO, sinais(), USUARIO_ENFERMEIRO).ok(), "nao triagem duas vezes");
    }

    @Test
    @Order(3)
    void filaDoMedicoEOutroMedicoSemAcesso() {
        Atendimento a = new Atendimento();
        List<Map<String, Object>> fila = a.filaDoMedico(MARINA);
        Map<String, Object> nossa = fila.stream().filter(c -> ((Number) c.get("id_consulta")).intValue() == consulta)
                .findFirst().orElse(null);
        assertNotNull(nossa, "a consulta triada entra na fila da medica");
        assertEquals(3, ((Number) nossa.get("ordem")).intValue(), "prioridade amarela da triagem");
        assertTrue(a.filaDoMedico(PAULO).stream().noneMatch(c -> ((Number) c.get("id_consulta")).intValue() == consulta),
                "nao aparece na fila de outro medico");
        assertNull(a.consultaDoMedico(consulta, PAULO), "IDOR: consulta de outro medico nao e carregada");
        assertFalse(a.iniciar(consulta, PAULO, USUARIO_MEDICO).ok());
        assertFalse(VinculoClinico.medicoPodeAcessarPaciente(PAULO, PACIENTE));
        assertEquals(HistoricoController.Nivel.NEGADO, HistoricoController.nivel(List.of("medico"), PAULO, 0, PACIENTE));
    }

    @Test
    @Order(4)
    void atendimentoComRascunhoVersionadoERetificacaoDaTriagem() {
        Atendimento a = new Atendimento();
        assertTrue(a.iniciar(consulta, MARINA, USUARIO_MEDICO).ok());
        Map<String, Object> c = a.consultaDoMedico(consulta, MARINA);
        assertEquals("em_atendimento", c.get("status"));
        assertEquals("Dor de garganta e febre há dois dias", c.get("queixa_principal"), "queixa da triagem pre-preenchida");

        // A enfermagem ainda pode retificar enquanto o atendimento nao termina.
        Triagem t = new Triagem();
        int idTriagem = ((Number) t.atual(consulta).get("id_triagem")).intValue();
        Map<String, Object> corrigida = sinais();
        corrigida.put("id_classificacao", "2");
        assertFalse(t.retificar(idTriagem, ENFERMEIRO, corrigida, "", USUARIO_ENFERMEIRO).ok(), "motivo obrigatorio");
        Triagem.Resultado ret = t.retificar(idTriagem, ENFERMEIRO, corrigida, "Saturação reavaliada", USUARIO_ENFERMEIRO);
        assertTrue(ret.ok(), String.valueOf(ret.erros()));
        assertEquals(2, ((Number) t.atual(consulta).get("versoes")).intValue());
        assertFalse(t.retificar(idTriagem, ENFERMEIRO, corrigida, "de novo", USUARIO_ENFERMEIRO).ok(), "a anterior ja foi retificada");

        Map<String, Object> rascunho = new HashMap<>();
        rascunho.put("queixa_principal", "Odinofagia e febre");
        rascunho.put("historia_doenca_atual", "Início há 48h.");
        assertTrue(a.salvarRascunho(consulta, MARINA, rascunho, USUARIO_MEDICO).ok());
        assertTrue(a.salvarRascunho(consulta, MARINA, rascunho, USUARIO_MEDICO).ok(), "salvar igual nao gera versao");
        assertEquals(1, a.quantidadeDeVersoes(consulta));
        assertEquals(2, ((Number) a.consultaDoMedico(consulta, MARINA).get("versao")).intValue());
        assertFalse(a.salvarRascunho(consulta, PAULO, rascunho, USUARIO_MEDICO).ok(), "so o autor altera");
        Map<String, Object> parcial = new HashMap<>();
        parcial.put("queixa_principal", "Odinofagia e febre");
        assertTrue(a.salvarRascunho(consulta, MARINA, parcial, USUARIO_MEDICO).ok());
        assertEquals("Início há 48h.", a.consultaDoMedico(consulta, MARINA).get("historia_doenca_atual"),
                "campo nao enviado nao e apagado");

        assertTrue(a.registrarEvolucao(consulta, MARINA, "evolucao", "Reavaliado após antitérmico.", USUARIO_MEDICO).ok());
        assertFalse(a.registrarEvolucao(consulta, MARINA, "adendo", "Antes de finalizar", USUARIO_MEDICO).ok());
        assertTrue(a.registrarAlergia(PACIENTE, "Dipirona", "Urticária", "moderada", USUARIO_MEDICO).ok());
        assertEquals(1, a.alergias(PACIENTE).size());
    }

    @Test
    @Order(5)
    void receitaEExameDuranteOAtendimento() {
        Receita rec = new Receita();
        assertFalse(rec.emitir(consulta, MARINA, null, List.of(), USUARIO_MEDICO).ok());
        Map<String, Object> incompleto = new HashMap<>();
        incompleto.put("medicamento", "Paracetamol");
        assertFalse(rec.emitir(consulta, MARINA, null, List.of(incompleto), USUARIO_MEDICO).ok());

        Map<String, Object> item = Map.of("medicamento", "Paracetamol", "dosagem", "750 mg", "via", "oral",
                "frequencia", "6 em 6 horas se febre", "duracao", "5 dias");
        Map<String, Object> item2 = Map.of("medicamento", "Amoxicilina", "dosagem", "500 mg",
                "frequencia", "8 em 8 horas", "duracao", "7 dias");
        assertFalse(rec.emitir(consulta, PAULO, null, List.of(item), USUARIO_MEDICO).ok(), "outro medico nao prescreve");
        Receita.Resultado r = rec.emitir(consulta, MARINA, "Hidratação e repouso.", List.of(item, item2), USUARIO_MEDICO);
        assertTrue(r.ok(), String.valueOf(r.erros()));
        Map<String, Object> impressao = rec.paraImpressao((int) r.id(), MARINA);
        assertEquals(2, ((List<?>) impressao.get("itens")).size());
        assertNotNull(impressao.get("crm"));
        assertNull(rec.paraImpressao((int) r.id(), PAULO), "IDOR na impressao");
        assertFalse(rec.cancelar((int) r.id(), MARINA, "", USUARIO_MEDICO).ok());
        assertTrue(rec.cancelar((int) r.id(), MARINA, "Dose ajustada", USUARIO_MEDICO).ok());
        assertFalse(rec.cancelar((int) r.id(), MARINA, "Dose ajustada", USUARIO_MEDICO).ok(), "ja cancelada");

        SolicitacaoExame ex = new SolicitacaoExame();
        Map<String, Object> pedido = new HashMap<>();
        pedido.put("nome_exame", "Hemograma completo");
        pedido.put("justificativa", "Febre persistente");
        assertTrue(ex.solicitar(consulta, MARINA, pedido, USUARIO_MEDICO).ok());
        assertFalse(ex.solicitar(consulta, PAULO, pedido, USUARIO_MEDICO).ok());
        assertEquals(1, ex.daConsulta(consulta).size());
    }

    @Test
    @Order(6)
    void finalizarTornaOProntuarioImutavel() throws Exception {
        Atendimento a = new Atendimento();
        Map<String, Object> semConduta = new HashMap<>();
        semConduta.put("queixa_principal", "Odinofagia e febre");
        semConduta.put("historia_doenca_atual", "Início há 48h.");
        assertFalse(a.finalizar(consulta, MARINA, semConduta, USUARIO_MEDICO).ok(), "conduta obrigatoria");

        Map<String, Object> completo = new HashMap<>(semConduta);
        completo.put("hipoteses_diagnosticas", "Faringite aguda");
        completo.put("conduta", "Sintomáticos e retorno se piora.");
        assertTrue(a.finalizar(consulta, MARINA, completo, USUARIO_MEDICO).ok());
        assertEquals("realizada", status());
        assertFalse(a.salvarRascunho(consulta, MARINA, completo, USUARIO_MEDICO).ok());
        assertTrue(a.registrarEvolucao(consulta, MARINA, "adendo", "Resultado do teste rápido negativo.", USUARIO_MEDICO).ok());

        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            assertThrows(java.sql.SQLException.class, () -> Sql.executar(conn,
                    "UPDATE prontuarios SET conduta = 'alterada' WHERE id_consulta = ?", consulta), "trigger bloqueia alteracao");
        }
        assertFalse(new Triagem().retificar(((Number) new Triagem().atual(consulta).get("id_triagem")).intValue(), ENFERMEIRO,
                sinais(), "tarde demais", USUARIO_ENFERMEIRO).ok(), "triagem nao se retifica depois do atendimento");
    }

    @Test
    @Order(7)
    void historicoFiltradoPorPerfilEAuditado() {
        assertEquals(HistoricoController.Nivel.MEDICO, HistoricoController.nivel(List.of("medico"), MARINA, 0, PACIENTE));
        assertEquals(HistoricoController.Nivel.ENFERMAGEM, HistoricoController.nivel(List.of("enfermeiro"), 0, ENFERMEIRO, PACIENTE));
        assertEquals(HistoricoController.Nivel.ADMINISTRATIVO, HistoricoController.nivel(List.of("recepcionista"), 0, 0, PACIENTE));
        assertEquals(HistoricoController.Nivel.NEGADO, HistoricoController.nivel(List.of("admin"), 0, 0, PACIENTE), "admin sem acesso clinico");
        assertEquals(HistoricoController.Nivel.NEGADO, HistoricoController.nivel(List.of("enfermeiro"), 0, 999, PACIENTE));

        assertEquals(1, new Atendimento().prontuariosDoPaciente(PACIENTE).size());
        long auditados = Sql.numero("SELECT COUNT(*) FROM auditoria WHERE id_paciente = ? AND acao IN "
                + "('triagem.registrada', 'triagem.retificada', 'atendimento.iniciado', 'prontuario.alterado', "
                + "'atendimento.finalizado', 'receita.emitida', 'receita.cancelada', 'exame.solicitado', 'alergia.registrada')", PACIENTE);
        // 10 = 9 acoes + uma segunda "prontuario.alterado": finalizar grava antes a ultima versao do rascunho
        assertEquals(10, auditados);
        long comConteudoClinico = Sql.numero("SELECT COUNT(*) FROM auditoria WHERE detalhes LIKE '%garganta%' OR detalhes LIKE '%Faringite%'");
        assertEquals(0, comConteudoClinico, "auditoria nao guarda conteudo clinico");
    }

    private static String status() {
        return String.valueOf(Sql.um("SELECT status FROM consultas WHERE id_consulta = ?", consulta).get("status"));
    }
}
