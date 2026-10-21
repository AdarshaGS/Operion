package com.operion.performance;

import com.operion.common.InvalidRequestException;
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

/** A goal a teacher is working towards in one cycle; a reviewer records at the end whether it was met. */
@Getter
@Entity
@Table(name = "teacher_goals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeacherGoal extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "cycle_id")
	private AppraisalCycle cycle;

	@ManyToOne(optional = false)
	@JoinColumn(name = "teacher_person_id")
	private Person teacher;

	@Column(nullable = false)
	private String title;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GoalStatus status;

	public TeacherGoal(AppraisalCycle cycle, Person teacher, String title, String description) {
		this.cycle = cycle;
		this.teacher = teacher;
		edit(title, description);
		this.status = GoalStatus.OPEN;
	}

	public void edit(String title, String description) {
		if (title == null || title.isBlank()) {
			throw new InvalidRequestException("A goal needs a title");
		}
		this.title = title.trim();
		this.description = description == null || description.isBlank() ? null : description.trim();
	}

	public void markAs(GoalStatus status) {
		this.status = status;
	}
}
