package com.operion.learning;

import com.operion.academic.SchoolClass;
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

/** A note, link or file scoped to a class (every section of it) and optionally one subject (#228). */
@Getter
@Entity
@Table(name = "learning_materials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningMaterial extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	/** Nullable - null means the material is for the class in general. */
	@ManyToOne
	@JoinColumn(name = "subject_id")
	private Subject subject;

	@Column(nullable = false)
	private String title;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MaterialKind kind;

	@Column(name = "attachment_ref")
	private String attachmentRef;

	private String url;

	@ManyToOne(optional = false)
	@JoinColumn(name = "uploaded_by_person_id")
	private Person uploadedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MaterialStatus status;

	public LearningMaterial(SchoolClass schoolClass, Subject subject, String title, String description, MaterialKind kind,
			String attachmentRef, String url, Person uploadedBy) {
		this.schoolClass = schoolClass;
		this.subject = subject;
		this.title = title;
		this.description = description;
		this.kind = kind;
		this.attachmentRef = attachmentRef;
		this.url = url;
		this.uploadedBy = uploadedBy;
		this.status = MaterialStatus.ACTIVE;
	}

	public void archive() {
		this.status = MaterialStatus.ARCHIVED;
	}
}
