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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The many-to-many between Student and Guardian, carrying relationship attributes.
 * Booleans are deliberately orthogonal - no correlation constraint - a primary guardian
 * can lack pickup rights under a custody order, an emergency contact needn't be a legal
 * guardian, per ai-context/erp-system-plan.md §2.3. Mutable in place (unlike
 * StudentEnrollment/TeacherAssignment) - relies on Foundation's AuditLog for the trail;
 * isPrimaryGuardian uniqueness per student is enforced in ParentService, same
 * is-current-under-transaction convention as AcademicYear/StudentEnrollment.
 */
@Getter
@Entity
@Table(name = "student_guardians")
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
	}

	public void unsetPrimary() {
		this.primaryGuardian = false;
	}

	public void update(GuardianRelationshipType relationshipType, boolean primaryGuardian, boolean emergencyContact,
			boolean canPickup, boolean canReceiveCommunication, int contactPriority) {
		this.relationshipType = relationshipType;
		this.primaryGuardian = primaryGuardian;
		this.emergencyContact = emergencyContact;
		this.canPickup = canPickup;
		this.canReceiveCommunication = canReceiveCommunication;
		this.contactPriority = contactPriority;
	}

	public void deactivate() {
		this.status = StudentGuardianStatus.INACTIVE;
	}
}
