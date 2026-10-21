package com.operion.timetable;

import java.time.DayOfWeek;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
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

/** A weekly time a teacher cannot be timetabled. A null period means the whole weekday. */
@Getter
@Entity
@Table(name = "teacher_unavailability")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherUnavailability extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_person_id")
	private Person teacherPerson;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 10)
	private DayOfWeek dayOfWeek;

	@ManyToOne
	@JoinColumn(name = "period_id")
	private Period period;

	private String reason;

	public TeacherUnavailability(Person teacherPerson, DayOfWeek dayOfWeek, Period period, String reason) {
		this.teacherPerson = teacherPerson;
		this.dayOfWeek = dayOfWeek;
		this.period = period;
		this.reason = reason == null || reason.isBlank() ? null : reason.trim();
	}

	/** True when this blocks the given weekday and period. */
	public boolean blocks(DayOfWeek day, Period other) {
		if (dayOfWeek != day) {
			return false;
		}
		return period == null || (period.getStartTime().isBefore(other.getEndTime()) && period.getEndTime().isAfter(other.getStartTime()));
	}
}
