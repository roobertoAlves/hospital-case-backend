package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.Sql;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

/**
 * Pacientes, agenda de consultas e de exames num banco temporario (script.sql + migracoes),
 * com os dados ficticios das migracoes. Nada fica no banco de desenvolvimento.
 */
class AgendamentoIntegrationTest {

    private static final int RECEPCAO = 30;      // usuario da recepcao no seed
    private static final int LUCIA = 5;          // medica ficticia: seg-sex 08-12, consultas de 20 min, pediatria (3)
    private static final int PEDIATRIA = 3;
    private static final int CARDIOLOGIA = 2;
    private static final int HEMOGRAMA = 1;      // 10 min, laboratorio (07-12)
    private static final int ULTRASSOM = 5;      // 30 min, imagem (08-17)

    private static String banco;
    private static LocalDate segunda;

    @BeforeAll
    static void criarBanco() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        banco = TestDatabase.usarBancoTemporario("hospital_agenda");
        segunda = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @AfterAll
    static void apagarBanco() throws Exception {
        if (banco != null) {
            TestDatabase.descartarBancoTemporario(banco);
        }
    }

    private static Map<String, Object> dadosPaciente(String nome, String cpf) {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", nome);
        d.put("cpf", cpf);
        d.put("data_nascimento", "1988-04-10");
        d.put("telefone", "11988887777");
        d.put("email", "teste.ficticio@exemplo.test");
        d.put("uf", "sp");
        return d;
    }

    // ---------------------------------------------------------------- pacientes

    @Test
    void cadastroValidaENormalizaEImpedeCpfRepetido() {
        Paciente modelo = new Paciente();
        Paciente.Resultado ok = modelo.criar(dadosPaciente("Teste Normalizacao Ficticio", "861.442.140-03"), RECEPCAO);
        assertTrue(ok.ok(), String.valueOf(ok.erros()));
        Map<String, Object> salvo = modelo.porId((int) ok.id());
        assertEquals("86144214003", salvo.get("cpf"), "CPF gravado so com digitos");
        assertEquals("(11) 98888-7777", salvo.get("telefone"));
        assertEquals("SP", salvo.get("uf"));

        Paciente.Resultado repetido = modelo.criar(dadosPaciente("Outro Nome Ficticio", "86144214003"), RECEPCAO);
        assertEquals("Já existe um paciente com este CPF.", repetido.erros().get("cpf"));

        Map<String, Object> ruim = dadosPaciente("Ab", "12345678900");
        ruim.put("data_nascimento", "2999-01-01");
        ruim.put("email", "sem-arroba");
        ruim.put("nome; DROP TABLE pacientes; --", "x");
        Paciente.Resultado erros = modelo.criar(ruim, RECEPCAO);
        assertEquals(Map.of(
                "nome", "Informe o nome completo.",
                "cpf", "CPF inválido.",
                "data_nascimento", "Data de nascimento inválida.",
                "email", "E-mail inválido."), erros.erros());
    }

    @Test
    void buscaPorNomeECpfComPaginacao() {
        Paciente modelo = new Paciente();
        assertEquals(1, modelo.buscar("Elaine", false, 1, 20).total());
        assertEquals(1, modelo.buscar("529.982", false, 1, 20).total(), "prefixo de CPF");
        assertEquals(0, modelo.buscar("%", false, 1, 20).total(), "curinga do LIKE e tratado como texto");
        Paciente.Pagina p = modelo.buscar("", false, 2, 3);
        assertEquals(3, p.itens().size());
        assertTrue(p.total() >= 10);
    }

    // ---------------------------------------------------------------- consultas

    @Test
    void agendaRespeitaGradeConflitosECancelamentoLiberaOHorario() {
        Agenda agenda = new Agenda();
        LocalDateTime oitoEVinte = segunda.atTime(8, 20);

        Agenda.Resultado primeira = agenda.agendar(4, LUCIA, PEDIATRIA, oitoEVinte, RECEPCAO);
        assertTrue(primeira.ok(), primeira.erro());

        assertFalse(agenda.agendar(6, LUCIA, PEDIATRIA, oitoEVinte, RECEPCAO).ok(), "medico ja ocupado");
        assertFalse(agenda.agendar(4, LUCIA, PEDIATRIA, segunda.atTime(8, 30), RECEPCAO).ok(), "fora da grade de 20 min");
        assertFalse(agenda.agendar(6, LUCIA, PEDIATRIA, segunda.atTime(14, 0), RECEPCAO).ok(), "fora do horario do medico");
        assertFalse(agenda.agendar(6, LUCIA, CARDIOLOGIA, segunda.atTime(9, 0), RECEPCAO).ok(), "especialidade de outro medico");
        assertFalse(agenda.agendar(6, LUCIA, PEDIATRIA, LocalDateTime.now().minusDays(1), RECEPCAO).ok(), "no passado");

        List<Agenda.Horario> grade = agenda.horarios(LUCIA, segunda);
        assertEquals(12, grade.size(), "08:00 a 12:00 em passos de 20 min");
        assertFalse(grade.get(1).livre(), "08:20 ocupado");

        assertTrue(agenda.cancelar((int) primeira.id(), "Paciente pediu para remarcar", RECEPCAO).ok());
        assertTrue(agenda.horarios(LUCIA, segunda).get(1).livre(), "cancelamento libera o horario");
        assertTrue(agenda.agendar(6, LUCIA, PEDIATRIA, oitoEVinte, RECEPCAO).ok());
    }

    @Test
    void duasRecepcionistasNoMesmoHorarioSoUmaGrava() throws Exception {
        LocalDateTime nove = segunda.plusDays(1).atTime(9, 0);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Agenda.Resultado>> tarefas = new ArrayList<>();
            int[] pacientes = {7, 8, 9, 10};
            for (int p : pacientes) {
                tarefas.add(() -> new Agenda().agendar(p, LUCIA, PEDIATRIA, nove, RECEPCAO));
            }
            int gravadas = 0;
            for (Future<Agenda.Resultado> f : pool.invokeAll(tarefas)) {
                if (f.get().ok()) {
                    gravadas++;
                }
            }
            assertEquals(1, gravadas);
            assertEquals(1, Sql.numero("SELECT COUNT(*) FROM consultas WHERE id_medico = ? AND data_hora = ? AND slot_ativo = 1", LUCIA, nove));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void chegadaSoNoDiaDaConsulta() {
        Agenda agenda = new Agenda();
        Agenda.Resultado r = agenda.agendar(5, LUCIA, PEDIATRIA, segunda.plusDays(2).atTime(10, 0), RECEPCAO);
        assertTrue(r.ok(), r.erro());
        Agenda.Resultado chegada = agenda.registrarChegada((int) r.id(), RECEPCAO);
        assertEquals("A chegada só pode ser registrada no dia da consulta.", chegada.erro());
    }

    // ---------------------------------------------------------------- exames

    @Test
    void exameNaoSobrepoeNoMesmoLocalEUsaDuracaoDoTipo() {
        AgendaExames exames = new AgendaExames();
        LocalDateTime dezHoras = segunda.atTime(10, 0);
        Agenda.Resultado us = exames.agendar(4, ULTRASSOM, dezHoras, null, RECEPCAO);
        assertTrue(us.ok(), us.erro());

        assertFalse(exames.agendar(6, ULTRASSOM, segunda.atTime(10, 20), null, RECEPCAO).ok(), "sobrepoe os 30 min do ultrassom");
        assertTrue(exames.agendar(6, ULTRASSOM, segunda.atTime(10, 30), null, RECEPCAO).ok(), "logo apos o termino");
        assertTrue(exames.agendar(7, HEMOGRAMA, dezHoras, null, RECEPCAO).ok(), "outro local, mesmo horario");
        assertFalse(exames.agendar(4, HEMOGRAMA, segunda.atTime(10, 10), null, RECEPCAO).ok(), "mesmo paciente ocupado (10:00-10:30)");
        assertFalse(exames.agendar(8, HEMOGRAMA, segunda.atTime(12, 0), null, RECEPCAO).ok(), "laboratorio fecha as 12:00");

        assertTrue(exames.cancelar((int) us.id(), "Paciente sem jejum", RECEPCAO).ok());
        assertTrue(exames.agendar(8, ULTRASSOM, dezHoras, null, RECEPCAO).ok(), "cancelamento libera o horario");
    }

    @Test
    void exameNaoAgendaNoDomingoNemSemPacienteAtivo() {
        AgendaExames exames = new AgendaExames();
        assertTrue(exames.horarios(HEMOGRAMA, segunda.minusDays(1)).isEmpty(), "domingo");
        assertFalse(exames.horarios(HEMOGRAMA, segunda.minusDays(2)).isEmpty(), "laboratorio atende sabado");
        assertTrue(exames.horarios(ULTRASSOM, segunda.minusDays(2)).isEmpty(), "imagem nao atende sabado");

        new Paciente().definirAtivo(10, false, RECEPCAO);
        assertEquals("Paciente inativo ou inexistente.",
                exames.agendar(10, HEMOGRAMA, segunda.atTime(7, 0), null, RECEPCAO).erro());
        new Paciente().definirAtivo(10, true, RECEPCAO);
    }

    @Test
    void painelContaAPartirDasLinhas() {
        Painel painel = new Painel();
        LocalDate dia = segunda.plusDays(3);
        long antes = painel.consultasDoDia(dia);
        assertTrue(new Agenda().agendar(9, LUCIA, PEDIATRIA, dia.atTime(11, 0), RECEPCAO).ok());
        assertEquals(antes + 1, painel.consultasDoDia(dia));
        assertEquals(0, painel.canceladasDoDia(dia));
    }
}
