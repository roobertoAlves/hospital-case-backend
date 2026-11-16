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

/**
 * ZTF_J2EE - PatientHistoryController
 *
 * Patient history filtered by role (RF-HIS-01, RN-ACE-02):
 * - doctor with a clinical link: finished records, triages, allergies, prescriptions and exams;
 * - nurse with a clinical link: triages and allergies;
 * - reception: only the administrative history (appointments and exams, no clinical content).
 * A professional without a link to the patient gets 403; every read is audited.
 * Served through the /api/patients/* servlet (route GET /api/patients/{id}/history).
 */
public class PatientHistoryController extends Controller {

    private static final long serialVersionUID = 1L;

    public enum AccessLevel { DOCTOR, NURSING, ADMINISTRATIVE, DENIED }

    /** GET /api/patients/{id}/history */
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

    /**
     * Isolated access rule (testable without HTTP): a clinical role only counts with a clinical
     * link; without it, the professional falls back to the administrative level only if they are
     * also in the reception group.
     */
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
