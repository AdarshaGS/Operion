package com.operion.certificate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.operion.attendance.AttendanceStatus;
import com.operion.document.DocumentBranding;
import com.operion.document.pdf.DateText;
import com.operion.identity.Person;
import com.operion.parent.StudentGuardian;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExit;
import com.operion.student.StudentExitRepository;

/**
 * The {{token}} values a certificate's wording can use, and the substitution itself. Substitution is plain
 * string replacement of known tokens - tenant-edited wording is never handed to a template engine as an
 * expression. A token with no value (or an unknown token) prints as a blank line to fill in by hand.
 */
@Component
public class CertificateMergeFields {

	public static final String BLANK = "________";

	private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

	/** Every built-in token with a one-line description - drives the help shown next to the wording editor. */
	public static final Map<String, String> CATALOG = catalog();

	private final StudentEnrollmentRepository enrollmentRepository;
	private final StudentExitRepository exitRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final CertificateRequestRepository requestRepository;

	public CertificateMergeFields(StudentEnrollmentRepository enrollmentRepository, StudentExitRepository exitRepository,
			StudentGuardianRepository studentGuardianRepository, CertificateRequestRepository requestRepository) {
		this.enrollmentRepository = enrollmentRepository;
		this.exitRepository = exitRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.requestRepository = requestRepository;
	}

	public Map<String, String> values(Student student, DocumentBranding school, String purpose, Map<String, String> manualValues,
			String certificateNumber, LocalDate issueDate) {
		Map<String, String> values = new LinkedHashMap<>();
		Person person = student.getPerson();
		values.put("school.name", school.schoolName());
		values.put("school.address", school.addressLine());
		values.put("student.name", fullName(person));
		values.put("student.firstName", person.getFirstName());
		values.put("student.lastName", person.getLastName());
		values.put("student.admissionNumber", student.getAdmissionNumber());
		values.put("student.studentId", student.getStudentId());
		values.put("student.dateOfBirth", DateText.format(person.getDateOfBirth()));
		values.put("student.dateOfBirthInWords", DateText.inWords(person.getDateOfBirth()));
		values.put("student.gender", person.getGender());
		values.put("student.nationality", student.getNationality());
		values.put("student.category", student.getCategory());
		values.put("student.admissionDate", DateText.format(student.getAdmissionDate()));
		primaryGuardian(student.getId()).ifPresent(guardian -> values.put("guardian.name", fullName(guardian.getGuardian().getPerson())));

		List<StudentEnrollment> enrollments = new ArrayList<>(enrollmentRepository.findByStudentId(student.getId()));
		enrollments.sort(Comparator.comparing(StudentEnrollment::getEnrolledDate, Comparator.nullsFirst(Comparator.naturalOrder()))
				.thenComparing(StudentEnrollment::getId));
		if (!enrollments.isEmpty()) {
			StudentEnrollment first = enrollments.get(0);
			StudentEnrollment latest = enrollments.stream().filter(StudentEnrollment::isCurrent).findFirst()
					.orElse(enrollments.get(enrollments.size() - 1));
			values.put("class.admitted", first.getSection().getSchoolClass().label());
			values.put("class.current", latest.getSection().getSchoolClass().label() + " - " + latest.getSection().getName());
			values.put("academicYear", latest.getAcademicYear().getName());
			putAttendance(values, latest.getId());
		}

		exitRepository.findByStudentId(student.getId()).stream()
				.max(Comparator.comparing(StudentExit::getExitDate).thenComparing(StudentExit::getId))
				.ifPresent(exit -> {
					values.put("exit.date", DateText.format(exit.getExitDate()));
					values.put("exit.type", humanize(exit.getExitType().name()));
					values.put("exit.reason", exit.getReason());
					values.put("exit.destinationSchool", exit.getDestinationSchool());
				});

		values.put("purpose", purpose);
		values.put("certificate.number", certificateNumber);
		values.put("issueDate", DateText.format(issueDate));
		if (manualValues != null) {
			manualValues.forEach((key, value) -> values.put("manual." + key, value));
		}
		return values;
	}

	public static String substitute(String text, Map<String, String> values) {
		if (text == null) {
			return null;
		}
		Matcher matcher = TOKEN.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String value = values.get(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(value == null || value.isBlank() ? BLANK : value));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/** Attendance in the latest enrollment: marked days, and days present (late counts as present, half day as half). */
	private void putAttendance(Map<String, String> values, Long enrollmentId) {
		List<Object[]> counts = requestRepository.attendanceCounts(enrollmentId);
		if (counts.isEmpty()) {
			return;
		}
		long marked = 0;
		BigDecimal present = BigDecimal.ZERO;
		for (Object[] row : counts) {
			AttendanceStatus status = (AttendanceStatus) row[0];
			long count = ((Number) row[1]).longValue();
			marked += count;
			if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) {
				present = present.add(BigDecimal.valueOf(count));
			} else if (status == AttendanceStatus.HALF_DAY) {
				present = present.add(BigDecimal.valueOf(count).divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY));
			}
		}
		values.put("attendance.workingDays", String.valueOf(marked));
		values.put("attendance.daysPresent", present.stripTrailingZeros().toPlainString());
	}

	private Optional<StudentGuardian> primaryGuardian(Long studentId) {
		return studentGuardianRepository.findByStudentId(studentId).stream()
				.filter(link -> link.getStatus() == StudentGuardianStatus.ACTIVE)
				.min(Comparator.comparing((StudentGuardian link) -> !link.isPrimaryGuardian()).thenComparingInt(StudentGuardian::getContactPriority));
	}

	static String fullName(Person person) {
		return person.getLastName() == null || person.getLastName().isBlank() ? person.getFirstName()
				: person.getFirstName() + " " + person.getLastName();
	}

	private static String humanize(String code) {
		String lower = code.toLowerCase().replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static Map<String, String> catalog() {
		Map<String, String> catalog = new LinkedHashMap<>();
		catalog.put("school.name", "School name (branding override, else organisation name)");
		catalog.put("school.address", "School address line");
		catalog.put("student.name", "Student's full name");
		catalog.put("student.firstName", "Student's first name");
		catalog.put("student.lastName", "Student's last name");
		catalog.put("student.admissionNumber", "Admission number");
		catalog.put("student.studentId", "Student ID");
		catalog.put("student.dateOfBirth", "Date of birth, e.g. 5 March 2012");
		catalog.put("student.dateOfBirthInWords", "Date of birth in words");
		catalog.put("student.gender", "Gender as recorded");
		catalog.put("student.nationality", "Nationality");
		catalog.put("student.category", "Category as recorded");
		catalog.put("student.admissionDate", "Date of admission");
		catalog.put("guardian.name", "Primary guardian's name");
		catalog.put("class.admitted", "Class the student was first enrolled in");
		catalog.put("class.current", "Current (or last) class and section");
		catalog.put("academicYear", "Academic year of the current (or last) enrollment");
		catalog.put("attendance.workingDays", "Days attendance was marked in that enrollment");
		catalog.put("attendance.daysPresent", "Days present in that enrollment (half day = 0.5)");
		catalog.put("exit.date", "Date of leaving (from the student's exit record)");
		catalog.put("exit.type", "Exit type, e.g. Transfer");
		catalog.put("exit.reason", "Reason for leaving");
		catalog.put("exit.destinationSchool", "School the student is moving to");
		catalog.put("purpose", "Purpose given on the request");
		catalog.put("certificate.number", "This certificate's number");
		catalog.put("issueDate", "Date of issue");
		catalog.put("manual.<key>", "A value typed in per request - define keys under Manual fields");
		return java.util.Collections.unmodifiableMap(catalog);
	}
}
