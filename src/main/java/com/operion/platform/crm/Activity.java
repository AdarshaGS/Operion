package com.operion.platform.crm;

import com.operion.common.persistence.PlatformPlane;
import java.time.Instant;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Insert-only relationship history (call/email/meeting/note) - deliberately no mutators,
 * it's an audit trail. */
@Getter
@Entity
@Table(name = "crm_activities")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@PlatformPlane
public class Activity extends BaseEntity {

	@Column(name = "lead_id", updatable = false)
	private Long leadId;

	@Column(name = "organisation_id", updatable = false)
	private Long organisationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private ActivityType type;

	@Column(nullable = false, columnDefinition = "TEXT", updatable = false)
	private String body;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "author_id", updatable = false)
	private Long authorId;

	public Activity(CrmOwner owner, ActivityType type, String body, Instant occurredAt, Long authorId) {
		this.leadId = owner.leadId();
		this.organisationId = owner.organisationId();
		this.type = type;
		this.body = body;
		this.occurredAt = occurredAt;
		this.authorId = authorId;
	}
}
