package com.operion.academic;

import java.time.LocalDate;
import java.util.Map;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - same REQUIRES_NEW convention as StudentRowImportService.
 * Lives in com.operion.academic alongside this slice's other two importers, but only
 * orchestrates calls into com.operion.student's existing repositories/services - it
 * owns no entities of its own.
 */
@Service
public class StudentEnrolmentRowImportService {

	private final StudentRepository studentRepository;
	private final AcademicYearRepository academicYearRepository;
	private final SchoolClassRepository schoolClassRepository;
	private final SectionRepository sectionRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentService studentService;

	public StudentEnrolmentRowImportService(StudentRepository studentRepository, AcademicYearRepository academicYearRepository,
			SchoolClassRepository schoolClassRepository, SectionRepository sectionRepository,
			StudentEnrollmentRepository studentEnrollmentRepository, StudentService studentService) {
		this.studentRepository = studentRepository;
		this.academicYearRepository = academicYearRepository;
		this.schoolClassRepository = schoolClassRepository;
		this.sectionRepository = sectionRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentService = studentService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String admissionNumber = require(row, "studentAdmissionNumber");
		String academicYearLabel = require(row, "academicYear");
		String className = require(row, "className");
		String sectionName = require(row, "sectionName");
		LocalDate enrolledDate = parseDate(require(row, "enrolledDate"));
		Integer rollNumber = parseIntOrNull(row.get("rollNumber"));

		Student student = studentRepository.findByAdmissionNumber(admissionNumber)
				.orElseThrow(() -> new IllegalArgumentException("No student with admission number " + admissionNumber));
		AcademicYear academicYear = academicYearRepository.findByNameIgnoreCase(academicYearLabel)
				.orElseThrow(() -> new IllegalArgumentException("No academic year named " + academicYearLabel));
		SchoolClass schoolClass = schoolClassRepository
				.findByAcademicYearIdAndDisplayNameIgnoreCase(academicYear.getId(), className)
				.orElseThrow(() -> new IllegalArgumentException("No class named " + className + " in " + academicYearLabel));
		Section section = sectionRepository.findBySchoolClassIdAndNameIgnoreCase(schoolClass.getId(), sectionName)
				.orElseThrow(() -> new IllegalArgumentException("No section named " + sectionName + " under " + className));

		// Duplicate = student already has a current enrollment (is_current is
		// unique-per-student, same convention documented on StudentEnrollment/StudentService).
		if (studentEnrollmentRepository.findByStudentIdAndCurrentTrue(student.getId()).isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE,
					"Student " + admissionNumber + " already has a current enrollment", null);
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Row is valid", null);
		}

		StudentEnrollment enrollment = studentService.enroll(student, academicYear, section, rollNumber, enrolledDate);
		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Enrolled", enrollment.getId());
	}

	private static String require(Map<String, String> row, String field) {
		String value = blankToNull(row.get(field));
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static LocalDate parseDate(String value) {
		return LocalDate.parse(value.trim());
	}

	private static Integer parseIntOrNull(String value) {
		String trimmed = blankToNull(value);
		return trimmed == null ? null : Integer.parseInt(trimmed);
	}
}
