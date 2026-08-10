package com.operion.examination;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Org-scoped catalog - one concrete percentage-band model, not a generic template engine. */
@Getter
@Entity
@Table(name = "grading_scales")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingScale extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "is_default", nullable = false)
	private boolean defaultScale;

	public GradingScale(String name, boolean defaultScale) {
		this.name = name;
		this.defaultScale = defaultScale;
	}
}
