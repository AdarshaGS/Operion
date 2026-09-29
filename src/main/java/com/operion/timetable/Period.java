package com.operion.timetable;

import java.time.LocalTime;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One slot of the org's school day (e.g. "Period 1 09:00-09:45", or a lunch break).
 * Org-wide and ordinal-ordered - the day's shape is configured, never hardcoded. Break
 * slots exist so the grid can render them, but never hold a timetable entry.
 */
@Getter
@Entity
@Table(name = "periods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Period extends TenantScopedEntity {

	@Column(nullable = false)
	private int ordinal;

	@Column(nullable = false)
	private String label;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Column(name = "is_break", nullable = false)
	private boolean breakSlot;

	public Period(int ordinal, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		this.ordinal = ordinal;
		update(label, startTime, endTime, breakSlot);
	}

	public void update(String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		if (label == null || label.isBlank()) {
			throw new IllegalArgumentException("Period label is required");
		}
		if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
			throw new IllegalStateException("Period end time must be after its start time");
		}
		this.label = label.trim();
		this.startTime = startTime;
		this.endTime = endTime;
		this.breakSlot = breakSlot;
	}
}
