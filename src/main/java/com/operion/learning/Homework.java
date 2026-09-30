package com.operion.learning;

import java.time.LocalDate;

import com.operion.academic.Section;
import com.operion.academic.Subject;
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

/** Day-to-day coursework for one section + subject - distinct from a formal Exam (#226). */
@Getter
@Entity
@Table(name = "homework")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Homework extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "section_id")
	private Section section;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(nullable = false)
	private String title;

	private String description;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	/** Opaque AssetStorageService reference, nullable. */
	@Column(name = "attachment_ref")
	private String attachmentRef;

	@ManyToOne(optional = false)
	@JoinColumn(name = "assigned_by_person_id")
	private Person assignedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HomeworkStatus status;

	public Homework(Section section, Subject subject, String title, String description, LocalDate dueDate,
			String attachmentRef, Person assignedBy) {
		this.section = section;
		this.subject = subject;
		this.title = title;
		this.description = description;
		this.dueDate = dueDate;
		this.attachmentRef = attachmentRef;
		this.assignedBy = assignedBy;
		this.status = HomeworkStatus.ACTIVE;
	}

	public void cancel() {
		this.status = HomeworkStatus.CANCELLED;
	}
}
