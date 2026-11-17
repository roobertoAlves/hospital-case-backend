package controllers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import models.ClinicalAccess;
import models.Encounter;
import models.ExamRequest;
import models.Patient;
import models.Prescription;
import models.Triage;
import utils.RequestHelper;

public class PatientHistoryController extends Controller {

    private static final long serialVersionUID = 1L;

    public enum AccessLevel { DOCTOR, NURSING, ADMINISTRATIVE, DENIED }

    public void history(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int patientId = PatientController.routeId(request);
        Map<String, Object> patient = patientId > 0 ? new Patient().byId(patientId) : null;
        if (patient == null) {
            notFound(response, "Paciente não encontrado.");
            return;
        }
        AccessLevel level = level(RequestHelper.getSessionUserGroups(request),
                TriageController.professionalId(request, "medico"),
                TriageController.professionalId(request, "enfermeiro"), patientId);
        if (level == AccessLevel.DENIED) {
            audit(request, "historico.negado", "pacientes", patientId, patientId, null);
            forbidden(response, "Seu perfil não tem acesso ao histórico deste paciente.");
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patient", patient);
        data.put("level", level.name());
        data.put("administrative", new Patient().administrativeHistory(patientId));
        if (level == AccessLevel.DOCTOR || level == AccessLevel.NURSING) {
            data.put("allergies", new Encounter().allergies(patientId));
            data.put("background", new models.PatientBackground().of(patientId));
            data.put("triages", new Triage().ofPatient(patientId));
        }
        if (level == AccessLevel.DOCTOR) {
            data.put("medical_records", new Encounter().patientMedicalRecords(patientId));
            data.put("prescriptions", new Prescription().ofPatient(patientId));
            data.put("exam_requests", new ExamRequest().ofPatient(patientId));
        }
        audit(request, "historico.leitura", "pacientes", patientId, patientId, level.name().toLowerCase());
        jsonSuccess(response, data, null);
    }

    static AccessLevel level(List<String> groups, int doctorId, int nurseId, int patientId) {
        if (groups.contains("medico") && doctorId > 0 && ClinicalAccess.doctorCanAccessPatient(doctorId, patientId)) {
            return AccessLevel.DOCTOR;
        }
        if (groups.contains("enfermeiro") && nurseId > 0 && ClinicalAccess.nurseCanAccessPatient(nurseId, patientId)) {
            return AccessLevel.NURSING;
        }
        if (groups.contains("recepcionista")) {
            return AccessLevel.ADMINISTRATIVE;
        }
        return AccessLevel.DENIED;
    }
}
