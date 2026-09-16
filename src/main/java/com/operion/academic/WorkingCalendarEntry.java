package com.operion.academic;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.Campus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One dated entry in an academic year's working calendar - either a HOLIDAY or a
 * SPECIAL_WORKING_DAY (see {@link WorkingCalendarEntryType}). Scoped to an academic year
 * (holidays are declared fresh each year) and optionally a single campus - null campus
 * means it applies organisation-wide. Complements the weekly working-days mask on the
 * core {@code OrganisationConfiguration} (see #143): that's the recurring pattern, this
 * is the dated exceptions to it.
 */
@Getter
@Entity
@Table(name = "working_calendar_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkingCalendarEntry extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	/** Null = applies to every campus; set = applies only to that one. */
	@ManyToOne(optional = true)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "entry_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private WorkingCalendarEntryType type;

	public WorkingCalendarEntry(AcademicYear academicYear, Campus campus, LocalDate date, String label,
			WorkingCalendarEntryType type) {
		this.academicYear = academicYear;
		this.campus = campus;
		this.date = date;
		this.label = label;
		this.type = type;
	}
}
