package com.operion.student.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Currently-enrolled students for a section - the Attendance module's daily register
 * needs this to know who to mark, and nothing exposed it (StudentEnrollmentController
 * only lists one student's own enrollment history, keyed by studentId, not by section).
 * Gated by STUDENT_VIEW since it's fundamentally student roster data, even though it's
 * also consumed cross-module by Attendance/Fees/Examinations - matches DefaultRoles'
 * Teacher/Accountant roles, which already carry STUDENT_VIEW. */
@RestController
@RequestMapping("/api/v1/sections/{sectionId}/enrollments")
@RequirePermission("STUDENT_VIEW")
public class SectionEnrollmentController {

	private final StudentEnrollmentRepository studentEnrollmentRepository;

	public SectionEnrollmentController(StudentEnrollmentRepository studentEnrollmentRepository) {
		this.studentEnrollmentRepository = studentEnrollmentRepository;
	}

	@GetMapping
	public List<StudentEnrollmentResponse> currentEnrollments(@PathVariable Long sectionId) {
		return studentEnrollmentRepository.findBySectionIdAndCurrentTrue(sectionId).stream()
				.map(StudentEnrollmentResponse::from)
				.toList();
	}
}
