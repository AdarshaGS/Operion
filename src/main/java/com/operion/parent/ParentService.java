package com.operion.parent;

import com.operion.identity.Person;
import com.operion.student.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns Guardian creation and the Student<->Guardian relationship, including the one
 * real business rule: at most one primary guardian per student, enforced the same
 * is-current-under-transaction way as StudentEnrollment/AcademicYear, per
 * ai-context/erp-system-plan.md §2.3.
 */
@Service
public class ParentService {

	private final GuardianRepository guardianRepository;
	private final StudentGuardianRepository studentGuardianRepository;

	public ParentService(GuardianRepository guardianRepository, StudentGuardianRepository studentGuardianRepository) {
		this.guardianRepository = guardianRepository;
		this.studentGuardianRepository = studentGuardianRepository;
	}

	/** Guardian is created lazily the first time a Person is linked to a student as a guardian. */
	@Transactional
	public Guardian getOrCreateGuardian(Person person, String occupation) {
		return guardianRepository.findByPersonId(person.getId())
				.orElseGet(() -> guardianRepository.save(new Guardian(person, occupation)));
	}

	@Transactional
	public StudentGuardian linkGuardian(Student student, Guardian guardian, GuardianRelationshipType relationshipType,
			boolean primaryGuardian, boolean emergencyContact, boolean canPickup, boolean canReceiveCommunication,
			int contactPriority) {
		if (primaryGuardian) {
			studentGuardianRepository.findByStudentIdAndPrimaryGuardianTrue(student.getId()).ifPresent(StudentGuardian::unsetPrimary);
		}
		return studentGuardianRepository.save(new StudentGuardian(student, guardian, relationshipType, primaryGuardian,
				emergencyContact, canPickup, canReceiveCommunication, contactPriority));
	}

	@Transactional
	public StudentGuardian updateRelationship(StudentGuardian studentGuardian, GuardianRelationshipType relationshipType,
			boolean primaryGuardian, boolean emergencyContact, boolean canPickup, boolean canReceiveCommunication,
			int contactPriority) {
		if (primaryGuardian) {
			studentGuardianRepository.findByStudentIdAndPrimaryGuardianTrue(studentGuardian.getStudent().getId())
					.filter(existing -> !existing.getId().equals(studentGuardian.getId()))
					.ifPresent(StudentGuardian::unsetPrimary);
		}
		studentGuardian.update(relationshipType, primaryGuardian, emergencyContact, canPickup, canReceiveCommunication, contactPriority);
		return studentGuardianRepository.save(studentGuardian);
	}
}
