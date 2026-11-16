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
class SchedulingIntegrationTest {

    private static final int RECEPTION = 30;      // usuario da recepcao no seed
    private static final int LUCIA = 5;          // medica ficticia: seg-sex 08-12, consultas de 20 min, pediatria (3)
    private static final int PEDIATRICS = 3;
    private static final int CARDIOLOGY = 2;
    private static final int BLOOD_COUNT = 1;      // 10 min, laboratorio (07-12)
    private static final int ULTRASOUND = 5;      // 30 min, imagem (08-17)

    private static String testDb;
    private static LocalDate monday;

    @BeforeAll
    static void createDatabase() {
        assumeTrue(TestDatabase.available(), "Banco indisponivel");
        testDb = TestDatabase.useTemporaryDatabase("hospital_agenda");
        monday = LocalDate.now().plusWeeks(2).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @AfterAll
    static void deleteDatabase() throws Exception {
        if (testDb != null) {
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    private static Map<String, Object> patientData(String name, String cpf) {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", name);
        d.put("cpf", cpf);
        d.put("data_nascimento", "1988-04-10");
        d.put("telefone", "11988887777");
        d.put("email", "teste.ficticio@exemplo.test");
        d.put("uf", "sp");
        return d;
    }

    // ---------------------------------------------------------------- pacientes

    @Test
    void registrationValidatesNormalizesAndPreventsDuplicateCpf() {
        Patient model = new Patient();
        Patient.Result ok = model.create(patientData("Teste Normalizacao Ficticio", "861.442.140-03"), RECEPTION);
        assertTrue(ok.ok(), String.valueOf(ok.errors()));
        Map<String, Object> saved = model.byId((int) ok.id());
        assertEquals("86144214003", saved.get("cpf"), "CPF gravado so com digitos");
        assertEquals("(11) 98888-7777", saved.get("telefone"));
        assertEquals("SP", saved.get("uf"));

        Patient.Result repeated = model.create(patientData("Outro Nome Ficticio", "86144214003"), RECEPTION);
        assertEquals("Já existe um paciente com este CPF.", repeated.errors().get("cpf"));

        Map<String, Object> bad = patientData("Ab", "12345678900");
        bad.put("data_nascimento", "2999-01-01");
        bad.put("email", "sem-arroba");
        bad.put("nome; DROP TABLE pacientes; --", "x");
        Patient.Result errors = model.create(bad, RECEPTION);
        assertEquals(Map.of(
                "nome", "Informe o nome completo.",
                "cpf", "CPF inválido.",
                "data_nascimento", "Data de nascimento inválida.",
                "email", "E-mail inválido."), errors.errors());
    }

    @Test
    void searchByNameAndCpfWithPagination() {
        Patient model = new Patient();
        assertEquals(1, model.search("Elaine", false, 1, 20).total());
        assertEquals(1, model.search("529.982", false, 1, 20).total(), "prefixo de CPF");
        assertEquals(0, model.search("%", false, 1, 20).total(), "curinga do LIKE e tratado como texto");
        Patient.Page p = model.search("", false, 2, 3);
        assertEquals(3, p.items().size());
        assertTrue(p.total() >= 10);
    }

    // ---------------------------------------------------------------- consultas

    @Test
    void scheduleRespectsGridConflictsAndCancellationFreesSlot() {
        Appointments schedule = new Appointments();
        LocalDateTime eightTwenty = monday.atTime(8, 20);

        Appointments.Result first = schedule.book(4, LUCIA, PEDIATRICS, eightTwenty, RECEPTION);
        assertTrue(first.ok(), first.error());

        assertFalse(schedule.book(6, LUCIA, PEDIATRICS, eightTwenty, RECEPTION).ok(), "medico ja ocupado");
        assertFalse(schedule.book(4, LUCIA, PEDIATRICS, monday.atTime(8, 30), RECEPTION).ok(), "fora da grade de 20 min");
        assertFalse(schedule.book(6, LUCIA, PEDIATRICS, monday.atTime(14, 0), RECEPTION).ok(), "fora do horario do medico");
        assertFalse(schedule.book(6, LUCIA, CARDIOLOGY, monday.atTime(9, 0), RECEPTION).ok(), "especialidade de outro medico");
        assertFalse(schedule.book(6, LUCIA, PEDIATRICS, LocalDateTime.now().minusDays(1), RECEPTION).ok(), "no passado");

        List<Appointments.Slot> grade = schedule.slots(LUCIA, monday);
        assertEquals(12, grade.size(), "08:00 a 12:00 em passos de 20 min");
        assertFalse(grade.get(1).free(), "08:20 ocupado");

        assertTrue(schedule.cancel((int) first.id(), "Paciente pediu para remarcar", RECEPTION).ok());
        assertTrue(schedule.slots(LUCIA, monday).get(1).free(), "cancelamento libera o horario");
        assertTrue(schedule.book(6, LUCIA, PEDIATRICS, eightTwenty, RECEPTION).ok());
    }

    @Test
    void twoReceptionistsAtSameTimeOnlyOneSaves() throws Exception {
        LocalDateTime nine = monday.plusDays(1).atTime(9, 0);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Appointments.Result>> tasks = new ArrayList<>();
            int[] patients = {7, 8, 9, 10};
            for (int p : patients) {
                tasks.add(() -> new Appointments().book(p, LUCIA, PEDIATRICS, nine, RECEPTION));
            }
            int saved = 0;
            for (Future<Appointments.Result> f : pool.invokeAll(tasks)) {
                if (f.get().ok()) {
                    saved++;
                }
            }
            assertEquals(1, saved);
            assertEquals(1, Sql.number("SELECT COUNT(*) FROM consultas WHERE id_medico = ? AND data_hora = ? AND slot_ativo = 1", LUCIA, nine));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void arrivalOnlyOnAppointmentDay() {
        Appointments schedule = new Appointments();
        Appointments.Result r = schedule.book(5, LUCIA, PEDIATRICS, monday.plusDays(2).atTime(10, 0), RECEPTION);
        assertTrue(r.ok(), r.error());
        Appointments.Result arrival = schedule.recordArrival((int) r.id(), RECEPTION);
        assertEquals("A chegada só pode ser registrada no dia da consulta.", arrival.error());
    }

    // ---------------------------------------------------------------- exames

    @Test
    void examDoesNotOverlapAtSameLocationAndUsesTypeDuration() {
        ExamSchedule exams = new ExamSchedule();
        LocalDateTime tenAm = monday.atTime(10, 0);
        Appointments.Result us = exams.book(4, ULTRASOUND, tenAm, null, RECEPTION);
        assertTrue(us.ok(), us.error());

        assertFalse(exams.book(6, ULTRASOUND, monday.atTime(10, 20), null, RECEPTION).ok(), "sobrepoe os 30 min do ultrassom");
        assertTrue(exams.book(6, ULTRASOUND, monday.atTime(10, 30), null, RECEPTION).ok(), "logo apos o termino");
        assertTrue(exams.book(7, BLOOD_COUNT, tenAm, null, RECEPTION).ok(), "outro local, mesmo horario");
        assertFalse(exams.book(4, BLOOD_COUNT, monday.atTime(10, 10), null, RECEPTION).ok(), "mesmo paciente ocupado (10:00-10:30)");
        assertFalse(exams.book(8, BLOOD_COUNT, monday.atTime(12, 0), null, RECEPTION).ok(), "laboratorio fecha as 12:00");

        assertTrue(exams.cancel((int) us.id(), "Paciente sem jejum", RECEPTION).ok());
        assertTrue(exams.book(8, ULTRASOUND, tenAm, null, RECEPTION).ok(), "cancelamento libera o horario");
    }

    @Test
    void examNotBookedOnSundayNorWithoutActivePatient() {
        ExamSchedule exams = new ExamSchedule();
        assertTrue(exams.slots(BLOOD_COUNT, monday.minusDays(1)).isEmpty(), "domingo");
        assertFalse(exams.slots(BLOOD_COUNT, monday.minusDays(2)).isEmpty(), "laboratorio atende sabado");
        assertTrue(exams.slots(ULTRASOUND, monday.minusDays(2)).isEmpty(), "imagem nao atende sabado");

        new Patient().setActive(10, false, RECEPTION);
        assertEquals("Paciente inativo ou inexistente.",
                exams.book(10, BLOOD_COUNT, monday.atTime(7, 0), null, RECEPTION).error());
        new Patient().setActive(10, true, RECEPTION);
    }

    @Test
    void dashboardCountsFromRows() {
        Dashboard dashboard = new Dashboard();
        LocalDate day = monday.plusDays(3);
        long before = dashboard.dayAppointments(day);
        assertTrue(new Appointments().book(9, LUCIA, PEDIATRICS, day.atTime(11, 0), RECEPTION).ok());
        assertEquals(before + 1, dashboard.dayAppointments(day));
        assertEquals(0, dashboard.dayCancelled(day));
    }
}
