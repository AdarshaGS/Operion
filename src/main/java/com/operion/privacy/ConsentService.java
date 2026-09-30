package com.operion.privacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.operion.audit.AuditLogService;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import org.springframework.stereotype.Service;

@Service
public class ConsentService {

	public enum ConsentState {
		GRANTED,
		WITHDRAWN,
		NOT_RECORDED
	}

	/** One row per purpose for a person; {@code since}/{@code source} are null when NOT_RECORDED. */
	public record CurrentConsent(ConsentPurpose purpose, ConsentState state, Instant since, ConsentSource source) {
	}

	private final ConsentPurposeRepository purposeRepository;
	private final ConsentRecordRepository recordRepository;
	private final AuditLogService auditLogService;

	public ConsentService(ConsentPurposeRepository purposeRepository, ConsentRecordRepository recordRepository,
			AuditLogService auditLogService) {
		this.purposeRepository = purposeRepository;
		this.recordRepository = recordRepository;
		this.auditLogService = auditLogService;
	}

	public ConsentPurpose createPurpose(String code, String name, String description) {
		if (code == null || code.isBlank() || name == null || name.isBlank()) {
			throw new IllegalArgumentException("Code and name are required");
		}
		if (purposeRepository.findByCode(code.trim()).isPresent()) {
			throw new IllegalStateException("A consent purpose with code '" + code.trim() + "' already exists");
		}
		return purposeRepository.save(new ConsentPurpose(code.trim(), name.trim(), description));
	}

	public ConsentPurpose updatePurpose(ConsentPurpose purpose, String name, String description) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Name is required");
		}
		purpose.update(name.trim(), description);
		return purposeRepository.save(purpose);
	}

	public ConsentPurpose changePurposeStatus(ConsentPurpose purpose, ConsentPurposeStatus status) {
		purpose.changeStatus(status);
		return purposeRepository.save(purpose);
	}

	public ConsentRecord record(Person person, ConsentPurpose purpose, boolean granted, ConsentSource source, String note) {
		if (purpose.getStatus() != ConsentPurposeStatus.ACTIVE) {
			throw new IllegalStateException("Consent purpose '" + purpose.getCode() + "' is inactive");
		}
		ConsentRecord saved = recordRepository.save(new ConsentRecord(person, purpose, granted, source, note,
				TenantContext.getActorId()));
		auditLogService.record("CONSENT_RECORD", saved.getId(), granted ? "GRANTED" : "WITHDRAWN", null,
				Map.of("personId", person.getId(), "purpose", purpose.getCode()));
		return saved;
	}

	/** Newest first. */
	public List<ConsentRecord> history(Long personId) {
		return recordRepository.findByPersonIdOrderByRecordedAtDescIdDesc(personId);
	}

	/** Every ACTIVE purpose, plus any inactive one the person already has a record for. */
	public List<CurrentConsent> current(Long personId) {
		Map<Long, ConsentRecord> latest = new HashMap<>();
		for (ConsentRecord record : history(personId)) {
			latest.putIfAbsent(record.getPurpose().getId(), record);
		}
		List<CurrentConsent> result = new ArrayList<>();
		for (ConsentPurpose purpose : purposeRepository.findAll()) {
			ConsentRecord record = latest.get(purpose.getId());
			if (record == null && purpose.getStatus() != ConsentPurposeStatus.ACTIVE) {
				continue;
			}
			result.add(record == null
					? new CurrentConsent(purpose, ConsentState.NOT_RECORDED, null, null)
					: new CurrentConsent(purpose, record.isGranted() ? ConsentState.GRANTED : ConsentState.WITHDRAWN,
							record.getRecordedAt(), record.getSource()));
		}
		return result;
	}
}
