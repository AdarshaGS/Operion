package com.operion.examination;

import com.operion.common.TenantScopedEntity;
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

/** An examination event within an academic year ("Term 1 Unit Test", "Half-Yearly"). */
@Getter
@Entity
@Table(name = "exams")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exam extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "academic_year_id")
	private AcademicYear academicYear;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "exam_type", nullable = false, length = 20)
	private ExamType examType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExamStatus status;

	public Exam(AcademicYear academicYear, String name, ExamType examType) {
		this.academicYear = academicYear;
		this.name = name;
		this.examType = examType;
		this.status = ExamStatus.ACTIVE;
	}
}
