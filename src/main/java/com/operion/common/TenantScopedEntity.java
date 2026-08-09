package com.operion.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.TenantId;

/**
 * Base for every entity owned by a single organisation. {@code organisationId} is
 * annotated with Hibernate's {@code @TenantId}: it is auto-populated from
 * {@link TenantContext} on insert and auto-applied as a WHERE filter on every query,
 * so tenant isolation can't be forgotten in an individual repository method.
 */
@Getter
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

	@TenantId
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private Long organisationId;
}
