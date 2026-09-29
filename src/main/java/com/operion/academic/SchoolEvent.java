package com.operion.academic;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.Campus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An ad-hoc, admin-added calendar event (sports day, PTM, etc.) - deliberately separate
 * from WorkingCalendarEntry (#143), which feeds working-day computation and is scoped to
 * an academic year; an event is neither a holiday nor a working-day exception, just
 * something to show on the calendar, so it carries no academic-year FK. Optionally scoped
 * to a single campus (null = organisation-wide), same convention as
 * WorkingCalendarEntry.campus. Single-date only for v1 (#240) - a date-range field can be
 * added later without breaking anything if a multi-day event ever comes up.
 */
@Getter
@Entity
@Table(name = "school_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolEvent extends TenantScopedEntity {

	/** Null = applies to every campus; set = applies only to that one. */
	@ManyToOne(optional = true)
	@JoinColumn(name = "campus_id")
	private Campus campus;

	@Column(name = "event_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	public SchoolEvent(Campus campus, LocalDate date, String title, String description) {
		this.campus = campus;
		this.date = date;
		this.title = title;
		this.description = description;
	}
}
