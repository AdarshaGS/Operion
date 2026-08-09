package com.operion.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Single reusable, append-only ledger for "who changed what, when, for which
 * organisation" - shared by every module, not just Foundation. entity_type/entity_id
 * is a deliberate polymorphic reference with no FK, so audit survives independent of
 * how each module's schema evolves. Not organisation-scoped via @TenantId: audit rows
 * are looked up by an explicitly passed organisation id, not the implicit request
 * tenant, and organisation_id must stay nullable for pre-org/platform events.
 */
@Getter
@Entity
@Table(name = "audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "organisation_id")
	private Long organisationId;

	@Column(name = "actor_user_id")
	private Long actorUserId;

	@Column(name = "entity_type", nullable = false)
	private String entityType;

	@Column(name = "entity_id", nullable = false)
	private Long entityId;

	@Column(nullable = false)
	private String action;

	@Column(name = "before_value", columnDefinition = "json")
	private String beforeValue;

	@Column(name = "after_value", columnDefinition = "json")
	private String afterValue;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "ip_address")
	private String ipAddress;

	public AuditLog(Long organisationId, Long actorUserId, String entityType, Long entityId,
			String action, String beforeValue, String afterValue) {
		this.organisationId = organisationId;
		this.actorUserId = actorUserId;
		this.entityType = entityType;
		this.entityId = entityId;
		this.action = action;
		this.beforeValue = beforeValue;
		this.afterValue = afterValue;
		this.occurredAt = Instant.now();
	}
}
