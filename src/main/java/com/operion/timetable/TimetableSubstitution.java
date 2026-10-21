package com.operion.timetable;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** On {@code date}, {@code substitute} takes the slot instead of the teacher it belongs to. The weekly timetable is untouched. */
@Getter
@Entity
@Table(name = "timetable_substitutions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableSubstitution extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "timetable_entry_id")
	private TimetableEntry entry;

	@Column(name = "substitution_date", nullable = false)
	private LocalDate date;

	@ManyToOne(optional = false)
	@JoinColumn(name = "absent_teacher_person_id")
	private Person absentTeacher;

	@ManyToOne(optional = false)
	@JoinColumn(name = "substitute_teacher_person_id")
	private Person substituteTeacher;

	private String reason;

	public TimetableSubstitution(TimetableEntry entry, LocalDate date, Person absentTeacher, Person substituteTeacher, String reason) {
		this.entry = entry;
		this.date = date;
		this.absentTeacher = absentTeacher;
		this.substituteTeacher = substituteTeacher;
		this.reason = reason == null || reason.isBlank() ? null : reason.trim();
	}

	public void changeSubstitute(Person substituteTeacher, String reason) {
		this.substituteTeacher = substituteTeacher;
		this.reason = reason == null || reason.isBlank() ? null : reason.trim();
	}
}
