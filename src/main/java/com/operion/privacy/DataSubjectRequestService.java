package com.operion.privacy;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.audit.AuditLogService;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import org.springframework.stereotype.Service;

@Service
public class DataSubjectRequestService {

	/** Days from receipt when the caller gives no due date. */
	public static final int DEFAULT_RESPONSE_DAYS = 30;

	private final DataSubjectRequestRepository requestRepository;
	private final ConsentService consentService;
	private final AuditLogService auditLogService;

	public DataSubjectRequestService(DataSubjectRequestRepository requestRepository, ConsentService consentService,
			AuditLogService auditLogService) {
		this.requestRepository = requestRepository;
		this.consentService = consentService;
		this.auditLogService = auditLogService;
	}

	public DataSubjectRequest open(Person person, DataSubjectRequestType type, String description, LocalDate receivedDate,
			LocalDate dueDate) {
		LocalDate received = receivedDate != null ? receivedDate : LocalDate.now();
		LocalDate due = dueDate != null ? dueDate : received.plusDays(DEFAULT_RESPONSE_DAYS);
		if (due.isBefore(received)) {
			throw new IllegalArgumentException("Due date cannot be before the received date");
		}
		DataSubjectRequest saved = requestRepository.save(new DataSubjectRequest(person, type, description, received, due));
		auditLogService.record("DATA_SUBJECT_REQUEST", saved.getId(), "RECEIVED", null,
				Map.of("personId", person.getId(), "type", type));
		return saved;
	}

	public DataSubjectRequest verifyIdentity(DataSubjectRequest request, String method) {
		if (method == null || method.isBlank()) {
			throw new IllegalArgumentException("Say how the requester's identity was verified");
		}
		DataSubjectRequestStatus before = request.getStatus();
		request.verifyIdentity(method.trim(), TenantContext.getActorId());
		return save(request, "IDENTITY_VERIFIED", before);
	}

	public DataSubjectRequest complete(DataSubjectRequest request, String resolutionNote) {
		requireNote(resolutionNote);
		DataSubjectRequestStatus before = request.getStatus();
		request.complete(resolutionNote.trim(), TenantContext.getActorId());
		return save(request, "COMPLETED", before);
	}

	public DataSubjectRequest reject(DataSubjectRequest request, String resolutionNote) {
		requireNote(resolutionNote);
		DataSubjectRequestStatus before = request.getStatus();
		request.reject(resolutionNote.trim(), TenantContext.getActorId());
		return save(request, "REJECTED", before);
	}

	/** The person's own data as held by the organisation's identity and consent records, for an
	 * ACCESS or EXPORT request. Refused until identity is verified, so it can never be handed to
	 * an unverified requester. */
	public Map<String, Object> exportSubjectData(DataSubjectRequest request) {
		if (request.getRequestType() != DataSubjectRequestType.ACCESS && request.getRequestType() != DataSubjectRequestType.EXPORT) {
			throw new IllegalStateException("Only ACCESS or EXPORT requests can produce a data export");
		}
		if (request.getStatus() != DataSubjectRequestStatus.IDENTITY_VERIFIED
				&& request.getStatus() != DataSubjectRequestStatus.COMPLETED) {
			throw new IllegalStateException("Verify the requester's identity before exporting their data");
		}
		Person person = request.getPerson();
		Map<String, Object> profile = new LinkedHashMap<>();
		profile.put("firstName", person.getFirstName());
		profile.put("lastName", person.getLastName());
		profile.put("dateOfBirth", person.getDateOfBirth());
		profile.put("gender", person.getGender());
		profile.put("phone", person.getPhone());
		profile.put("email", person.getEmail());
		profile.put("address", person.getAddress());
		List<Map<String, Object>> consents = consentService.history(person.getId()).stream().map(r -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("purpose", r.getPurpose().getName());
			row.put("granted", r.isGranted());
			row.put("source", r.getSource());
			row.put("recordedAt", r.getRecordedAt());
			return row;
		}).toList();
		Map<String, Object> export = new LinkedHashMap<>();
		export.put("requestId", request.getId());
		export.put("person", profile);
		export.put("consents", consents);
		auditLogService.record("DATA_SUBJECT_REQUEST", request.getId(), "DATA_EXPORTED", null, null);
		return export;
	}

	private DataSubjectRequest save(DataSubjectRequest request, String action, DataSubjectRequestStatus before) {
		DataSubjectRequest saved = requestRepository.save(request);
		auditLogService.record("DATA_SUBJECT_REQUEST", saved.getId(), action, before, saved.getStatus());
		return saved;
	}

	private static void requireNote(String note) {
		if (note == null || note.isBlank()) {
			throw new IllegalArgumentException("A resolution note is required");
		}
	}
}
