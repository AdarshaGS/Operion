package com.operion.hr;

import java.time.LocalDate;

import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.Department;
import com.operion.organisation.Designation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approving an application and hiring the applicant is one unit: the decision, the person and
 * the staff profile are saved together or not at all (a duplicate employee code, for instance,
 * leaves the application PENDING). Kept apart from HrService so that service's constructor
 * stays as it is for the tests that build it by hand.
 */
@Service
public class JobApplicationHireService {

	private final HrService hrService;
	private final PersonRepository personRepository;

	public JobApplicationHireService(HrService hrService, PersonRepository personRepository) {
		this.hrService = hrService;
		this.personRepository = personRepository;
	}

	@Transactional
	public StaffProfile approveAndHire(JobApplication application, Long decidedBy, Campus campus, Designation designation,
			Department department, String employeeCode, LocalDate dateOfJoining, EmploymentType employmentType) {
		hrService.approveJobApplication(application, decidedBy);
		String name = application.getApplicantName().trim();
		int split = name.lastIndexOf(' ');
		Person person = new Person(split < 0 ? name : name.substring(0, split), split < 0 ? null : name.substring(split + 1));
		person.setEmail(application.getEmail());
		person = personRepository.save(person);
		return hrService.createStaffProfile(person, campus, employeeCode.trim(), designation, department, dateOfJoining, employmentType);
	}
}
