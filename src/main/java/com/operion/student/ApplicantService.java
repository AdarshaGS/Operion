package com.operion.student;

import java.time.LocalDate;

import com.operion.identity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the applicant inquiry/reject/convert-to-student lifecycle. */
@Service
public class ApplicantService {

	private final ApplicantRepository applicantRepository;
	private final StudentService studentService;

	public ApplicantService(ApplicantRepository applicantRepository, StudentService studentService) {
		this.applicantRepository = applicantRepository;
		this.studentService = studentService;
	}

	public Applicant inquire(Person person, LocalDate inquiryDate, String source, String notes) {
		return applicantRepository.save(new Applicant(person, inquiryDate, source, notes));
	}

	public Applicant reject(Applicant applicant) {
		applicant.reject();
		return applicantRepository.save(applicant);
	}

	/** Admits the applicant's own Person as a Student, then marks the applicant CONVERTED - one atomic action. */
	@Transactional
	public Student convert(Applicant applicant, String admissionNumber, LocalDate admissionDate, String admissionSource,
			String previousSchool, String tcNumber, Double entranceScore, String bloodGroup, String category,
			String nationality, String remarks, String medicalAlerts, String emergencyContactName,
			String emergencyContactPhone) {
		Student student = studentService.admit(applicant.getPerson(), admissionNumber, admissionDate, admissionSource,
				previousSchool, tcNumber, entranceScore, bloodGroup, category, nationality, remarks, medicalAlerts,
				emergencyContactName, emergencyContactPhone);
		applicant.markConverted();
		applicantRepository.save(applicant);
		return student;
	}
}
