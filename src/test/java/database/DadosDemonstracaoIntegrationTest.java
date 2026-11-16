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
class DadosDemonstracaoIntegrationTest {

    private static String banco;

    @BeforeAll
    static void criar() {
        assumeTrue(TestDatabase.disponivel(), "Banco indisponivel");
        banco = TestDatabase.usarBancoTemporario("hospital_demo");
    }

    @AfterAll
    static void apagar() throws Exception {
        if (banco != null) {
            TestDatabase.descartarBancoTemporario(banco);
        }
    }

    @Test
    void temVolumeEmCadaSituacao() {
        assertTrue(Sql.numero("SELECT COUNT(*) FROM pacientes") >= 40);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM especialidades WHERE ativo = 1") >= 11);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM medicos") >= 9);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM consultas WHERE status = 'realizada'") >= 50);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM consultas WHERE status = 'agendada' AND data_hora > NOW()") >= 50);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM consultas WHERE status IN ('ausente', 'cancelada')") > 0);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM receita_itens") > 0);
        assertTrue(Sql.numero("SELECT COUNT(*) FROM exames") > 0);
    }

    @Test
    void dadosClinicosCoerentesComAConsulta() {
        assertEquals(0, Sql.numero("SELECT COUNT(*) FROM consultas c WHERE c.status = 'realizada' "
                + "AND NOT EXISTS (SELECT 1 FROM prontuarios p WHERE p.id_consulta = c.id_consulta AND p.status = 'finalizado')"),
                "toda consulta realizada tem prontuario finalizado");
        assertEquals(0, Sql.numero("SELECT COUNT(*) FROM consultas c WHERE c.status IN ('realizada', 'aguardando_atendimento') "
                + "AND NOT EXISTS (SELECT 1 FROM triagens t WHERE t.id_consulta = c.id_consulta)"), "quem passou pela triagem tem triagem");
        assertEquals(0, Sql.numero("SELECT COUNT(*) FROM prontuarios p JOIN consultas c ON c.id_consulta = p.id_consulta "
                + "WHERE p.id_medico <> c.id_medico"), "prontuario e do medico da consulta");
        assertEquals(0, Sql.numero("SELECT COUNT(*) FROM consultas c WHERE NOT EXISTS (SELECT 1 FROM medico_especialidades me "
                + "WHERE me.id_profissional = c.id_medico AND me.id_especialidade = c.id_especialidade)"), "especialidade do medico");
    }

    @Test
    void consultasDentroDaDisponibilidadeDoMedico() {
        assertEquals(0, Sql.numero("SELECT COUNT(*) FROM consultas c WHERE c.id_consulta >= 10000 AND NOT EXISTS ("
                + "SELECT 1 FROM disponibilidades_medico d WHERE d.id_profissional = c.id_medico AND d.ativo = 1 "
                + "AND d.dia_semana = WEEKDAY(c.data_hora) + 1 AND TIME(c.data_hora) >= d.hora_inicio "
                + "AND ADDTIME(TIME(c.data_hora), SEC_TO_TIME(c.duracao_min * 60)) <= d.hora_fim)"));
    }

    @Test
    void novosLoginsEntramNosGruposCertos() {
        Map<String, Object> carla = Sql.um("SELECT g.nome FROM usuarios u JOIN usuarios_grupos ug ON ug.id_usuario = u.id_usuario "
                + "JOIN grupos g ON g.id_grupo = ug.id_grupo WHERE u.email = 'carla.cardio@hospital.local'");
        assertEquals("medico", carla.get("nome"));
        assertTrue(new models.Usuario().autenticar("carla.cardio@hospital.local", "medico123") != null, "senha de demonstracao");
        assertEquals("enfermeiro", Sql.um("SELECT g.nome FROM usuarios_grupos ug JOIN grupos g ON g.id_grupo = ug.id_grupo "
                + "WHERE ug.id_usuario = 52").get("nome"));
    }
}
