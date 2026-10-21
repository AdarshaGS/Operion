package com.operion.timetable;

import java.time.DayOfWeek;

import com.operion.academic.Section;
import com.operion.academic.Subject;
import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.organisation.AcademicYear;
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
 * A weekly-recurring slot: this section has this subject, taught by this teacher, on
 * this weekday in this period. Represents the current timetable, not history - a change
 * replaces the row. academicYear is denormalized off section->schoolClass so the
 * teacher-clash rule ("no double-booking within a year") is a plain unique key.
 */
@Getter
@Entity
@Table(name = "timetable_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableEntry extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_person_id")
	private Person teacherPerson;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false, length = 10)
	private DayOfWeek dayOfWeek;

	@ManyToOne(optional = false)
	@JoinColumn(name = "period_id")
	private Period period;

	/** Optional - null when the school doesn't track rooms for this slot. */
	@ManyToOne
	@JoinColumn(name = "room_id")
	private Room room;

	public TimetableEntry(AcademicYear academicYear, Section section, Subject subject, Person teacherPerson,
			DayOfWeek dayOfWeek, Period period, Room room) {
		this(academicYear, section, subject, teacherPerson, dayOfWeek, period);
		this.room = room;
	}

	public TimetableEntry(AcademicYear academicYear, Section section, Subject subject, Person teacherPerson,
			DayOfWeek dayOfWeek, Period period) {
		this.academicYear = academicYear;
		this.section = section;
		this.subject = subject;
		this.teacherPerson = teacherPerson;
		this.dayOfWeek = dayOfWeek;
		this.period = period;
	}

	public void reassign(Subject subject, Person teacherPerson) {
		this.subject = subject;
		this.teacherPerson = teacherPerson;
	}

	public void reassign(Subject subject, Person teacherPerson, Room room) {
		reassign(subject, teacherPerson);
		this.room = room;
	}
}
