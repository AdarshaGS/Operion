package com.operion.student;

import java.time.LocalDate;

import com.operion.academic.Section;
import com.operion.organisation.AcademicYear;

/**
 * Enrols a student the way the admissions flow needs: placement plus, when the organisation has the
 * admission-fee workflow on, the admission fees assigned and invoiced in the same transaction. A port in
 * the student package, implemented by finance, so student code never depends on finance. Bulk and
 * year-end paths (CSV import, promotion) deliberately keep calling {@link StudentService} directly and
 * so never trigger admission fees.
 */
public interface AdmissionEnrollmentPort {

	StudentEnrollment enroll(Student student, AcademicYear academicYear, Section section, Integer rollNumber, LocalDate enrolledDate);
}
