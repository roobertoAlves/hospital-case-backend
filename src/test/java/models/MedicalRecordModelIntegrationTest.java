package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import database.Sql;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

class MedicalRecordModelIntegrationTest {

    private static String testDb;

    @BeforeAll
    static void create() {
        assumeTrue(TestDatabase.available(), "Database unavailable");
        testDb = TestDatabase.useTemporaryDatabase("hospital_cfm");
    }

    @AfterAll
    static void delete() throws Exception {
        if (testDb != null) {
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    @Test
    void demoRecordsFollowTheCfmModel() {
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM pacientes WHERE nome_mae IS NULL OR naturalidade IS NULL"), "identification");
        assertEquals(Sql.number("SELECT COUNT(*) FROM pacientes"), Sql.number("SELECT COUNT(*) FROM paciente_antecedentes"),
                "every demo patient has a health background");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM prontuarios WHERE status = 'finalizado' "
                + "AND (exame_fisico IS NULL OR revisao_sistemas IS NULL OR diagnostico IS NULL)"), "finished records are complete");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM pacientes WHERE data_nascimento > DATE_SUB(CURDATE(), INTERVAL 18 YEAR) "
                + "AND responsavel_nome IS NULL"), "minors have a legal guardian");
        assertEquals(1, Sql.number("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = DATABASE() "
                + "AND trigger_name = 'trg_prontuarios_finalizado'"), "immutability trigger recreated");
    }

    @Test
    void backgroundIsValidatedSavedAndAudited() {
        PatientBackground b = new PatientBackground();
        Map<String, Object> bad = new HashMap<>();
        bad.put("tabagismo", "as vezes");
        assertTrue(b.save(2, bad, 32).containsKey("tabagismo"), "choices are fixed");

        Map<String, Object> ok = new HashMap<>();
        ok.put("doencas_preexistentes", "Hipertensão arterial");
        ok.put("medicamentos_uso_continuo", "Losartana 50 mg");
        ok.put("tabagismo", "ex_fumante");
        ok.put("etilismo", "social");
        ok.put("atividade_fisica", "3x_ou_mais");
        ok.put("historia_familiar", "Pai com infarto aos 58 anos");
        long auditBefore = Sql.number("SELECT COUNT(*) FROM auditoria WHERE acao = 'antecedentes.alterados'");
        assertTrue(b.save(2, ok, 32).isEmpty());
        Map<String, Object> saved = b.of(2);
        assertEquals("ex_fumante", saved.get("tabagismo"));
        assertEquals("Losartana 50 mg", saved.get("medicamentos_uso_continuo"));
        assertEquals(auditBefore + 1, Sql.number("SELECT COUNT(*) FROM auditoria WHERE acao = 'antecedentes.alterados'"));

        ok.put("tabagismo", "");
        assertTrue(b.save(2, ok, 32).isEmpty());
        assertEquals(null, b.of(2).get("tabagismo"), "an empty choice clears the field");
    }

    @Test
    void patientIntakeAcceptsIdentificationAndGuardian() {
        Map<String, Object> d = new HashMap<>();
        d.put("nome", "Clara Menezes Prado");
        d.put("cpf", "734.918.265-19");
        d.put("data_nascimento", "2015-04-02");
        d.put("nome_mae", "Helena Menezes Prado");
        d.put("naturalidade", "Santos/SP");
        d.put("responsavel_nome", "Helena Menezes Prado");
        d.put("responsavel_parentesco", "Mãe");
        d.put("responsavel_telefone", "13988887777");
        Patient.Result r = new Patient().create(d, 30);
        assertTrue(r.ok(), "errors: " + r.errors());
        Map<String, Object> p = new Patient().byId((int) r.id());
        assertEquals("Helena Menezes Prado", p.get("nome_mae"));
        assertEquals("(13) 98888-7777", p.get("responsavel_telefone"));
    }
}
