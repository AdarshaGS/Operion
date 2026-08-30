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
 * Capacity is an application-layer limit, not a DB constraint - enforced in
 * StudentService (enroll/promote/reassignSection all reject once a section's current
 * enrollment count would exceed it), not here or in AcademicService, since Section
 * itself has no view of enrollment. Homeroom teacher is deliberately not a FK here; see
 * TeacherAssignment.
 */
@Getter
@Entity
@Table(name = "sections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Section extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "school_class_id")
	private SchoolClass schoolClass;

	@Column(nullable = false)
	private String name;

	/** Nullable - no capacity limit set. */
	private Integer capacity;

	/** Nullable. */
	private String room;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SectionStatus status;

	public Section(SchoolClass schoolClass, String name, Integer capacity, String room) {
		this.schoolClass = schoolClass;
		this.name = name;
		this.capacity = capacity;
		this.room = room;
		this.status = SectionStatus.ACTIVE;
	}

	public void changeStatus(SectionStatus status) {
		this.status = status;
	}

	public void update(String name, Integer capacity, String room) {
		this.name = name;
		this.capacity = capacity;
		this.room = room;
	}
}
