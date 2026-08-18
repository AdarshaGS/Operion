package com.operion.audit.api;

import java.time.Instant;

import com.operion.audit.AuditLog;

public record AuditLogResponse(Long id, Long actorUserId, String entityType, Long entityId, String action, Instant occurredAt) {

	public static AuditLogResponse from(AuditLog log) {
		return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getEntityType(), log.getEntityId(), log.getAction(),
				log.getOccurredAt());
	}
}
