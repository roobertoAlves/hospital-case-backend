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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatientPortalIntegrationTest {

    private static final int ANA_USER = 60;
    private static final int ANA = 1;
    private static final int RECEPTION = 30;
    private static String testDb;
    private static long order;
    private static long examWithOrder;

    @BeforeAll
    static void prepare() {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        testDb = TestDatabase.useTemporaryDatabase("hospital_portal");
    }

    @AfterAll
    static void delete() throws Exception {
        if (testDb != null) {
            for (Map<String, Object> p : Sql.list("SELECT arquivo FROM pedidos_medicos")) {
                new File(utils.Config.getStorageDir("pedidos"), String.valueOf(p.get("arquivo"))).delete();
            }
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    private static Map<String, Object> registration(String cpf, String email) {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", "Paula Portal Ficticia");
        d.put("cpf", cpf);
        d.put("data_nascimento", "1990-04-10");
        d.put("email", email);
        d.put("telefone", "11977776666");
        d.put("password", "Senha123forte");
        d.put("confirm_password", "Senha123forte");
        d.put("accept_terms", "1");
        return d;
    }

    private static LocalDate businessDay(int weeks) {
        LocalDate d = Appointments.now().toLocalDate().plusWeeks(weeks);
        while (d.getDayOfWeek() != DayOfWeek.TUESDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    @Order(1)
    void newCpfRegistrationCreatesLinkedRecordAndAccount() {
        Map<String, Object> withoutAcceptance = registration("52998224725", "x@exemplo.test");
        withoutAcceptance.put("accept_terms", "0");
        assertTrue(new PatientPortal().register(withoutAcceptance).result().errors().containsKey("accept_terms"));
        Map<String, Object> weak = registration("52998224725", "x@exemplo.test");
        weak.put("password", "123");
        weak.put("confirm_password", "123");
        assertTrue(new PatientPortal().register(weak).result().errors().containsKey("password"));

        String newCpf = "48152637017";
        PatientPortal.Registration c = new PatientPortal().register(registration(newCpf, "paula.portal@exemplo.test"));
        assertTrue(c.result().ok(), () -> String.valueOf(c.result().errors()));
        assertNull(c.code(), "a new CPF needs no code");
        int userId = (int) c.result().id();
        Integer pac = PatientPortal.userPatient(userId);
        assertNotNull(pac);
        assertEquals("paciente", Sql.one("SELECT g.nome FROM usuarios_grupos ug JOIN grupos g ON g.id_grupo = ug.id_grupo WHERE ug.id_usuario = ?",
                userId).get("nome"));
        assertTrue(new PatientPortal().register(registration(newCpf, "outra@exemplo.test")).result().errors().containsKey("cpf"), "CPF that already has an account");
    }

    @Test
    @Order(2)
    void existingRecordLinksOnlyWithCodeFromRecordEmail() {
        String cpfBruno = String.valueOf(Sql.one("SELECT cpf FROM pacientes WHERE id_paciente = 2").get("cpf"));
        assertTrue(new PatientPortal().register(registration(cpfBruno, "bruno.portal@exemplo.test")).result().errors().get("cpf").contains("recepção"));

        String cpfElaine = String.valueOf(Sql.one("SELECT cpf FROM pacientes WHERE id_paciente = 5").get("cpf"));
        PatientPortal.Registration c = new PatientPortal().register(registration(cpfElaine, "elaine.nova@exemplo.test"));
        assertTrue(c.result().ok());
        assertEquals("elaine.moura@exemplo.test", c.recordEmail());
        int userId = (int) c.result().id();
        assertNull(PatientPortal.userPatient(userId), "not linked yet");
        assertEquals(c.code().length(), 6);
        assertFalse(Sql.one("SELECT codigo_hash FROM vinculos_portal WHERE id_usuario = ?", userId).get("codigo_hash").equals(c.code()),
                "only the code hash is stored");

        String wrong = c.code().equals("000000") ? "111111" : "000000";
        assertFalse(new PatientPortal().link(userId, wrong).ok());
        assertEquals(1, ((Number) Sql.one("SELECT tentativas FROM vinculos_portal WHERE id_usuario = ?", userId).get("tentativas")).intValue());
        assertTrue(new PatientPortal().link(userId, c.code()).ok());
        assertEquals(5, PatientPortal.userPatient(userId));
        assertFalse(new PatientPortal().link(userId, c.code()).ok(), "single-use code");
    }

    @Test
    @Order(3)
    void patientOnlyTouchesOwnRecords() {
        PatientPortal p = new PatientPortal();
        LocalDateTime tuesday = businessDay(5).atTime(9, 0);
        PatientPortal.Result r = p.bookAppointment(ANA, 3, 1, tuesday, ANA_USER);
        assertTrue(r.ok(), r.error());
        assertTrue(PatientPortal.patientAppointment((int) r.id(), ANA));
        assertFalse(PatientPortal.patientAppointment((int) r.id(), 5), "another patient cannot see it");
        assertFalse(p.confirmAppointment((int) r.id(), 5, ANA_USER).ok(), "confirming someone else's appointment fails");
        assertTrue(p.confirmAppointment((int) r.id(), ANA, ANA_USER).ok());
        assertFalse(p.confirmAppointment((int) r.id(), ANA, ANA_USER).ok(), "ja confirmada");
        assertEquals("portal", Sql.one("SELECT origem FROM consultas WHERE id_consulta = ?", r.id()).get("origem"));
        assertNull(new Prescription().forPatientPrint(
                ((Number) Sql.one("SELECT r.id_receita FROM receitas r JOIN consultas c ON c.id_consulta = r.id_consulta WHERE c.id_paciente <> 1 LIMIT 1")
                        .get("id_receita")).intValue(), ANA), "someone else's prescription does not open");
    }

    @Test
    @Order(4)
    void examRequiringOrderOnlyWithAttachmentOrRequest() {
        PatientPortal p = new PatientPortal();
        LocalDateTime day = businessDay(6).atTime(9, 0);
        assertFalse(p.bookExam(ANA, 4, day, null, null, ANA_USER).ok(), "X-ray requires an order");
        assertTrue(p.bookExam(ANA, 1, day.withHour(8), null, null, ANA_USER).ok(), "hemograma e livre");

        Map<String, Object> txt = new HashMap<>();
        txt.put("file_name", "pedido.pdf");
        txt.put("content", "data:application/pdf;base64," + Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes()));
        assertTrue(new MedicalOrder().send(ANA, txt, ANA_USER).errors().containsKey("content"), "type decided by the bytes, not by the extension");

        Map<String, Object> pdf = new HashMap<>();
        pdf.put("file_name", "pedido raio-x.pdf");
        pdf.put("content", "data:application/pdf;base64," + Base64.getEncoder().encodeToString("%PDF-1.4\n%fake\n".getBytes()));
        pdf.put("id_tipo_exame", "4");
        PatientPortal.Result env = new MedicalOrder().send(ANA, pdf, ANA_USER);
        assertTrue(env.ok(), () -> String.valueOf(env.errors()));
        order = env.id();
        assertNotNull(new MedicalOrder().file((int) order, ANA));
        assertNull(new MedicalOrder().file((int) order, 5), "another patient cannot download the order");

        PatientPortal.Result ag = p.bookExam(ANA, 4, day, null, (int) order, ANA_USER);
        assertTrue(ag.ok(), ag.error());
        examWithOrder = ag.id();
        assertFalse(p.bookExam(5, 4, day.plusHours(1), null, (int) order, ANA_USER).ok(), "someone else's order is not accepted");
    }

    @Test
    @Order(5)
    void rejectingOrderCancelsExam() {
        MedicalOrder pm = new MedicalOrder();
        assertFalse(pm.review((int) order, false, "", RECEPTION).ok(), "recusa exige motivo");
        assertTrue(pm.review((int) order, false, "Pedido ilegível", RECEPTION).ok());
        Map<String, Object> x = Sql.one("SELECT status, motivo_cancelamento FROM exames_agendados WHERE id_exame_agendado = ?", examWithOrder);
        assertEquals("cancelado", x.get("status"));
        assertTrue(String.valueOf(x.get("motivo_cancelamento")).contains("Pedido ilegível"));
        assertFalse(pm.review((int) order, true, "", RECEPTION).ok(), "ja conferido");
    }

    @Test
    @Order(6)
    void vaccineFollowsSchemeAndAdministrationIsImmutable() throws Exception {
        Vaccine v = new Vaccine();
        LocalDate saturday = Appointments.now().toLocalDate().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY));
        assertTrue(v.slots(saturday).isEmpty(), "sala fechada no fim de semana");
        LocalDateTime when = businessDay(2).atTime(10, 0);
        Appointments.Result r = v.book(ANA, 2, when, ANA_USER, "portal");
        assertTrue(r.ok(), r.error());
        assertEquals(3, ((Number) Sql.one("SELECT dose FROM vacinas_agendadas WHERE id_agendamento = ?", r.id()).get("dose")).intValue(),
                "Ana ja tem 2 doses de hepatite B");
        assertFalse(v.book(ANA, 2, when.plusDays(1), ANA_USER, "portal").ok(), "one pending dose per vaccine");
        assertFalse(v.book(5, 1, when, ANA_USER, "portal").ok(), "horario ocupado na sala");
        assertFalse(v.cancel((int) r.id(), "nao posso ir", ANA_USER, 5).ok(), "another patient cannot cancel");

        long today;
        try (java.sql.Connection c = database.DBConnection.openConnection()) {
            today = Sql.insert(c, "INSERT INTO vacinas_agendadas (id_paciente, id_vacina, dose, data_hora) VALUES (?, 1, 1, ?)",
                    5, Appointments.now().withHour(23).withMinute(45));
        }
        Map<String, Object> d = new HashMap<>();
        assertTrue(v.apply((int) today, d, 2, 31).containsKey("lote"), "lote obrigatorio");
        d.put("lote", "FLU-TESTE-01");
        assertTrue(v.apply((int) today, d, 2, 31).isEmpty());
        assertEquals("aplicada", Sql.one("SELECT status FROM vacinas_agendadas WHERE id_agendamento = ?", today).get("status"));
        assertThrows(RuntimeException.class, () -> Sql.execute("UPDATE vacinas_aplicadas SET lote = 'X' WHERE id_agendamento = ?", today),
                "registro de aplicacao e imutavel");
        assertEquals(1, ((java.util.List<?>) v.card(5).get("administered")).size());
    }

    @Test
    @Order(7)
    void receptionLinksAccountWhenRecordHasNoEmail() {
        assertFalse(new PatientPortal().linkInReception(2, "paula.portal@exemplo.test", RECEPTION).ok(), "account already linked to another record");
        assertFalse(new PatientPortal().linkInReception(1, "qualquer@exemplo.test", RECEPTION).ok(), "Ana's record already has an account");
        String cpfGabi = String.valueOf(Sql.one("SELECT cpf FROM pacientes WHERE id_paciente = 7").get("cpf"));
        PatientPortal.Registration c = new PatientPortal().register(registration(cpfGabi, "conta.sem.ficha@exemplo.test"));
        assertTrue(c.result().ok());
        assertTrue(new PatientPortal().linkInReception(2, "conta.sem.ficha@exemplo.test", RECEPTION).ok());
        assertEquals(2, PatientPortal.userPatient((int) c.result().id()));
        assertEquals(1, Sql.number("SELECT COUNT(*) FROM auditoria WHERE acao = 'portal.vinculo_recepcao' AND id_paciente = 2"));
    }
}
