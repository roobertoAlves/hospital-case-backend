package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import models.User;
import org.junit.jupiter.api.Test;

class DashboardTest {

    @Test
    void roleLabelFollowsTheMainGroup() {
        assertEquals("Médico", User.roleLabel(List.of("admin", "medico")));
        assertEquals("Recepção", User.roleLabel(List.of("recepcionista")));
        assertEquals("Paciente", User.roleLabel(List.of("paciente")));
        assertEquals("Usuário", User.roleLabel(List.of()));
    }

    @Test
    void hospitalDashboardAcceptsHospitalRolesAndBlocksExamples() {
        Router router = new Router();
        Map<String, Object> route = router.matchRoute("/api/dashboard").methods.get("get");
        Map<String, Object> support = Map.of("id_usuario", 23, "grupos", List.of("suporte"), "permissoes", List.of());
        Map<String, Object> doctor = Map.of("id_usuario", 32, "grupos", List.of("medico"), "permissoes", List.of());
        Map<String, Object> admin = Map.of("id_usuario", 1, "grupos", List.of("admin"), "permissoes", List.of());
        assertEquals(403, router.checkAccessControl(route, support).get("statusCode"));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(route, doctor).get("allowed")));
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(route, admin).get("allowed")));
    }

    @Test
    void patientRoutesRequireTheRightPermission() {
        Router router = new Router();
        Map<String, Object> create = router.matchRoute("/api/patients").methods.get("post");
        Map<String, Object> nurse = Map.of("id_usuario", 31, "grupos", List.of("enfermeiro"), "permissoes", List.of("pacientes.view"));
        Map<String, Object> reception = Map.of("id_usuario", 30, "grupos", List.of("recepcionista"), "permissoes", List.of("pacientes.create"));
        assertEquals(403, router.checkAccessControl(create, nurse).get("statusCode"), "a nurse does not register patients");
        assertTrue(Boolean.TRUE.equals(router.checkAccessControl(create, reception).get("allowed")));
        assertEquals("PatientController", router.matchRoute("/api/patients/12").methods.get("get").get("controller"));
        assertEquals("show", router.matchRoute("/api/patients/12").methods.get("get").get("action"));
        assertEquals("12", router.matchRoute("/api/patients/12/history").params.get("id"));
    }
}
