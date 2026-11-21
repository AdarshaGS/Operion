package com.operion.parent;

import com.operion.common.TenantScopedEntity;
import com.operion.student.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The many-to-many between Student and Guardian, carrying relationship attributes.
 * Booleans are deliberately orthogonal - no correlation constraint - a primary guardian
 * can lack pickup rights under a custody order, an emergency contact needn't be a legal
 * guardian, per ai-context/erp-system-plan.md §2.3. Mutable in place (unlike
 * StudentEnrollment/TeacherAssignment) - relies on Foundation's AuditLog for the trail;
 * isPrimaryGuardian uniqueness per student is arranged in ParentService (unset the old
 * primary first) and backed by the uq_student_guardians_student_primary key over primary_marker.
 */
@Getter
@Entity
@Table(name = "student_guardians", uniqueConstraints = {
		@UniqueConstraint(name = "uq_student_guardians_student_primary", columnNames = { "student_id", "primary_marker" }) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentGuardian extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "student_id")
	private Student student;

	@ManyToOne(optional = false)
	@JoinColumn(name = "guardian_id")
	private Guardian guardian;

	@Enumerated(EnumType.STRING)
	@Column(name = "relationship_type", nullable = false, length = 30)
	private GuardianRelationshipType relationshipType;

	@Column(name = "is_primary_guardian", nullable = false)
	private boolean primaryGuardian;

	/** TRUE while this link is the student's ACTIVE primary guardian, else NULL, so a unique key can allow at most one. Kept in step by every mutator below. */
	@Column(name = "primary_marker")
	private Boolean primaryMarker;

	@Column(name = "is_emergency_contact", nullable = false)
	private boolean emergencyContact;

	@Column(name = "can_pickup", nullable = false)
	private boolean canPickup;

	@Column(name = "can_receive_communication", nullable = false)
	private boolean canReceiveCommunication;

	@Column(name = "contact_priority", nullable = false)
	private int contactPriority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StudentGuardianStatus status;

	public StudentGuardian(Student student, Guardian guardian, GuardianRelationshipType relationshipType,
			boolean primaryGuardian, boolean emergencyContact, boolean canPickup, boolean canReceiveCommunication,
			int contactPriority) {
		this.student = student;
		this.guardian = guardian;
		this.relationshipType = relationshipType;
		this.primaryGuardian = primaryGuardian;
		this.emergencyContact = emergencyContact;
		this.canPickup = canPickup;
		this.canReceiveCommunication = canReceiveCommunication;
		this.contactPriority = contactPriority;
		this.status = StudentGuardianStatus.ACTIVE;
		syncPrimaryMarker();
	}

	private void syncPrimaryMarker() {
		this.primaryMarker = primaryGuardian && status == StudentGuardianStatus.ACTIVE ? Boolean.TRUE : null;
	}

	public void unsetPrimary() {
		this.primaryGuardian = false;
		syncPrimaryMarker();
	}

	public void update(GuardianRelationshipType relationshipType, boolean primaryGuardian, boolean emergencyContact,
			boolean canPickup, boolean canReceiveCommunication, int contactPriority) {
		this.relationshipType = relationshipType;
		this.primaryGuardian = primaryGuardian;
		this.emergencyContact = emergencyContact;
		this.canPickup = canPickup;
		this.canReceiveCommunication = canReceiveCommunication;
		this.contactPriority = contactPriority;
		syncPrimaryMarker();
	}

	/** An inactive link can no longer be the student's primary guardian, so the flag is cleared with the status. */
	public void deactivate() {
		this.status = StudentGuardianStatus.INACTIVE;
		this.primaryGuardian = false;
		syncPrimaryMarker();
	}
}
