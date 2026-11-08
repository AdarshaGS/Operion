package com.operion.student;

import java.util.Optional;

import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearClosureCheck;
import org.springframework.stereotype.Component;

/**
 * "Promote first, then close": a year cannot be closed while any student still holds a current
 * enrolment in it, because closing freezes the year and a frozen current enrolment could no
 * longer be promoted or exited.
 */
@Component
public class EnrolmentClosureCheck implements AcademicYearClosureCheck {

	private final StudentEnrollmentRepository studentEnrollmentRepository;

	public EnrolmentClosureCheck(StudentEnrollmentRepository studentEnrollmentRepository) {
		this.studentEnrollmentRepository = studentEnrollmentRepository;
	}

	@Override
	public Optional<String> blockingReason(AcademicYear academicYear) {
		long enrolled = studentEnrollmentRepository.countByAcademicYearIdAndCurrentTrue(academicYear.getId());
		if (enrolled == 0) {
			return Optional.empty();
		}
		return Optional.of(enrolled + (enrolled == 1 ? " student is" : " students are") + " still enrolled in "
				+ academicYear.getName() + "; promote or exit them before closing");
	}
}
