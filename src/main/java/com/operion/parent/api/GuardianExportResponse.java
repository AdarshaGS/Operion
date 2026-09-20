package com.operion.parent.api;

import com.operion.identity.Person;
import com.operion.parent.Guardian;
import com.operion.parent.StudentGuardian;

/** One row per Student<->Guardian link (a guardian linked to multiple students exports
 * as multiple rows), matching StudentExportResponse's convention. */
public record GuardianExportResponse(Long guardianId, String firstName, String lastName, String email, String phone,
		String occupation, String studentAdmissionNumber, String relationshipType) {

	static GuardianExportResponse from(StudentGuardian link) {
		Guardian guardian = link.getGuardian();
		Person person = guardian.getPerson();
		return new GuardianExportResponse(guardian.getId(), person.getFirstName(), person.getLastName(), person.getEmail(),
				person.getPhone(), guardian.getOccupation(), link.getStudent().getAdmissionNumber(),
				link.getRelationshipType().name());
	}
}
