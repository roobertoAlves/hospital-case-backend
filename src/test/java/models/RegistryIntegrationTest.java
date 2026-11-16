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
 * Professional and specialty registry, weekly availability, rescheduling and the audit log
 * search, on a temporary database.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistryIntegrationTest {

    private static final int ADMIN = 1;
    private static final int RECEPTION = 30;
    private static final int MARINA = 3;
    private static final int PATIENT = 1;

    private static String testDb;
    private static long newDoctor;
    private static long originalAppointment;
    private static long rescheduledAppointment;

    @BeforeAll
    static void prepare() {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        testDb = TestDatabase.useTemporaryDatabase("hospital_cadastros");
    }

    @AfterAll
    static void delete() throws Exception {
        if (testDb != null) {
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    private static Map<String, Object> doctor(String crm, String esp) {
        Map<String, Object> d = new HashMap<>();
        d.put("tipo", "medico");
        d.put("nome", "Carlos Cardio (ficticio)");
        d.put("email", "carlos.cardio@hospital.local");
        d.put("telefone", "11988887777");
        d.put("crm", crm);
        d.put("crm_uf", "sp");
        d.put("duracao_consulta_min", "20");
        d.put("specialty_ids", esp);
        return d;
    }

    /** Next Monday from tomorrow on (Marina works Mondays, 08:00-12:00). */
    private static LocalDate nextMonday() {
        LocalDate d = Appointments.now().toLocalDate().plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    @Test
    @Order(1)
    void validatesLicenseSpecialtiesAndLink() {
        Professional p = new Professional();
        assertTrue(p.create(doctor("000001", "1"), ADMIN).errors().containsKey("crm"), "CRM 000001/SP ja e da Marina");
        assertTrue(p.create(doctor("123456", ""), ADMIN).errors().containsKey("specialty_ids"));
        assertTrue(p.create(doctor("123456", "999"), ADMIN).errors().containsKey("specialty_ids"), "invalid specialtyexistente");

        Map<String, Object> linked = doctor("123456", "1");
        linked.put("id_usuario", "32");
        assertTrue(p.create(linked, ADMIN).errors().containsKey("id_usuario"), "user 32 is already Marina");

        Map<String, Object> nurse = new HashMap<>();
        nurse.put("tipo", "enfermeiro");
        nurse.put("nome", "Outra Enfermeira");
        nurse.put("coren", "000001");
        nurse.put("coren_uf", "SP");
        assertTrue(p.create(nurse, ADMIN).errors().containsKey("coren"), "COREN repetido");

        Map<String, Object> func = new HashMap<>();
        func.put("tipo", "funcionario");
        func.put("nome", "Fulano Sem Cargo");
        assertTrue(p.create(func, ADMIN).errors().containsKey("cargo"));

        Map<String, Object> type = new HashMap<>();
        type.put("tipo", "administrador");
        assertTrue(p.create(type, ADMIN).errors().containsKey("tipo"));

        Map<String, Object> repeatedAccess = doctor("123456", "1");
        repeatedAccess.put("email", "medico@hospital.local");
        repeatedAccess.put("create_access", "1");
        assertTrue(p.create(repeatedAccess, ADMIN).errors().containsKey("email"), "e-mail of an existing user");
    }

    @Test
    @Order(2)
    void createsDoctorWithAccessInRightGroupAndTemporaryPassword() {
        Map<String, Object> d = doctor("123456", "2,1");
        d.put("create_access", "1");
        Professional.Result r = new Professional().create(d, ADMIN);
        assertTrue(r.ok(), () -> "erros: " + r.errors());
        newDoctor = r.id();
        assertNotNull(r.temporaryPassword());
        assertEquals("carlos.cardio@hospital.local", r.accessEmail());

        Map<String, Object> u = Sql.one("SELECT u.id_usuario, u.primeiro_acesso, u.senha_hash, g.nome AS grupo FROM usuarios u "
                + "JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario JOIN grupos g ON g.id_grupo = ug.id_grupo WHERE u.email = ?",
                "carlos.cardio@hospital.local");
        assertEquals("medico", u.get("grupo"));
        assertEquals("1", String.valueOf(u.get("primeiro_acesso")), "password change required on first access");
        assertTrue(utils.PasswordSecurity.verify(r.temporaryPassword(), String.valueOf(u.get("senha_hash"))));

        Map<String, Object> show = new Professional().byId((int) newDoctor);
        assertEquals("SP", show.get("crm_uf"), "UF normalizada");
        assertEquals("(11) 98888-7777", show.get("telefone"));
        assertEquals(2, ((List<?>) show.get("specialty_ids")).size());
        assertNotNull(new Professional().byUser(((Number) u.get("id_usuario")).intValue()));
        assertEquals(1, Sql.number("SELECT COUNT(*) FROM auditoria WHERE acao = 'profissional.criado' AND id_entidade = ?", newDoctor));
        assertEquals(1, Sql.number("SELECT COUNT(*) FROM auditoria WHERE acao = 'usuario.criado'"));
    }

    @Test
    @Order(3)
    void updatesWithoutChangingType() {
        Map<String, Object> d = doctor("123456", "2");
        d.put("tipo", "enfermeiro");
        d.put("duracao_consulta_min", "40");
        d.put("id_usuario", String.valueOf(Sql.one("SELECT id_usuario FROM profissionais WHERE id_profissional = ?", newDoctor).get("id_usuario")));
        Professional.Result r = new Professional().update((int) newDoctor, d, ADMIN);
        assertTrue(r.ok(), () -> "erros: " + r.errors());
        Map<String, Object> show = new Professional().byId((int) newDoctor);
        assertEquals("medico", show.get("tipo"));
        assertEquals(40, ((Number) show.get("duracao_consulta_min")).intValue());
        assertEquals(List.of(2), show.get("specialty_ids"));

        Map<String, Object> bad = doctor("123456", "2");
        bad.put("duracao_consulta_min", "17");
        assertTrue(new Professional().update((int) newDoctor, bad, ADMIN).errors().containsKey("duracao_consulta_min"));
    }

    @Test
    @Order(4)
    void availabilityRejectsOverlapAndFeedsSlots() {
        Professional p = new Professional();
        List<Map<String, Object>> overlapping = List.of(range(2, "08:00", "12:00"), range(2, "11:00", "13:00"));
        assertTrue(p.saveAvailability((int) newDoctor, overlapping, ADMIN).containsKey("range_1"));
        assertTrue(p.saveAvailability((int) newDoctor, List.of(range(2, "12:00", "08:00")), ADMIN).containsKey("range_0"));
        assertTrue(p.saveAvailability(2, List.of(range(2, "08:00", "12:00")), ADMIN).containsKey("_"), "a nurse has no schedule");

        Map<String, String> ok = p.saveAvailability((int) newDoctor, List.of(range(2, "08:00", "10:00"), range(4, "14:00", "16:00")), ADMIN);
        assertTrue(ok.isEmpty(), () -> "erros: " + ok);
        LocalDate tuesday = nextMonday().plusDays(1);
        List<Appointments.Slot> slots = new Appointments().slots((int) newDoctor, tuesday);
        assertEquals(3, slots.size(), "08:00-10:00 em consultas de 40 min: 08:00, 08:40, 09:20");
        assertEquals(LocalTime.of(8, 40), slots.get(1).time());
        assertTrue(new Appointments().slots((int) newDoctor, tuesday.plusDays(1)).isEmpty(), "Wednesday without a range");
    }

    private static Map<String, Object> range(int day, String ini, String end) {
        Map<String, Object> f = new HashMap<>();
        f.put("dia_semana", (double) day);
        f.put("hora_inicio", ini);
        f.put("hora_fim", end);
        return f;
    }

    @Test
    @Order(5)
    void rescheduleCancelsOriginalAndCreatesLinked() {
        Appointments schedule = new Appointments();
        // Four weeks ahead: beyond the appointments generated by the demo migration (003).
        LocalDateTime monday8 = nextMonday().plusWeeks(4).atTime(8, 0);
        Appointments.Result a = schedule.book(PATIENT, MARINA, 1, monday8, RECEPTION);
        assertTrue(a.ok(), () -> a.error());
        originalAppointment = a.id();

        assertFalse(schedule.reschedule((int) originalAppointment, MARINA, monday8, "", RECEPTION).ok(), "mesmo horario");
        assertFalse(schedule.reschedule((int) originalAppointment, MARINA, nextMonday().minusWeeks(2).atTime(8, 0), "", RECEPTION).ok(), "passado");
        assertFalse(schedule.reschedule((int) originalAppointment, MARINA, monday8.withHour(7), "", RECEPTION).ok(), "fora da grade");

        Appointments.Result r = schedule.reschedule((int) originalAppointment, MARINA, monday8.plusMinutes(30), "Pedido do paciente", RECEPTION);
        assertTrue(r.ok(), () -> r.error());
        rescheduledAppointment = r.id();

        Map<String, Object> old = Sql.one("SELECT status, motivo_cancelamento, cancelada_por FROM consultas WHERE id_consulta = ?", originalAppointment);
        assertEquals("cancelada", old.get("status"));
        assertTrue(String.valueOf(old.get("motivo_cancelamento")).startsWith("Remarcada para "));
        assertTrue(String.valueOf(old.get("motivo_cancelamento")).endsWith("Pedido do paciente"));
        Map<String, Object> newForm = Sql.one("SELECT status, remarcada_de, id_paciente FROM consultas WHERE id_consulta = ?", rescheduledAppointment);
        assertEquals("agendada", newForm.get("status"));
        assertEquals(originalAppointment, ((Number) newForm.get("remarcada_de")).longValue());
        assertEquals(1, Sql.number("SELECT COUNT(*) FROM auditoria WHERE acao = 'consulta.remarcada' AND id_entidade = ?", rescheduledAppointment));

        assertFalse(schedule.reschedule((int) originalAppointment, MARINA, monday8.plusHours(1), "", RECEPTION).ok(), "a original ja esta cancelada");
        Appointments.Result taken = schedule.book(2, MARINA, 1, monday8.plusHours(2), RECEPTION);
        assertTrue(taken.ok());
        assertFalse(schedule.reschedule((int) rescheduledAppointment, MARINA, monday8.plusHours(2), "", RECEPTION).ok(), "horario ocupado");
        assertEquals("agendada", Sql.one("SELECT status FROM consultas WHERE id_consulta = ?", rescheduledAppointment).get("status"),
                "falha na remarcacao desfaz o cancelamento (mesma transacao)");
        schedule.cancel((int) taken.id(), "test cleanup", RECEPTION);
    }

    @Test
    @Order(6)
    void cannotDeactivateWithFutureAppointments() {
        assertNotNull(new Professional().setActive(MARINA, false, ADMIN), "Marina has a scheduled appointment");
        assertNotNull(new Specialty().setActive(1, false));
        new Appointments().cancel((int) rescheduledAppointment, "test cleanup", RECEPTION);
        assertNull(new Professional().setActive((int) newDoctor, false, ADMIN));
        assertEquals("0", String.valueOf(Sql.one("SELECT ativo FROM profissionais WHERE id_profissional = ?", newDoctor).get("ativo")));
        assertNull(new Professional().byUser(((Number) Sql.one("SELECT id_usuario FROM profissionais WHERE id_profissional = ?",
                newDoctor).get("id_usuario")).intValue()), "inativo perde o vinculo clinico");
        assertNull(new Professional().setActive((int) newDoctor, true, ADMIN));
    }

    @Test
    @Order(7)
    void specialtyWithUniqueName() {
        Specialty e = new Specialty();
        long[] id = new long[1];
        Map<String, Object> d = new HashMap<>();
        d.put("nome", "Cardiologia");
        assertTrue(e.save(0, d, ADMIN, id).containsKey("nome"), "nome repetido");
        d.put("nome", "Hematologia");
        d.put("descricao", "Doencas do sangue");
        assertTrue(e.save(0, d, ADMIN, id).isEmpty());
        d.put("nome", "Hematologia Clínica");
        assertTrue(e.save((int) id[0], d, ADMIN, new long[1]).isEmpty());
        assertEquals("Hematologia Clínica", e.byId((int) id[0]).get("nome"));
        assertNull(e.setActive((int) id[0], false));
        assertFalse(e.active().stream().anyMatch(x -> "Hematologia Clínica".equals(x.get("nome"))));
    }

    @Test
    @Order(8)
    void nameWithHtmlIsSavedWithoutTags() {
        Map<String, Object> d = new HashMap<>();
        d.put("tipo", "funcionario");
        d.put("nome", "<script>alert(1)</script>Joana <b>Silva</b>");
        d.put("cargo", "Recepcionista <img src=x onerror=alert(2)>");
        Professional.Result r = new Professional().create(d, ADMIN);
        assertTrue(r.ok(), () -> "erros: " + r.errors());
        Map<String, Object> p = Sql.one("SELECT nome, cargo FROM profissionais WHERE id_profissional = ?", r.id());
        assertEquals("Joana Silva", p.get("nome"));
        assertFalse(String.valueOf(p.get("cargo")).contains("<"), "tags removidas antes de gravar");
    }

    @Test
    @Order(9)
    void auditQueryFiltersAndMasks() {
        AuditLogQuery c = new AuditLogQuery();
        LocalDate today = Appointments.now().toLocalDate();
        AuditLogQuery.Page prof = c.search(new AuditLogQuery.Filter(today.minusDays(1), today.plusDays(1), null, "profissional", null, 1));
        assertTrue(prof.total() >= 3, "created, updated, availability");
        assertTrue(prof.items().stream().allMatch(i -> String.valueOf(i.get("acao")).startsWith("profissional.")));
        AuditLogQuery.Page pac = c.search(new AuditLogQuery.Filter(today.minusDays(1), today.plusDays(1), RECEPTION, null, PATIENT, 1));
        assertTrue(pac.items().stream().allMatch(i -> ((Number) i.get("id_paciente")).intValue() == PATIENT));
        assertTrue(c.modules().contains("consulta"));
        assertEquals("an***@exemplo.test", AuditLogQuery.maskedEmail("ana.ficticia@exemplo.test"));
        assertEquals("a***@x.test", AuditLogQuery.maskedEmail("a@x.test"));
    }
}
