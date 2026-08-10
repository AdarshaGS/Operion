package com.operion.examination;

import java.time.LocalDate;

import com.operion.academic.SchoolClass;
import com.operion.academic.Subject;
import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per (exam, schoolClass, subject) - an explicit row per class, same pattern as
 * FeeStructure, since max marks/pass marks can differ by class even for the same exam.
 */
@Getter
@Entity
@Table(name = "exam_schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExamSchedule extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "exam_id")
	private Exam exam;

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(name = "exam_date", nullable = false)
	private LocalDate examDate;

	@Column(name = "max_marks", nullable = false)
	private Double maxMarks;

	@Column(name = "pass_marks", nullable = false)
	private Double passMarks;

	public ExamSchedule(Exam exam, SchoolClass schoolClass, Subject subject, LocalDate examDate, Double maxMarks, Double passMarks) {
		this.exam = exam;
		this.schoolClass = schoolClass;
		this.subject = subject;
		this.examDate = examDate;
		this.maxMarks = maxMarks;
		this.passMarks = passMarks;
	}
}
