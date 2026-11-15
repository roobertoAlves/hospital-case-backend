package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import controllers.DashboardController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MenuEPainelTest {

    private static boolean temItem(Map<String, List<Menu.Item>> menu, String rotulo) {
        return menu.values().stream().flatMap(List::stream).anyMatch(i -> i.rotulo().equals(rotulo));
    }

    @Test
    void menuMostraSoOQueAsPermissoesPermitem() {
        Map<String, List<Menu.Item>> recepcao = Menu.visiveis(List.of("pacientes.view", "agenda.view"));
        assertTrue(temItem(recepcao, "Pacientes"));
        assertTrue(temItem(recepcao, "Início"), "Início vale para qualquer perfil");
        assertFalse(temItem(recepcao, "Fila de atendimento"));
        assertFalse(temItem(recepcao, "Usuários e permissões"));

        Map<String, List<Menu.Item>> medico = Menu.visiveis(List.of("atendimento.fila", "agenda.view.propria"));
        assertTrue(temItem(medico, "Fila de atendimento"));
        assertFalse(temItem(medico, "Pacientes"));
    }

    @Test
    void cadaPerfilVaiParaOProprioPainel() {
        assertEquals("painel", DashboardController.rotaDoPainel(List.of("admin", "medico")));
        assertEquals("painel", DashboardController.rotaDoPainel(List.of("medico")));
        assertEquals("painel", DashboardController.rotaDoPainel(List.of("recepcionista")));
        assertEquals("dashboard/suporte", DashboardController.rotaDoPainel(List.of("suporte")));
        assertEquals("dashboard/usuario", DashboardController.rotaDoPainel(List.of()));
    }

    @Test
    void painelHospitalarAceitaPerfisHospitalaresEBloqueiaExemplos() {
        Router router = new Router();
        Map<String, Object> rota = router.matchRoute("/painel").methods.get("get");
        Map<String, Object> suporte = Map.of("id_usuario", 23, "grupos", List.of("suporte"), "permissoes", List.of());
        Map<String, Object> medico = Map.of("id_usuario", 32, "grupos", List.of("medico"), "permissoes", List.of());
        Map<String, Object> admin = Map.of("id_usuario", 1, "grupos", List.of("admin"), "permissoes", List.of());
        assertEquals(403, router.checkAccessControl(rota, suporte).get("statusCode"));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(rota, medico).get("allowed")));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(rota, admin).get("allowed")));
    }

    @Test
    void rotasDePacienteExigemAPermissaoCerta() {
        Router router = new Router();
        Map<String, Object> criar = router.matchRoute("/api/pacientes").methods.get("post");
        Map<String, Object> enfermeiro = Map.of("id_usuario", 31, "grupos", List.of("enfermeiro"), "permissoes", List.of("pacientes.view"));
        Map<String, Object> recepcao = Map.of("id_usuario", 30, "grupos", List.of("recepcionista"), "permissoes", List.of("pacientes.create"));
        assertEquals(403, router.checkAccessControl(criar, enfermeiro).get("statusCode"), "enfermeiro nao cadastra paciente");
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(criar, recepcao).get("allowed")));
        assertEquals("PacienteController", router.matchRoute("/pacientes/novo").methods.get("get").get("controller"));
        assertEquals("ficha", router.matchRoute("/pacientes/12").methods.get("get").get("action"));
        assertEquals("12", router.matchRoute("/pacientes/12/editar").params.get("id"));
    }
}
