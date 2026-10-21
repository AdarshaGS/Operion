package com.operion.timetable;

import java.time.LocalTime;

import com.operion.common.InvalidRequestException;
import com.operion.common.TenantScopedEntity;
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

	/** True for every non-teaching slot (BREAK and SPECIAL) - kept in step with slotType. */
	@Column(name = "is_break", nullable = false)
	private boolean breakSlot;

	@Enumerated(EnumType.STRING)
	@Column(name = "slot_type", nullable = false, length = 20)
	private SlotType slotType;

	/** The day this period belongs to; null is the organisation's default day. Fixed at creation. */
	@ManyToOne
	@JoinColumn(name = "template_id")
	private PeriodTemplate template;

	public Period(int ordinal, String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		this(ordinal, label, startTime, endTime, breakSlot ? SlotType.BREAK : SlotType.TEACHING);
	}

	public Period(PeriodTemplate template, int ordinal, String label, LocalTime startTime, LocalTime endTime, SlotType slotType) {
		this(ordinal, label, startTime, endTime, slotType);
		this.template = template;
	}

	public Period(int ordinal, String label, LocalTime startTime, LocalTime endTime, SlotType slotType) {
		this.ordinal = ordinal;
		update(label, startTime, endTime, slotType);
	}

	public void update(String label, LocalTime startTime, LocalTime endTime, boolean breakSlot) {
		update(label, startTime, endTime, breakSlot ? SlotType.BREAK : SlotType.TEACHING);
	}

	public boolean isTeaching() {
		return slotType.isTeaching();
	}

	public void update(String label, LocalTime startTime, LocalTime endTime, SlotType slotType) {
		if (label == null || label.isBlank()) {
			throw new InvalidRequestException("Period label is required");
		}
		if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
			throw new InvalidRequestException("Period end time must be after its start time");
		}
		this.label = label.trim();
		this.startTime = startTime;
		this.endTime = endTime;
		this.slotType = slotType == null ? SlotType.TEACHING : slotType;
		this.breakSlot = !this.slotType.isTeaching();
	}
}
