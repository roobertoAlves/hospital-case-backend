package controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.Sql;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.Appointments;
import models.Certificate;
import models.ClinicalAccess;
import models.Emergency;
import models.Encounter;
import models.ExamRequest;
import models.PatientPortal;
import models.Referral;
import models.Triage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import support.TestDatabase;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmergencyAndDocumentsIntegrationTest {

    private static final int RECEPTION_USER = 30;
    private static final int NURSE_USER = 31;
    private static final int NURSE = 2;
    private static final int DOCTOR_USER = 32;
    private static final int MARINA = 3;

    private static String testDb;
    private static int otavio;
    private static int livia;
    private static int liviaUser;
    private static int walkInPatient;
    private static int scheduledPatient;
    private static int walkIn;
    private static int scheduled;

    @BeforeAll
    static void prepare() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        testDb = TestDatabase.useTemporaryDatabase("hospital_emergencia");
        otavio = id("SELECT id_profissional FROM profissionais WHERE email = 'plantao@hospital.local'");
        livia = id("SELECT id_profissional FROM profissionais WHERE email = 'livia.plantao@hospital.local'");
        liviaUser = id("SELECT id_usuario FROM usuarios WHERE email = 'livia.plantao@hospital.local'");
        List<Map<String, Object>> free = Sql.list("SELECT id_paciente FROM pacientes p WHERE ativo = 1 AND NOT EXISTS (SELECT 1 FROM consultas c "
                + "WHERE c.id_paciente = p.id_paciente AND c.status IN ('agendada', 'aguardando_triagem', 'em_triagem', 'aguardando_atendimento', "
                + "'em_atendimento')) ORDER BY id_paciente LIMIT 2");
        walkInPatient = ((Number) free.get(0).get("id_paciente")).intValue();
        scheduledPatient = ((Number) free.get(1).get("id_paciente")).intValue();
        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            scheduled = (int) Sql.insert(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, status, chegada_em) "
                    + "VALUES (?, ?, 1, DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), 'aguardando_triagem', NOW())", scheduledPatient, MARINA);
        }
    }

    @AfterAll
    static void deleteDatabase() throws Exception {
        if (testDb != null) {
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    private static int id(String sql) {
        Map<String, Object> r = Sql.one(sql);
        assertNotNull(r, sql);
        return ((Number) r.values().iterator().next()).intValue();
    }

    private static Map<String, Object> signs(String classification) {
        Map<String, Object> d = new HashMap<>();
        d.put("pa_sistolica", "160");
        d.put("pa_diastolica", "100");
        d.put("frequencia_cardiaca", "118");
        d.put("saturacao_o2", "91");
        d.put("queixa_principal", "Dor no peito irradiando para o braço");
        d.put("id_classificacao", classification);
        return d;
    }

    @Test
    @Order(1)
    void emergencySpecialtyIsSeparateFromBooking() {
        Integer specialty = Emergency.specialtyId();
        assertNotNull(specialty);
        assertTrue(Emergency.isEmergencyDoctor(otavio));
        assertTrue(Emergency.isEmergencyDoctor(livia));
        assertFalse(Emergency.isEmergencyDoctor(MARINA));
        assertTrue(new Appointments().activeSpecialties().stream().noneMatch(s -> specialty.equals(((Number) s.get("id_especialidade")).intValue())),
                "emergency is not offered for booking");
    }

    @Test
    @Order(2)
    void receptionOpensWalkInEmergencyThatGoesFirstInTriage() {
        Emergency e = new Emergency();
        assertFalse(e.open(walkInPatient, "", RECEPTION_USER).ok(), "needs a reason");
        Appointments.Result r = e.open(walkInPatient, "Desmaio na sala de espera", RECEPTION_USER);
        assertTrue(r.ok(), r.error());
        walkIn = (int) r.id();
        assertFalse(e.open(walkInPatient, "Outra entrada", RECEPTION_USER).ok(), "patient already in the queue");
        Map<String, Object> c = Sql.one("SELECT tipo, status, id_especialidade FROM consultas WHERE id_consulta = ?", walkIn);
        assertEquals("emergencia", c.get("tipo"));
        assertEquals("aguardando_triagem", c.get("status"));
        assertEquals(Emergency.specialtyId(), ((Number) c.get("id_especialidade")).intValue());
        assertEquals("emergencia", new Triage().queue().get(0).get("tipo"), "emergencies lead the triage queue");
    }

    @Test
    @Order(3)
    void criticalTriageMovesScheduledVisitToEmergency() {
        Triage t = new Triage();
        assertTrue(t.start(scheduled, NURSE_USER));
        Triage.Result r = t.record(scheduled, NURSE, signs("1"), NURSE_USER);
        assertTrue(r.ok(), String.valueOf(r.errors()));
        assertTrue(r.emergency(), "red classification goes to the emergency doctor");
        Map<String, Object> c = Sql.one("SELECT tipo, id_medico, medico_original, slot_ativo FROM consultas WHERE id_consulta = ?", scheduled);
        assertEquals("emergencia", c.get("tipo"));
        assertEquals(MARINA, ((Number) c.get("medico_original")).intValue());
        assertNull(c.get("slot_ativo"), "the specialist's slot is released");
        assertTrue(Emergency.isEmergencyDoctor(((Number) c.get("id_medico")).intValue()));
    }

    @Test
    @Order(4)
    void anyOnCallDoctorTakesTheCase() {
        assertTrue(new Encounter().doctorQueue(livia).stream().anyMatch(q -> ((Number) q.get("id_consulta")).intValue() == scheduled));
        assertTrue(new Encounter().doctorQueue(MARINA).stream().noneMatch(q -> ((Number) q.get("id_consulta")).intValue() == scheduled),
                "the specialist no longer sees it");
        assertTrue(ClinicalAccess.doctorCanAccessPatient(livia, scheduledPatient));
        assertTrue(new Encounter().start(scheduled, livia, liviaUser).ok());
        assertEquals(livia, id("SELECT id_medico FROM consultas WHERE id_consulta = " + scheduled));
    }

    @Test
    @Order(5)
    void examRequestComesFromTheCatalog() {
        Map<String, Object> type = Sql.one("SELECT id_tipo_exame, nome, codigo FROM tipos_exame WHERE ativo = 1 ORDER BY id_tipo_exame LIMIT 1");
        Map<String, Object> d = new HashMap<>();
        d.put("id_tipo_exame", type.get("id_tipo_exame"));
        d.put("justificativa", "Investigar dor torácica");
        ExamRequest x = new ExamRequest();
        assertTrue(x.request(scheduled, livia, d, liviaUser).ok());
        assertFalse(x.request(scheduled, livia, d, liviaUser).ok(), "same exam twice in the visit");
        Map<String, Object> saved = x.ofAppointment(scheduled).get(0);
        assertEquals(type.get("nome"), saved.get("nome_exame"));
        assertEquals(type.get("codigo"), saved.get("codigo"));
        assertTrue(new PatientPortal().openRequests(scheduledPatient).stream()
                .anyMatch(o -> type.get("id_tipo_exame").equals(o.get("id_tipo_exame"))), "the patient sees the request");
    }

    @Test
    @Order(6)
    void certificatesFollowTheRules() {
        Certificate c = new Certificate();
        Map<String, Object> d = new HashMap<>();
        d.put("tipo", "afastamento");
        d.put("dias", "3");
        d.put("cid10", "I20.9");
        assertFalse(c.issue(scheduled, livia, d, liviaUser).ok(), "CID needs the patient's consent");
        d.put("cid_autorizado", "1");
        Certificate.Result ok = c.issue(scheduled, livia, d, liviaUser);
        assertTrue(ok.ok(), String.valueOf(ok.errors()));
        Map<String, Object> visit = new HashMap<>();
        visit.put("tipo", "comparecimento");
        visit.put("hora_inicio", "10:00");
        visit.put("hora_fim", "09:00");
        assertFalse(c.issue(scheduled, livia, visit, liviaUser).ok(), "leaving before arriving");
        assertFalse(c.issue(scheduled, MARINA, d, DOCTOR_USER).ok(), "only the visit's doctor");
        assertNotNull(c.forPatientPrint((int) ok.id(), scheduledPatient));
        assertNull(c.forPatientPrint((int) ok.id(), walkInPatient), "another patient cannot open it");
        assertTrue(c.cancel((int) ok.id(), livia, "Emitido com dias errados", liviaUser).ok());
        assertEquals("cancelado", c.ofPatient(scheduledPatient).get(0).get("status"));
    }

    @Test
    @Order(7)
    void referralIsClosedWhenTheSpecialtyIsBooked() throws Exception {
        Map<String, Object> d = new HashMap<>();
        d.put("id_especialidade", Emergency.specialtyId());
        d.put("motivo", "Avaliação especializada");
        assertFalse(new Referral().issue(scheduled, livia, d, liviaUser).ok(), "cannot refer to the emergency room");
        d.put("id_especialidade", "2");
        d.put("prioridade", "prioritario");
        assertTrue(new Referral().issue(scheduled, livia, d, liviaUser).ok());
        assertEquals(1, new Referral().openOfPatient(scheduledPatient).size());
        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            long booked = Sql.insert(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, status) "
                    + "VALUES (?, 101, 2, DATE_ADD(NOW(), INTERVAL 30 DAY), 'agendada')", scheduledPatient);
            conn.setAutoCommit(false);
            java.lang.reflect.Method m = Referral.class.getDeclaredMethod("markScheduled", java.sql.Connection.class, int.class, int.class, long.class);
            m.setAccessible(true);
            m.invoke(null, conn, scheduledPatient, 2, booked);
            conn.commit();
        }
        assertEquals(0, new Referral().openOfPatient(scheduledPatient).size());
        assertEquals("agendado", new Referral().ofPatient(scheduledPatient).get(0).get("status"));
    }

    @Test
    @Order(8)
    void administratorKeepsOnlyAdministrativeAccess() {
        List<String> perms = Sql.list("SELECT p.codigo FROM permissoes p JOIN grupos_permissoes gp ON gp.id_permissao = p.id_permissao "
                + "JOIN grupos g ON g.id_grupo = gp.id_grupo WHERE g.nome = 'admin'").stream().map(r -> String.valueOf(r.get("codigo"))).toList();
        for (String clinical : List.of("agenda.view", "exames.agenda.view", "vacinas.agenda.view", "pacientes.view", "prontuario.view")) {
            assertFalse(perms.contains(clinical), "admin must not have " + clinical);
        }
        assertTrue(perms.contains("exames.catalogo.manage"));
        assertTrue(perms.contains("profissionais.manage"));
    }
}
