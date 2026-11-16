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
import models.Encounter;
import models.Prescription;
import models.ExamRequest;
import models.Triage;
import models.ClinicalAccess;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import support.TestDatabase;

/**
 * Full clinical flow on a temporary database: arrival -> triage (with amendment) -> encounter
 * (draft, versions, immutable completion, addendum) -> prescription -> exam -> history.
 * Includes access attempts by someone without a clinical link (another doctor).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClinicalFlowIntegrationTest {

    private static final int NURSE_USER = 31;
    private static final int NURSE = 2;
    private static final int DOCTOR_USER = 32;
    private static final int MARINA = 3;          // the appointment's doctor
    private static final int PAULO = 4;           // another doctor, without a clinical link
    private static final int PATIENT = 6;

    private static String testDb;
    private static int appointment;

    @BeforeAll
    static void prepareArrival() throws Exception {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        testDb = TestDatabase.useTemporaryDatabase("hospital_fluxo");
        // Reception already recorded the arrival: today's appointment awaiting triage.
        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            appointment = (int) Sql.insert(conn, "INSERT INTO consultas (id_paciente, id_medico, id_especialidade, data_hora, status, chegada_em) "
                    + "VALUES (?, ?, 1, DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), 'aguardando_triagem', NOW())", PATIENT, MARINA);
        }
    }

    @AfterAll
    static void deleteDatabase() throws Exception {
        if (testDb != null) {
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    private static Map<String, Object> signs() {
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
    void doctorCannotSeePatientBeforeTriage() {
        Encounter.Result r = new Encounter().start(appointment, MARINA, DOCTOR_USER);
        assertFalse(r.ok());
        assertFalse(ClinicalAccess.doctorCanAccessPatient(MARINA, PATIENT), "without triage the doctor has no clinical link yet");
        assertTrue(ClinicalAccess.nurseCanAccessPatient(NURSE, PATIENT), "patient in the triage queue");
    }

    @Test
    @Order(2)
    void onlyOneNurseStartsAndTriageValidatesRanges() {
        Triage t = new Triage();
        assertTrue(t.start(appointment, NURSE_USER));
        assertFalse(t.start(appointment, NURSE_USER), "a second attempt sees the triage already in progress");

        Map<String, Object> bad = signs();
        bad.put("saturacao_o2", "150");
        bad.put("pa_diastolica", "");
        bad.remove("id_classificacao");
        Triage.Result errors = t.record(appointment, NURSE, bad, NURSE_USER);
        assertFalse(errors.ok());
        assertTrue(errors.errors().containsKey("saturacao_o2"), String.valueOf(errors.errors()));
        assertTrue(errors.errors().containsKey("id_classificacao"), String.valueOf(errors.errors()));

        Triage.Result ok = t.record(appointment, NURSE, signs(), NURSE_USER);
        assertTrue(ok.ok(), String.valueOf(ok.errors()));
        assertEquals("aguardando_atendimento", status());
        assertEquals("37.8", new java.math.BigDecimal(String.valueOf(t.current(appointment).get("temperatura"))).stripTrailingZeros().toPlainString());
        assertFalse(t.record(appointment, NURSE, signs(), NURSE_USER).ok(), "no triage twice");
    }

    @Test
    @Order(3)
    void doctorQueueAndOtherDoctorWithoutAccess() {
        Encounter a = new Encounter();
        List<Map<String, Object>> queue = a.doctorQueue(MARINA);
        Map<String, Object> our = queue.stream().filter(c -> ((Number) c.get("id_consulta")).intValue() == appointment)
                .findFirst().orElse(null);
        assertNotNull(our, "the triaged appointment enters the doctor's queue");
        assertEquals(3, ((Number) our.get("ordem")).intValue(), "yellow priority from triage");
        assertTrue(a.doctorQueue(PAULO).stream().noneMatch(c -> ((Number) c.get("id_consulta")).intValue() == appointment),
                "not shown in another doctor's queue");
        assertNull(a.doctorAppointment(appointment, PAULO), "IDOR: another doctor's appointment is not loaded");
        assertFalse(a.start(appointment, PAULO, DOCTOR_USER).ok());
        assertFalse(ClinicalAccess.doctorCanAccessPatient(PAULO, PATIENT));
        assertEquals(PatientHistoryController.AccessLevel.DENIED, PatientHistoryController.level(List.of("medico"), PAULO, 0, PATIENT));
    }

    @Test
    @Order(4)
    void encounterWithVersionedDraftAndTriageAmendment() {
        Encounter a = new Encounter();
        assertTrue(a.start(appointment, MARINA, DOCTOR_USER).ok());
        Map<String, Object> c = a.doctorAppointment(appointment, MARINA);
        assertEquals("em_atendimento", c.get("status"));
        assertEquals("Dor de garganta e febre há dois dias", c.get("queixa_principal"), "complaint pre-filled from triage");

        // Nursing can still amend while the encounter is not over.
        Triage t = new Triage();
        int triageId = ((Number) t.current(appointment).get("id_triagem")).intValue();
        Map<String, Object> corrected = signs();
        corrected.put("id_classificacao", "2");
        assertFalse(t.amend(triageId, NURSE, corrected, "", NURSE_USER).ok(), "motivo obrigatorio");
        Triage.Result ret = t.amend(triageId, NURSE, corrected, "Saturação reavaliada", NURSE_USER);
        assertTrue(ret.ok(), String.valueOf(ret.errors()));
        assertEquals(2, ((Number) t.current(appointment).get("versoes")).intValue());
        assertFalse(t.amend(triageId, NURSE, corrected, "de novo", NURSE_USER).ok(), "a anterior ja foi retificada");

        Map<String, Object> draft = new HashMap<>();
        draft.put("queixa_principal", "Odinofagia e febre");
        draft.put("historia_doenca_atual", "Início há 48h.");
        assertTrue(a.saveDraft(appointment, MARINA, draft, DOCTOR_USER).ok());
        assertTrue(a.saveDraft(appointment, MARINA, draft, DOCTOR_USER).ok(), "saving the same content creates no version");
        assertEquals(1, a.versionCount(appointment));
        assertEquals(2, ((Number) a.doctorAppointment(appointment, MARINA).get("versao")).intValue());
        assertFalse(a.saveDraft(appointment, PAULO, draft, DOCTOR_USER).ok(), "so o autor altera");
        Map<String, Object> partial = new HashMap<>();
        partial.put("queixa_principal", "Odinofagia e febre");
        assertTrue(a.saveDraft(appointment, MARINA, partial, DOCTOR_USER).ok());
        assertEquals("Início há 48h.", a.doctorAppointment(appointment, MARINA).get("historia_doenca_atual"),
                "a field not sent is not erased");

        assertTrue(a.recordProgressNote(appointment, MARINA, "evolucao", "Reavaliado após antitérmico.", DOCTOR_USER).ok());
        assertFalse(a.recordProgressNote(appointment, MARINA, "adendo", "Antes de finalizar", DOCTOR_USER).ok());
        assertTrue(a.recordAllergy(PATIENT, "Dipirona", "Urticária", "moderada", DOCTOR_USER).ok());
        assertEquals(1, a.allergies(PATIENT).size());
    }

    @Test
    @Order(5)
    void prescriptionAndExamDuringEncounter() {
        Prescription rec = new Prescription();
        assertFalse(rec.issue(appointment, MARINA, null, List.of(), DOCTOR_USER).ok());
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("medicamento", "Paracetamol");
        assertFalse(rec.issue(appointment, MARINA, null, List.of(incomplete), DOCTOR_USER).ok());

        Map<String, Object> item = Map.of("medicamento", "Paracetamol", "dosagem", "750 mg", "via", "oral",
                "frequencia", "6 em 6 horas se febre", "duracao", "5 dias");
        Map<String, Object> item2 = Map.of("medicamento", "Amoxicilina", "dosagem", "500 mg",
                "frequencia", "8 em 8 horas", "duracao", "7 dias");
        assertFalse(rec.issue(appointment, PAULO, null, List.of(item), DOCTOR_USER).ok(), "another doctor cannot prescribe");
        Prescription.Result r = rec.issue(appointment, MARINA, "Hidratação e repouso.", List.of(item, item2), DOCTOR_USER);
        assertTrue(r.ok(), String.valueOf(r.errors()));
        Map<String, Object> print = rec.forPrint((int) r.id(), MARINA);
        assertEquals(2, ((List<?>) print.get("items")).size());
        assertNotNull(print.get("crm"));
        assertNull(rec.forPrint((int) r.id(), PAULO), "IDOR na impressao");
        assertFalse(rec.cancel((int) r.id(), MARINA, "", DOCTOR_USER).ok());
        assertTrue(rec.cancel((int) r.id(), MARINA, "Dose ajustada", DOCTOR_USER).ok());
        assertFalse(rec.cancel((int) r.id(), MARINA, "Dose ajustada", DOCTOR_USER).ok(), "ja cancelada");

        ExamRequest ex = new ExamRequest();
        Map<String, Object> order = new HashMap<>();
        order.put("nome_exame", "Hemograma completo");
        order.put("justificativa", "Febre persistente");
        assertTrue(ex.request(appointment, MARINA, order, DOCTOR_USER).ok());
        assertFalse(ex.request(appointment, PAULO, order, DOCTOR_USER).ok());
        assertEquals(1, ex.ofAppointment(appointment).size());
    }

    @Test
    @Order(6)
    void finishingMakesMedicalRecordImmutable() throws Exception {
        Encounter a = new Encounter();
        Map<String, Object> withoutPlan = new HashMap<>();
        withoutPlan.put("queixa_principal", "Odinofagia e febre");
        withoutPlan.put("historia_doenca_atual", "Início há 48h.");
        assertFalse(a.finish(appointment, MARINA, withoutPlan, DOCTOR_USER).ok(), "conduta obrigatoria");

        Map<String, Object> complete = new HashMap<>(withoutPlan);
        complete.put("hipoteses_diagnosticas", "Faringite aguda");
        complete.put("conduta", "Sintomáticos e retorno se piora.");
        assertTrue(a.finish(appointment, MARINA, complete, DOCTOR_USER).ok());
        assertEquals("realizada", status());
        assertFalse(a.saveDraft(appointment, MARINA, complete, DOCTOR_USER).ok());
        assertTrue(a.recordProgressNote(appointment, MARINA, "adendo", "Resultado do teste rápido negativo.", DOCTOR_USER).ok());

        try (java.sql.Connection conn = database.DBConnection.openConnection()) {
            assertThrows(java.sql.SQLException.class, () -> Sql.execute(conn,
                    "UPDATE prontuarios SET conduta = 'alterada' WHERE id_consulta = ?", appointment), "trigger bloqueia alteracao");
        }
        assertFalse(new Triage().amend(((Number) new Triage().current(appointment).get("id_triagem")).intValue(), NURSE,
                signs(), "tarde demais", NURSE_USER).ok(), "triage cannot be amended after the encounter");
    }

    @Test
    @Order(7)
    void historyFilteredByRoleAndAudited() {
        assertEquals(PatientHistoryController.AccessLevel.DOCTOR, PatientHistoryController.level(List.of("medico"), MARINA, 0, PATIENT));
        assertEquals(PatientHistoryController.AccessLevel.NURSING, PatientHistoryController.level(List.of("enfermeiro"), 0, NURSE, PATIENT));
        assertEquals(PatientHistoryController.AccessLevel.ADMINISTRATIVE, PatientHistoryController.level(List.of("recepcionista"), 0, 0, PATIENT));
        assertEquals(PatientHistoryController.AccessLevel.DENIED, PatientHistoryController.level(List.of("admin"), 0, 0, PATIENT), "admin has no clinical access");
        assertEquals(PatientHistoryController.AccessLevel.DENIED, PatientHistoryController.level(List.of("enfermeiro"), 0, 999, PATIENT));

        assertEquals(1, new Encounter().patientMedicalRecords(PATIENT).size());
        long audited = Sql.number("SELECT COUNT(*) FROM auditoria WHERE id_paciente = ? AND acao IN "
                + "('triagem.registrada', 'triagem.retificada', 'atendimento.iniciado', 'prontuario.alterado', "
                + "'atendimento.finalizado', 'receita.emitida', 'receita.cancelada', 'exame.solicitado', 'alergia.registrada')", PATIENT);
        // 10 = 9 actions + a second "prontuario.alterado": finishing first saves the last draft version
        assertEquals(10, audited);
        long withClinicalContent = Sql.number("SELECT COUNT(*) FROM auditoria WHERE detalhes LIKE '%garganta%' OR detalhes LIKE '%Faringite%'");
        assertEquals(0, withClinicalContent, "the audit log keeps no clinical content");
    }

    private static String status() {
        return String.valueOf(Sql.one("SELECT status FROM consultas WHERE id_consulta = ?", appointment).get("status"));
    }
}
