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
        assertTrue(temItem(recepcao, "Sair"));
        assertFalse(temItem(recepcao, "Fila de atendimento"));
        assertFalse(temItem(recepcao, "Usuários e permissões"));

        Map<String, List<Menu.Item>> medico = Menu.visiveis(List.of("atendimento.fila", "agenda.view.propria"));
        assertTrue(temItem(medico, "Fila de atendimento"));
        assertFalse(temItem(medico, "Pacientes"));
    }

    @Test
    void cadaPerfilVaiParaOProprioPainel() {
        assertEquals("dashboard/admin", DashboardController.rotaDoPainel(List.of("admin", "medico")));
        assertEquals("painel", DashboardController.rotaDoPainel(List.of("medico")));
        assertEquals("painel", DashboardController.rotaDoPainel(List.of("recepcionista")));
        assertEquals("dashboard/suporte", DashboardController.rotaDoPainel(List.of("suporte")));
        assertEquals("dashboard/usuario", DashboardController.rotaDoPainel(List.of()));
    }

    @Test
    void painelHospitalarBloqueiaAdminEPerfisDeExemplo() {
        Router router = new Router();
        Map<String, Object> rota = router.matchRoute("/painel").methods.get("get");
        Map<String, Object> admin = Map.of("id_usuario", 1, "grupos", List.of("admin"), "permissoes", List.of());
        Map<String, Object> medico = Map.of("id_usuario", 32, "grupos", List.of("medico"), "permissoes", List.of());
        assertEquals(403, router.checkAccessControl(rota, admin).get("statusCode"), "strictGroup: admin nao passa por cima");
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(rota, medico).get("allowed")));
    }
}
