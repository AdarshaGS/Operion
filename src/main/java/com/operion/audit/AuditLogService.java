package com.operion.audit;

import com.operion.common.TenantContext;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Records to the shared audit_logs ledger. before_value/after_value are JSON columns -
 * callers pass plain Java values (a String, an enum, a DTO, ...) and this serializes
 * them, so callers never have to hand-format JSON themselves (a bare enum name like
 * TRIAL isn't valid JSON text on its own - it needs to be the quoted string "TRIAL").
 */
@Service
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;
	private final ObjectMapper objectMapper;

	public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
		this.auditLogRepository = auditLogRepository;
		this.objectMapper = objectMapper;
	}

	public void record(String entityType, Long entityId, String action, Object beforeValue, Object afterValue) {
		auditLogRepository.save(new AuditLog(
				TenantContext.getOrganisationId(), TenantContext.getActorId(),
				entityType, entityId, action, toJson(beforeValue), toJson(afterValue)));
	}

	private String toJson(Object value) {
		return value == null ? null : objectMapper.writeValueAsString(value);
	}
}
