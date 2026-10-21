package com.operion.academic;

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
 * Subject-to-class mapping at the school_class level (all sections of a grade share
 * the subject list). Per-section electives are a future StudentSubjectSelection
 * concern, not built now.
 */
@Getter
@Entity
@Table(name = "class_subjects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassSubject extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ClassSubjectStatus status;

	/** Periods per week this subject gets in the class; null means no quota (nothing caps or requires its timetable slots). */
	@Column(name = "weekly_periods")
	private Integer weeklyPeriods;

	public ClassSubject(SchoolClass schoolClass, Subject subject, boolean mandatory) {
		this.schoolClass = schoolClass;
		this.subject = subject;
		this.mandatory = mandatory;
		this.status = ClassSubjectStatus.ACTIVE;
	}

	public void changeStatus(ClassSubjectStatus status) {
		this.status = status;
	}

	/** Zero or null clears the quota. */
	public void updateWeeklyPeriods(Integer weeklyPeriods) {
		if (weeklyPeriods != null && weeklyPeriods < 0) {
			throw new com.operion.common.InvalidRequestException("Weekly periods cannot be negative");
		}
		this.weeklyPeriods = weeklyPeriods == null || weeklyPeriods == 0 ? null : weeklyPeriods;
	}

	public void updateMandatory(boolean mandatory) {
		this.mandatory = mandatory;
	}
}
