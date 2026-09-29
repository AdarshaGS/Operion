package com.operion.student;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.audit.AuditLogService;
import com.operion.identity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the applicant inquiry/review/reject/convert-to-student lifecycle and the received-documents checklist. */
@Service
public class ApplicantService {

	/** Default checklist - a constant, deliberately not per-org config. */
	public static final List<String> DOCUMENT_TYPES =
			List.of("BIRTH_CERTIFICATE", "PREVIOUS_MARKSHEET", "TRANSFER_CERTIFICATE", "ADDRESS_PROOF", "PHOTO");

	private final ApplicantRepository applicantRepository;
	private final StudentService studentService;
	private final ApplicantDocumentRepository applicantDocumentRepository;
	private final AuditLogService auditLogService;

	public ApplicantService(ApplicantRepository applicantRepository, StudentService studentService,
			ApplicantDocumentRepository applicantDocumentRepository, AuditLogService auditLogService) {
		this.applicantRepository = applicantRepository;
		this.studentService = studentService;
		this.applicantDocumentRepository = applicantDocumentRepository;
		this.auditLogService = auditLogService;
	}

	public Applicant inquire(Person person, LocalDate inquiryDate, String source, String notes) {
		return applicantRepository.save(new Applicant(person, inquiryDate, source, notes));
	}

	public Applicant startReview(Applicant applicant) {
		ApplicantStatus before = applicant.getStatus();
		applicant.startReview();
		Applicant saved = applicantRepository.save(applicant);
		auditLogService.record("Applicant", saved.getId(), "APPLICANT_REVIEW_STARTED", before, saved.getStatus());
		return saved;
	}

	public Applicant reject(Applicant applicant, String reason) {
		ApplicantStatus before = applicant.getStatus();
		applicant.reject(reason);
		Applicant saved = applicantRepository.save(applicant);
		auditLogService.record("Applicant", saved.getId(), "APPLICANT_REJECTED", before, saved.getStatus());
		return saved;
	}

	/** Admits the applicant's own Person as a Student, then marks the applicant CONVERTED - one atomic action. */
	@Transactional
	public Student convert(Applicant applicant, String admissionNumber, LocalDate admissionDate, String admissionSource,
			String previousSchool, String tcNumber, Double entranceScore, String bloodGroup, String category,
			String nationality, String remarks, String medicalAlerts, String emergencyContactName,
			String emergencyContactPhone) {
		ApplicantStatus before = applicant.getStatus();
		Student student = studentService.admit(applicant.getPerson(), admissionNumber, admissionDate, admissionSource,
				previousSchool, tcNumber, entranceScore, bloodGroup, category, nationality, remarks, medicalAlerts,
				emergencyContactName, emergencyContactPhone);
		applicant.markConverted();
		applicantRepository.save(applicant);
		auditLogService.record("Applicant", applicant.getId(), "APPLICANT_CONVERTED", before, applicant.getStatus());
		return student;
	}

	/** {ALL, INQUIRY, UNDER_REVIEW, CONVERTED, REJECTED} -> count. */
	public Map<String, Long> statusCounts() {
		Map<String, Long> counts = new LinkedHashMap<>();
		counts.put("ALL", applicantRepository.count());
		for (ApplicantStatus status : ApplicantStatus.values()) {
			counts.put(status.name(), applicantRepository.countByStatus(status));
		}
		return counts;
	}

	/** Every default checklist type, in fixed order, with received=false for rows never touched. */
	public Map<String, Boolean> documents(Long applicantId) {
		Map<String, Boolean> result = new LinkedHashMap<>();
		DOCUMENT_TYPES.forEach(type -> result.put(type, false));
		applicantDocumentRepository.findByApplicantId(applicantId)
				.forEach(document -> result.put(document.getDocType(), document.isReceived()));
		return result;
	}

	/** Upsert by (applicant, docType); unknown doc types are rejected. */
	@Transactional
	public Map<String, Boolean> updateDocuments(Applicant applicant, Map<String, Boolean> changes) {
		for (String docType : changes.keySet()) {
			if (!DOCUMENT_TYPES.contains(docType)) {
				throw new IllegalArgumentException("Unknown document type " + docType);
			}
		}
		Map<String, ApplicantDocument> existing = new LinkedHashMap<>();
		applicantDocumentRepository.findByApplicantId(applicant.getId())
				.forEach(document -> existing.put(document.getDocType(), document));
		changes.forEach((docType, received) -> {
			ApplicantDocument document = existing.get(docType);
			if (document == null) {
				applicantDocumentRepository.save(new ApplicantDocument(applicant, docType, received));
			} else {
				document.markReceived(received);
				applicantDocumentRepository.save(document);
			}
		});
		return documents(applicant.getId());
	}
}
