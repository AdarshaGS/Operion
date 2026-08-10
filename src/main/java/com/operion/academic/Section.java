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
 * Capacity is a soft limit enforced in AcademicService, not a DB constraint - schools
 * routinely override it. Homeroom teacher is deliberately not a FK here; see
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
}
