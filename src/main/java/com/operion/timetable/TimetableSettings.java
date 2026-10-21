package com.operion.timetable;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Org-wide timetable policy. Lazy, at most one row per organisation - no row until the first PUT,
 * defaults on GET (same convention as LibrarySettings). Every rule is opt-in: a gap of 0 and null
 * workload limits mean "no rule", so an existing organisation's timetable keeps working unchanged.
 */
@Getter
@Setter
@Entity
@Table(name = "timetable_settings")
public class TimetableSettings extends TenantScopedEntity {

	/** Minimum minutes between two consecutive teaching periods; breaks and special slots are exempt. */
	@Column(name = "gap_minutes", nullable = false)
	private int gapMinutes;

	/** Most teaching periods one teacher may be given on a single weekday; null = unlimited. */
	@Column(name = "max_teacher_periods_per_day")
	private Integer maxTeacherPeriodsPerDay;

	/** Most teaching periods one teacher may be given across the week; null = unlimited. */
	@Column(name = "max_teacher_periods_per_week")
	private Integer maxTeacherPeriodsPerWeek;

	public TimetableSettings() {
		this.gapMinutes = 0;
	}
}
