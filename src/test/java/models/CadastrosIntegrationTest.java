package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.Sql;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import support.TestDatabase;

/**
 * Cadastro de profissionais e especialidades, disponibilidade semanal, remarcacao de
 * consulta e consulta da auditoria, num banco temporario.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CadastrosIntegrationTest {

    private static final int ADMIN = 1;
    private static final int RECEPCAO = 30;
    private static final int MARINA = 3;
    private static final int PACIENTE = 1;

    private static String banco;
    private static long novoMedico;
    private static long consultaOriginal;
    private static long consultaRemarcada;

    @BeforeAll
    static void preparar() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        banco = TestDatabase.usarBancoTemporario("hospital_cadastros");
    }

    @AfterAll
    static void apagar() throws Exception {
        if (banco != null) {
            TestDatabase.descartarBancoTemporario(banco);
        }
    }

    private static Map<String, Object> medico(String crm, String esp) {
        Map<String, Object> d = new HashMap<>();
        d.put("tipo", "medico");
        d.put("nome", "Carlos Cardio (ficticio)");
        d.put("email", "carlos.cardio@hospital.local");
        d.put("telefone", "11988887777");
        d.put("crm", crm);
        d.put("crm_uf", "sp");
        d.put("duracao_consulta_min", "20");
        d.put("especialidades", esp);
        return d;
    }

    /** Proxima segunda-feira a partir de amanha (Marina atende segunda, 08h-12h). */
    private static LocalDate proximaSegunda() {
        LocalDate d = Agenda.agora().toLocalDate().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    @Order(1)
    void validaRegistroEspecialidadesEVinculo() {
        Profissional p = new Profissional();
        assertTrue(p.criar(medico("000001", "1"), ADMIN).erros().containsKey("crm"), "CRM 000001/SP ja e da Marina");
        assertTrue(p.criar(medico("123456", ""), ADMIN).erros().containsKey("especialidades"));
        assertTrue(p.criar(medico("123456", "999"), ADMIN).erros().containsKey("especialidades"), "especialidade inexistente");

        Map<String, Object> vinculado = medico("123456", "1");
        vinculado.put("id_usuario", "32");
        assertTrue(p.criar(vinculado, ADMIN).erros().containsKey("id_usuario"), "usuario 32 ja e a Marina");

        Map<String, Object> enf = new HashMap<>();
        enf.put("tipo", "enfermeiro");
        enf.put("nome", "Outra Enfermeira");
        enf.put("coren", "000001");
        enf.put("coren_uf", "SP");
        assertTrue(p.criar(enf, ADMIN).erros().containsKey("coren"), "COREN repetido");

        Map<String, Object> func = new HashMap<>();
        func.put("tipo", "funcionario");
        func.put("nome", "Fulano Sem Cargo");
        assertTrue(p.criar(func, ADMIN).erros().containsKey("cargo"));

        Map<String, Object> tipo = new HashMap<>();
        tipo.put("tipo", "administrador");
        assertTrue(p.criar(tipo, ADMIN).erros().containsKey("tipo"));

        Map<String, Object> acessoRepetido = medico("123456", "1");
        acessoRepetido.put("email", "medico@hospital.local");
        acessoRepetido.put("criar_acesso", "1");
        assertTrue(p.criar(acessoRepetido, ADMIN).erros().containsKey("email"), "e-mail de usuario ja existente");
    }

    @Test
    @Order(2)
    void criaMedicoComAcessoNoGrupoCertoESenhaTemporaria() {
        Map<String, Object> d = medico("123456", "2,1");
        d.put("criar_acesso", "1");
        Profissional.Resultado r = new Profissional().criar(d, ADMIN);
        assertTrue(r.ok(), () -> "erros: " + r.erros());
        novoMedico = r.id();
        assertNotNull(r.senhaTemporaria());
        assertEquals("carlos.cardio@hospital.local", r.emailAcesso());

        Map<String, Object> u = Sql.um("SELECT u.id_usuario, u.primeiro_acesso, u.senha_hash, g.nome AS grupo FROM usuarios u "
                + "JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario JOIN grupos g ON g.id_grupo = ug.id_grupo WHERE u.email = ?",
                "carlos.cardio@hospital.local");
        assertEquals("medico", u.get("grupo"));
        assertEquals("1", String.valueOf(u.get("primeiro_acesso")), "troca de senha obrigatoria no primeiro acesso");
        assertTrue(utils.PasswordSecurity.verify(r.senhaTemporaria(), String.valueOf(u.get("senha_hash"))));

        Map<String, Object> ficha = new Profissional().porId((int) novoMedico);
        assertEquals("SP", ficha.get("crm_uf"), "UF normalizada");
        assertEquals("(11) 98888-7777", ficha.get("telefone"));
        assertEquals(2, ((List<?>) ficha.get("ids_especialidades")).size());
        assertNotNull(new Profissional().porUsuario(((Number) u.get("id_usuario")).intValue()));
        assertEquals(1, Sql.numero("SELECT COUNT(*) FROM auditoria WHERE acao = 'profissional.criado' AND id_entidade = ?", novoMedico));
        assertEquals(1, Sql.numero("SELECT COUNT(*) FROM auditoria WHERE acao = 'usuario.criado'"));
    }

    @Test
    @Order(3)
    void atualizaSemMudarOTipo() {
        Map<String, Object> d = medico("123456", "2");
        d.put("tipo", "enfermeiro");
        d.put("duracao_consulta_min", "40");
        d.put("id_usuario", String.valueOf(Sql.um("SELECT id_usuario FROM profissionais WHERE id_profissional = ?", novoMedico).get("id_usuario")));
        Profissional.Resultado r = new Profissional().atualizar((int) novoMedico, d, ADMIN);
        assertTrue(r.ok(), () -> "erros: " + r.erros());
        Map<String, Object> ficha = new Profissional().porId((int) novoMedico);
        assertEquals("medico", ficha.get("tipo"));
        assertEquals(40, ((Number) ficha.get("duracao_consulta_min")).intValue());
        assertEquals(List.of(2), ficha.get("ids_especialidades"));

        Map<String, Object> ruim = medico("123456", "2");
        ruim.put("duracao_consulta_min", "17");
        assertTrue(new Profissional().atualizar((int) novoMedico, ruim, ADMIN).erros().containsKey("duracao_consulta_min"));
    }

    @Test
    @Order(4)
    void disponibilidadeRejeitaSobreposicaoEAlimentaOsHorarios() {
        Profissional p = new Profissional();
        List<Map<String, Object>> sobrepostas = List.of(faixa(2, "08:00", "12:00"), faixa(2, "11:00", "13:00"));
        assertTrue(p.salvarDisponibilidade((int) novoMedico, sobrepostas, ADMIN).containsKey("faixa_1"));
        assertTrue(p.salvarDisponibilidade((int) novoMedico, List.of(faixa(2, "12:00", "08:00")), ADMIN).containsKey("faixa_0"));
        assertTrue(p.salvarDisponibilidade(2, List.of(faixa(2, "08:00", "12:00")), ADMIN).containsKey("_"), "enfermeiro nao tem agenda");

        Map<String, String> ok = p.salvarDisponibilidade((int) novoMedico, List.of(faixa(2, "08:00", "10:00"), faixa(4, "14:00", "16:00")), ADMIN);
        assertTrue(ok.isEmpty(), () -> "erros: " + ok);
        LocalDate terca = proximaSegunda().plusDays(1);
        List<Agenda.Horario> horarios = new Agenda().horarios((int) novoMedico, terca);
        assertEquals(3, horarios.size(), "08:00-10:00 em consultas de 40 min: 08:00, 08:40, 09:20");
        assertEquals(LocalTime.of(8, 40), horarios.get(1).hora());
        assertTrue(new Agenda().horarios((int) novoMedico, terca.plusDays(1)).isEmpty(), "quarta sem faixa");
    }

    private static Map<String, Object> faixa(int dia, String ini, String fim) {
        Map<String, Object> f = new HashMap<>();
        f.put("dia_semana", (double) dia);
        f.put("hora_inicio", ini);
        f.put("hora_fim", fim);
        return f;
    }

    @Test
    @Order(5)
    void remarcarCancelaAOriginalECriaALigada() {
        Agenda agenda = new Agenda();
        // Quatro semanas a frente: fora das consultas geradas pela migracao de demonstracao (003).
        LocalDateTime segunda8 = proximaSegunda().plusWeeks(4).atTime(8, 0);
        Agenda.Resultado a = agenda.agendar(PACIENTE, MARINA, 1, segunda8, RECEPCAO);
        assertTrue(a.ok(), () -> a.erro());
        consultaOriginal = a.id();

        assertFalse(agenda.remarcar((int) consultaOriginal, MARINA, segunda8, "", RECEPCAO).ok(), "mesmo horario");
        assertFalse(agenda.remarcar((int) consultaOriginal, MARINA, proximaSegunda().minusWeeks(2).atTime(8, 0), "", RECEPCAO).ok(), "passado");
        assertFalse(agenda.remarcar((int) consultaOriginal, MARINA, segunda8.withHour(7), "", RECEPCAO).ok(), "fora da grade");

        Agenda.Resultado r = agenda.remarcar((int) consultaOriginal, MARINA, segunda8.plusMinutes(30), "Pedido do paciente", RECEPCAO);
        assertTrue(r.ok(), () -> r.erro());
        consultaRemarcada = r.id();

        Map<String, Object> antiga = Sql.um("SELECT status, motivo_cancelamento, cancelada_por FROM consultas WHERE id_consulta = ?", consultaOriginal);
        assertEquals("cancelada", antiga.get("status"));
        assertTrue(String.valueOf(antiga.get("motivo_cancelamento")).startsWith("Remarcada para "));
        assertTrue(String.valueOf(antiga.get("motivo_cancelamento")).endsWith("Pedido do paciente"));
        Map<String, Object> nova = Sql.um("SELECT status, remarcada_de, id_paciente FROM consultas WHERE id_consulta = ?", consultaRemarcada);
        assertEquals("agendada", nova.get("status"));
        assertEquals(consultaOriginal, ((Number) nova.get("remarcada_de")).longValue());
        assertEquals(1, Sql.numero("SELECT COUNT(*) FROM auditoria WHERE acao = 'consulta.remarcada' AND id_entidade = ?", consultaRemarcada));

        assertFalse(agenda.remarcar((int) consultaOriginal, MARINA, segunda8.plusHours(1), "", RECEPCAO).ok(), "a original ja esta cancelada");
        Agenda.Resultado ocupado = agenda.agendar(2, MARINA, 1, segunda8.plusHours(2), RECEPCAO);
        assertTrue(ocupado.ok());
        assertFalse(agenda.remarcar((int) consultaRemarcada, MARINA, segunda8.plusHours(2), "", RECEPCAO).ok(), "horario ocupado");
        assertEquals("agendada", Sql.um("SELECT status FROM consultas WHERE id_consulta = ?", consultaRemarcada).get("status"),
                "falha na remarcacao desfaz o cancelamento (mesma transacao)");
        agenda.cancelar((int) ocupado.id(), "limpeza do teste", RECEPCAO);
    }

    @Test
    @Order(6)
    void naoInativaComConsultasFuturas() {
        assertNotNull(new Profissional().definirAtivo(MARINA, false, ADMIN), "Marina tem consulta agendada");
        assertNotNull(new Especialidade().definirAtivo(1, false));
        new Agenda().cancelar((int) consultaRemarcada, "limpeza do teste", RECEPCAO);
        assertNull(new Profissional().definirAtivo((int) novoMedico, false, ADMIN));
        assertEquals("0", String.valueOf(Sql.um("SELECT ativo FROM profissionais WHERE id_profissional = ?", novoMedico).get("ativo")));
        assertNull(new Profissional().porUsuario(((Number) Sql.um("SELECT id_usuario FROM profissionais WHERE id_profissional = ?",
                novoMedico).get("id_usuario")).intValue()), "inativo perde o vinculo clinico");
        assertNull(new Profissional().definirAtivo((int) novoMedico, true, ADMIN));
    }

    @Test
    @Order(7)
    void especialidadeComNomeUnico() {
        Especialidade e = new Especialidade();
        long[] id = new long[1];
        Map<String, Object> d = new HashMap<>();
        d.put("nome", "Cardiologia");
        assertTrue(e.salvar(0, d, ADMIN, id).containsKey("nome"), "nome repetido");
        d.put("nome", "Hematologia");
        d.put("descricao", "Doencas do sangue");
        assertTrue(e.salvar(0, d, ADMIN, id).isEmpty());
        d.put("nome", "Hematologia Clínica");
        assertTrue(e.salvar((int) id[0], d, ADMIN, new long[1]).isEmpty());
        assertEquals("Hematologia Clínica", e.porId((int) id[0]).get("nome"));
        assertNull(e.definirAtivo((int) id[0], false));
        assertFalse(e.ativas().stream().anyMatch(x -> "Hematologia Clínica".equals(x.get("nome"))));
    }

    @Test
    @Order(8)
    void nomeComHtmlEGravadoSemTags() {
        Map<String, Object> d = new HashMap<>();
        d.put("tipo", "funcionario");
        d.put("nome", "<script>alert(1)</script>Joana <b>Silva</b>");
        d.put("cargo", "Recepcionista <img src=x onerror=alert(2)>");
        Profissional.Resultado r = new Profissional().criar(d, ADMIN);
        assertTrue(r.ok(), () -> "erros: " + r.erros());
        Map<String, Object> p = Sql.um("SELECT nome, cargo FROM profissionais WHERE id_profissional = ?", r.id());
        assertEquals("Joana Silva", p.get("nome"));
        assertFalse(String.valueOf(p.get("cargo")).contains("<"), "tags removidas antes de gravar");
    }

    @Test
    @Order(9)
    void consultaDaAuditoriaFiltraEMascara() {
        ConsultaAuditoria c = new ConsultaAuditoria();
        LocalDate hoje = Agenda.agora().toLocalDate();
        ConsultaAuditoria.Pagina prof = c.buscar(new ConsultaAuditoria.Filtro(hoje.minusDays(1), hoje.plusDays(1), null, "profissional", null, 1));
        assertTrue(prof.total() >= 3, "criado, atualizado, disponibilidade");
        assertTrue(prof.itens().stream().allMatch(i -> String.valueOf(i.get("acao")).startsWith("profissional.")));
        ConsultaAuditoria.Pagina pac = c.buscar(new ConsultaAuditoria.Filtro(hoje.minusDays(1), hoje.plusDays(1), RECEPCAO, null, PACIENTE, 1));
        assertTrue(pac.itens().stream().allMatch(i -> ((Number) i.get("id_paciente")).intValue() == PACIENTE));
        assertTrue(c.modulos().contains("consulta"));
        assertEquals("an***@exemplo.test", ConsultaAuditoria.emailMascarado("ana.ficticia@exemplo.test"));
        assertEquals("a***@x.test", ConsultaAuditoria.emailMascarado("a@x.test"));
    }
}
