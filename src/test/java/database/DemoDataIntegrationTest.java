package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.TestDatabase;

/**
 * Banco novo com as migracoes de demonstracao (002 e 003): as telas tem o que mostrar em
 * qualquer dia, e os dados gerados respeitam as regras do dominio.
 */
class DemoDataIntegrationTest {

    private static String testDb;

    @BeforeAll
    static void create() {
        assumeTrue(TestDatabase.available(), "Banco indisponivel");
        testDb = TestDatabase.useTemporaryDatabase("hospital_demo");
    }

    @AfterAll
    static void delete() throws Exception {
        if (testDb != null) {
            TestDatabase.discardTemporaryDatabase(testDb);
        }
    }

    @Test
    void hasVolumeInEveryStatus() {
        assertTrue(Sql.number("SELECT COUNT(*) FROM pacientes") >= 40);
        assertTrue(Sql.number("SELECT COUNT(*) FROM especialidades WHERE ativo = 1") >= 11);
        assertTrue(Sql.number("SELECT COUNT(*) FROM medicos") >= 9);
        assertTrue(Sql.number("SELECT COUNT(*) FROM consultas WHERE status = 'realizada'") >= 50);
        assertTrue(Sql.number("SELECT COUNT(*) FROM consultas WHERE status = 'agendada' AND data_hora > NOW()") >= 50);
        assertTrue(Sql.number("SELECT COUNT(*) FROM consultas WHERE status IN ('ausente', 'cancelada')") > 0);
        assertTrue(Sql.number("SELECT COUNT(*) FROM receita_itens") > 0);
        assertTrue(Sql.number("SELECT COUNT(*) FROM exames") > 0);
    }

    @Test
    void clinicalDataMatchesAppointment() {
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM consultas c WHERE c.status = 'realizada' "
                + "AND NOT EXISTS (SELECT 1 FROM prontuarios p WHERE p.id_consulta = c.id_consulta AND p.status = 'finalizado')"),
                "toda consulta realizada tem prontuario finalizado");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM consultas c WHERE c.status IN ('realizada', 'aguardando_atendimento') "
                + "AND NOT EXISTS (SELECT 1 FROM triagens t WHERE t.id_consulta = c.id_consulta)"), "quem passou pela triagem tem triagem");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM prontuarios p JOIN consultas c ON c.id_consulta = p.id_consulta "
                + "WHERE p.id_medico <> c.id_medico"), "prontuario e do medico da consulta");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM consultas c WHERE NOT EXISTS (SELECT 1 FROM medico_especialidades me "
                + "WHERE me.id_profissional = c.id_medico AND me.id_especialidade = c.id_especialidade)"), "especialidade do medico");
    }

    @Test
    void appointmentsWithinDoctorAvailability() {
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM consultas c WHERE c.id_consulta >= 10000 AND NOT EXISTS ("
                + "SELECT 1 FROM disponibilidades_medico d WHERE d.id_profissional = c.id_medico AND d.ativo = 1 "
                + "AND d.dia_semana = WEEKDAY(c.data_hora) + 1 AND TIME(c.data_hora) >= d.hora_inicio "
                + "AND ADDTIME(TIME(c.data_hora), SEC_TO_TIME(c.duracao_min * 60)) <= d.hora_fim)"));
    }

    @Test
    void expandedCatalogIsConsistent() {
        assertTrue(Sql.number("SELECT COUNT(*) FROM tipos_exame WHERE ativo = 1") >= 80, "exames no catalogo");
        assertTrue(Sql.number("SELECT COUNT(DISTINCT categoria) FROM tipos_exame") >= 7, "categorias de exame");
        assertTrue(Sql.number("SELECT COUNT(*) FROM vacinas WHERE ativo = 1") >= 20);
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM vacinas WHERE faixa IS NULL"), "toda vacina tem faixa etaria");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM tipos_exame t JOIN locais_exame l ON l.id_local = t.id_local "
                + "WHERE ADDTIME(l.hora_inicio, SEC_TO_TIME(t.duracao_min * 60)) > l.hora_fim"), "todo exame cabe no horario do local");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM especialidades e WHERE e.ativo = 1 AND NOT EXISTS ("
                + "SELECT 1 FROM medico_especialidades me JOIN profissionais p ON p.id_profissional = me.id_profissional "
                + "WHERE me.id_especialidade = e.id_especialidade AND p.ativo = 1)"), "toda especialidade ativa tem medico");
        assertEquals(0, Sql.number("SELECT COUNT(*) FROM medicos m JOIN profissionais p ON p.id_profissional = m.id_profissional "
                + "WHERE p.ativo = 1 AND NOT EXISTS (SELECT 1 FROM disponibilidades_medico d WHERE d.id_profissional = m.id_profissional AND d.ativo = 1)"),
                "todo medico ativo tem horario de atendimento");
    }

    @Test
    void newLoginsJoinTheRightGroups() {
        Map<String, Object> carla = Sql.one("SELECT g.nome FROM usuarios u JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario "
                + "JOIN grupos g ON g.id_grupo = ug.id_grupo WHERE u.email = 'carla.cardio@hospital.local'");
        assertEquals("medico", carla.get("nome"));
        assertTrue(new models.User().authenticate("carla.cardio@hospital.local", "medico123") != null, "senha de demonstracao");
        assertEquals("enfermeiro", Sql.one("SELECT g.nome FROM usuarios_grupos ug JOIN grupos g ON g.id_grupo = ug.id_grupo "
                + "WHERE ug.id_usuario = 52").get("nome"));
    }
}
