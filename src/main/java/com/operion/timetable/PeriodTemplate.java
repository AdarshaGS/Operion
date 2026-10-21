package com.operion.timetable;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A named school day (its own list of periods) that some classes follow instead of the
 * organisation's default day. Periods with no template are the default day, which has no row here.
 */
@Getter
@Entity
@Table(name = "period_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodTemplate extends TenantScopedEntity {

	@Column(nullable = false)
	private String name;

	public PeriodTemplate(String name) {
		rename(name);
	}

	public void rename(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidRequestException("Template name is required");
		}
		this.name = name.trim();
	}
}
