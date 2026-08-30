package com.operion.student.api;

import java.time.LocalDate;

import com.operion.identity.Person;
import com.operion.parent.StudentGuardian;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;

/** One row of the #245 search/filter list - enriched with the student's *current*
 * section/class and primary guardian contact, columns StudentResponse doesn't carry
 * since those aren't Student's own fields. */
public record StudentListRowResponse(Long id, Long personId, String firstName, String lastName, String studentId,
		String admissionNumber, LocalDate admissionDate, String status, Long sectionId, String sectionName,
		Long schoolClassId, String schoolClassDisplayName, String primaryGuardianName, String primaryGuardianPhone) {

	static StudentListRowResponse from(Student student, StudentEnrollment currentEnrollment, StudentGuardian primaryGuardian) {
		Person person = student.getPerson();
		var section = currentEnrollment != null ? currentEnrollment.getSection() : null;
		Person guardianPerson = primaryGuardian != null ? primaryGuardian.getGuardian().getPerson() : null;
		return new StudentListRowResponse(student.getId(), person.getId(), person.getFirstName(), person.getLastName(),
				student.getStudentId(), student.getAdmissionNumber(), student.getAdmissionDate(), student.getStatus().name(),
				section != null ? section.getId() : null, section != null ? section.getName() : null,
				section != null ? section.getSchoolClass().getId() : null,
				section != null ? section.getSchoolClass().getDisplayName() : null,
				guardianPerson != null ? guardianPerson.getFirstName() + " " + guardianPerson.getLastName() : null,
				guardianPerson != null ? guardianPerson.getPhone() : null);
	}
}
