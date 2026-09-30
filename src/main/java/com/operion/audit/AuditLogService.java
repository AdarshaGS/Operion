package com.operion.audit;

import java.util.ArrayList;
import java.util.Set;

import com.operion.common.RequestOrigin;
import com.operion.common.TenantContext;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Records to the shared audit_logs ledger. before_value/after_value are JSON columns -
 * callers pass plain Java values (a String, an enum, a DTO, ...) and this serializes
 * them, so callers never have to hand-format JSON themselves (a bare enum name like
 * TRIAL isn't valid JSON text on its own - it needs to be the quoted string "TRIAL").
 *
 * Sensitive values (passwords, tokens, secrets, bank/tax identifiers) must never be stored
 * readable in an audit row. Rather than trust every caller, serialization redacts any
 * object property whose name matches {@link #isSensitive} at any depth, replacing the value
 * with {@link #REDACTED}. A new sensitive field with an unusual name needs adding there -
 * or, better, pass a purpose-built DTO that simply doesn't carry it.
 */
@Service
public class AuditLogService {

	public static final String REDACTED = "[REDACTED]";

	private static final Set<String> SENSITIVE_EXACT = Set.of("pan", "ifsc", "ifsccode", "taxid", "pin", "otp", "cvv");
	private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
			"password", "passwd", "secret", "token", "apikey", "accountnumber", "cardnumber", "credential");

	private final AuditLogRepository auditLogRepository;
	private final ObjectMapper objectMapper;

	public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
		this.auditLogRepository = auditLogRepository;
		this.objectMapper = objectMapper;
	}

	public void record(String entityType, Long entityId, String action, Object beforeValue, Object afterValue) {
		auditLogRepository.save(new AuditLog(
				TenantContext.getOrganisationId(), TenantContext.getActorId(),
				entityType, entityId, action, toJson(beforeValue), toJson(afterValue),
				RequestOrigin.getIpAddress(), RequestOrigin.getUserAgent()));
	}

	private String toJson(Object value) {
		if (value == null) {
			return null;
		}
		JsonNode tree = objectMapper.valueToTree(value);
		redact(tree);
		return objectMapper.writeValueAsString(tree);
	}

	private void redact(JsonNode node) {
		if (node.isObject()) {
			ObjectNode object = (ObjectNode) node;
			for (String name : new ArrayList<>(object.propertyNames())) {
				if (isSensitive(name)) {
					object.put(name, REDACTED);
				} else {
					redact(object.get(name));
				}
			}
		} else if (node.isArray()) {
			node.forEach(this::redact);
		}
	}

	static boolean isSensitive(String propertyName) {
		String normalised = propertyName.toLowerCase().replaceAll("[^a-z0-9]", "");
		return SENSITIVE_EXACT.contains(normalised) || SENSITIVE_FRAGMENTS.stream().anyMatch(normalised::contains);
	}
}
