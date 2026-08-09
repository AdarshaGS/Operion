package com.operion.student;

import java.time.LocalDate;

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
 * Insert-only exit event log, deliberately with no one-per-student uniqueness - a
 * student can withdraw and later re-admit, per ai-context/erp-system-plan.md §2.2.
 */
@Getter
@Entity
@Table(name = "student_exits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentExit extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@Enumerated(EnumType.STRING)
	@Column(name = "exit_type", nullable = false, length = 20)
	private StudentExitType exitType;

	@Column(name = "exit_date", nullable = false)
	private LocalDate exitDate;

	/** Nullable. */
	private String reason;

	/** Nullable - only meaningful for TRANSFER. */
	@Column(name = "destination_school")
	private String destinationSchool;

	/** Nullable - references a User id, no FK by design. */
	@Column(name = "initiated_by")
	private Long initiatedBy;

	public StudentExit(Student student, StudentExitType exitType, LocalDate exitDate, String reason,
			String destinationSchool, Long initiatedBy) {
		this.student = student;
		this.exitType = exitType;
		this.exitDate = exitDate;
		this.reason = reason;
		this.destinationSchool = destinationSchool;
		this.initiatedBy = initiatedBy;
	}
}
