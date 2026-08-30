package com.operion.student;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

/** Owns only the pre-admission inquiry pipeline - separate from StudentService, which
 * owns the real Student once an application is approved and admissions staff run the
 * normal admit flow (see StudentApplication's javadoc for why approval doesn't do that
 * automatically). */
@Service
public class StudentApplicationService {

	private final StudentApplicationRepository studentApplicationRepository;

	public StudentApplicationService(StudentApplicationRepository studentApplicationRepository) {
		this.studentApplicationRepository = studentApplicationRepository;
	}

	public StudentApplication submit(String applicantName, LocalDate dateOfBirth, String gender, String guardianName,
			String guardianPhone, Long desiredGradeLevelId, String notes) {
		return studentApplicationRepository.save(
				new StudentApplication(applicantName, dateOfBirth, gender, guardianName, guardianPhone, desiredGradeLevelId, notes));
	}

	public StudentApplication approve(StudentApplication application, Long decidedBy) {
		application.approve(decidedBy);
		return studentApplicationRepository.save(application);
	}

	public StudentApplication reject(StudentApplication application, Long decidedBy) {
		application.reject(decidedBy);
		return studentApplicationRepository.save(application);
	}
}
