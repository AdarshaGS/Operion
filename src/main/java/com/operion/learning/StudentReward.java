package com.operion.learning;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import com.operion.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Points a teacher awards a student for something worth recognising (#231). */
@Getter
@Entity
@Table(name = "student_rewards")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentReward extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private int points;

	private String reason;

	@ManyToOne(optional = false)
	@JoinColumn(name = "granted_by_person_id")
	private Person grantedBy;

	@Column(name = "granted_at", nullable = false)
	private Instant grantedAt;

	public StudentReward(Student student, String title, int points, String reason, Person grantedBy, Instant grantedAt) {
		this.student = student;
		this.title = title;
		this.points = points;
		this.reason = reason;
		this.grantedBy = grantedBy;
		this.grantedAt = grantedAt;
	}
}
