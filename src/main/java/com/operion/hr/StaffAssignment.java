package com.operion.hr;

import java.time.LocalDate;

import com.operion.common.TenantScopedEntity;
import com.operion.organisation.Campus;
import com.operion.organisation.Department;
import com.operion.organisation.Designation;
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
 * Insert-only history of a staff member's campus/department/designation, same
 * lifecycle as TeacherAssignment: a transfer or designation change ends this row and
 * inserts a new one, never an in-place update. StaffProfile keeps its own
 * campus/department/designation columns as the current snapshot for its existing
 * readers - this table is purely the "who was assigned where, and when" trail.
 */
@Getter
@Entity
@Table(name = "staff_assignments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffAssignment extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "staff_profile_id")
	private StaffProfile staffProfile;

	/** Nullable - org-wide staff without a single campus. */
	@ManyToOne
	@JoinColumn(name = "campus_id")
	private Campus campus;

	/** Nullable - org-wide staff without a department assignment. */
	@ManyToOne
	@JoinColumn(name = "department_id")
	private Department department;

	@ManyToOne(optional = false)
	@JoinColumn(name = "designation_id")
	private Designation designation;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** Nullable - null means ongoing. */
	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StaffAssignmentStatus status;

	public StaffAssignment(StaffProfile staffProfile, Campus campus, Department department, Designation designation, LocalDate startDate) {
		this.staffProfile = staffProfile;
		this.campus = campus;
		this.department = department;
		this.designation = designation;
		this.startDate = startDate;
		this.status = StaffAssignmentStatus.ACTIVE;
	}

	public void end(LocalDate endDate) {
		if (status == StaffAssignmentStatus.ENDED) {
			throw new IllegalStateException("Staff assignment is already ended");
		}
		this.endDate = endDate;
		this.status = StaffAssignmentStatus.ENDED;
	}
}
