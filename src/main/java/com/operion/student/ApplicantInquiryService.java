package com.operion.student;

import java.time.LocalDate;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an INQUIRY applicant from the public form. The caller (the public controller)
 * must have set TenantContext BEFORE invoking this - the transaction opens here, and the
 * tenant identifier resolves once per session (see ai-context/load-context.md).
 */
@Service
public class ApplicantInquiryService {

	static final String PUBLIC_FORM_SOURCE = "PUBLIC_FORM";

	private final PersonRepository personRepository;
	private final ApplicantRepository applicantRepository;
	private final GradeLevelRepository gradeLevelRepository;

	public ApplicantInquiryService(PersonRepository personRepository, ApplicantRepository applicantRepository,
			GradeLevelRepository gradeLevelRepository) {
		this.personRepository = personRepository;
		this.applicantRepository = applicantRepository;
		this.gradeLevelRepository = gradeLevelRepository;
	}

	@Transactional
	public Applicant submit(String firstName, String lastName, LocalDate dateOfBirth, String gender, Long desiredGradeId,
			String guardianName, String guardianPhone, String guardianEmail, String notes) {
		GradeLevel grade = null;
		if (desiredGradeId != null) {
			grade = gradeLevelRepository.findById(desiredGradeId)
					.filter(g -> g.getStatus() == GradeLevelStatus.ACTIVE)
					.orElseThrow(() -> new IllegalArgumentException("No grade with id " + desiredGradeId));
		}
		Person person = new Person(firstName.trim(), lastName.trim());
		person.setDateOfBirth(dateOfBirth);
		person.setGender(gender == null || gender.isBlank() ? null : gender.trim());
		person = personRepository.save(person);

		Applicant applicant = new Applicant(person, LocalDate.now(), PUBLIC_FORM_SOURCE, blankToNull(notes));
		applicant.recordInquiryDetails(grade, guardianName.trim(), guardianPhone.trim(), blankToNull(guardianEmail));
		return applicantRepository.save(applicant);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
