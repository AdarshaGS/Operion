package com.operion.regulatory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.operion.hr.StaffProfile;
import com.operion.identity.Person;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;

/**
 * The closed catalog of Operion fields an export column can read from. Neutral names only -
 * how an external layout labels or orders them is the schema's job, not this catalog's. A
 * student's custom fields aren't listed here; they're addressed as {@code custom:<fieldKey>}.
 */
public final class RegulatoryExportSources {

	public record Source<T>(String key, String label, Function<T, String> extractor) {
	}

	public record StudentContext(Student student, StudentEnrollment enrollment) {
	}

	public record StaffContext(StaffProfile staff) {
	}

	public static final String CUSTOM_PREFIX = "custom:";

	private static final Map<String, Source<StudentContext>> STUDENT = index(List.of(
			new Source<>("studentId", "Student ID", c -> text(c.student().getStudentId())),
			new Source<>("admissionNumber", "Admission number", c -> text(c.student().getAdmissionNumber())),
			new Source<>("admissionDate", "Admission date", c -> text(c.student().getAdmissionDate())),
			new Source<>("firstName", "First name", c -> text(person(c).getFirstName())),
			new Source<>("lastName", "Last name", c -> text(person(c).getLastName())),
			new Source<>("fullName", "Full name", c -> fullName(person(c))),
			new Source<>("dateOfBirth", "Date of birth", c -> text(person(c).getDateOfBirth())),
			new Source<>("gender", "Gender", c -> text(person(c).getGender())),
			new Source<>("phone", "Phone", c -> text(person(c).getPhone())),
			new Source<>("email", "Email", c -> text(person(c).getEmail())),
			new Source<>("address", "Address", c -> text(person(c).getAddress())),
			new Source<>("category", "Category", c -> text(c.student().getCategory())),
			new Source<>("nationality", "Nationality", c -> text(c.student().getNationality())),
			new Source<>("bloodGroup", "Blood group", c -> text(c.student().getBloodGroup())),
			new Source<>("previousSchool", "Previous school", c -> text(c.student().getPreviousSchool())),
			new Source<>("tcNumber", "Transfer certificate number", c -> text(c.student().getTcNumber())),
			new Source<>("status", "Student status", c -> text(c.student().getStatus())),
			new Source<>("academicYear", "Academic year", c -> c.enrollment() == null ? "" : text(c.enrollment().getAcademicYear().getName())),
			new Source<>("className", "Class", c -> c.enrollment() == null ? "" : text(c.enrollment().getSection().getSchoolClass().getDisplayName())),
			new Source<>("sectionName", "Section", c -> c.enrollment() == null ? "" : text(c.enrollment().getSection().getName())),
			new Source<>("rollNumber", "Roll number", c -> c.enrollment() == null ? "" : text(c.enrollment().getRollNumber()))));

	private static final Map<String, Source<StaffContext>> STAFF = index(List.of(
			new Source<>("employeeCode", "Employee code", c -> text(c.staff().getEmployeeCode())),
			new Source<>("firstName", "First name", c -> text(c.staff().getPerson().getFirstName())),
			new Source<>("lastName", "Last name", c -> text(c.staff().getPerson().getLastName())),
			new Source<>("fullName", "Full name", c -> fullName(c.staff().getPerson())),
			new Source<>("dateOfBirth", "Date of birth", c -> text(c.staff().getPerson().getDateOfBirth())),
			new Source<>("gender", "Gender", c -> text(c.staff().getPerson().getGender())),
			new Source<>("phone", "Phone", c -> text(c.staff().getPerson().getPhone())),
			new Source<>("email", "Email", c -> text(c.staff().getPerson().getEmail())),
			new Source<>("address", "Address", c -> text(c.staff().getPerson().getAddress())),
			new Source<>("designation", "Designation", c -> c.staff().getDesignation() == null ? "" : text(c.staff().getDesignation().getName())),
			new Source<>("department", "Department", c -> c.staff().getDepartment() == null ? "" : text(c.staff().getDepartment().getName())),
			new Source<>("campus", "Campus", c -> text(c.staff().getCampus().getName())),
			new Source<>("dateOfJoining", "Date of joining", c -> text(c.staff().getDateOfJoining())),
			new Source<>("employmentType", "Employment type", c -> text(c.staff().getEmploymentType())),
			new Source<>("status", "Staff status", c -> text(c.staff().getStatus()))));

	private RegulatoryExportSources() {
	}

	@SuppressWarnings("unchecked")
	public static <T> Map<String, Source<T>> forType(RegulatoryEntityType type) {
		return (Map<String, Source<T>>) (Map<String, ?>) (type == RegulatoryEntityType.STUDENT ? STUDENT : STAFF);
	}

	public static List<Source<?>> catalog(RegulatoryEntityType type) {
		return List.copyOf((type == RegulatoryEntityType.STUDENT ? STUDENT : STAFF).values());
	}

	private static <T> Map<String, Source<T>> index(List<Source<T>> sources) {
		Map<String, Source<T>> map = new LinkedHashMap<>();
		sources.forEach(s -> map.put(s.key(), s));
		return Collections.unmodifiableMap(map);
	}

	private static Person person(StudentContext context) {
		return context.student().getPerson();
	}

	private static String fullName(Person person) {
		return (text(person.getFirstName()) + " " + text(person.getLastName())).trim();
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}
}
