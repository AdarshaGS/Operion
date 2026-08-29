package com.operion.student;

import java.time.LocalDate;
import java.util.Map;

import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.api.StudentImportRowResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - deliberately a separate bean from StudentImportService so
 * REQUIRES_NEW actually takes effect (Spring's proxy-based @Transactional is a no-op on
 * self-invocation, i.e. a private method called from within the same class). This is
 * what lets a bad row (e.g. a duplicate admissionNumber) fail and roll back on its own
 * without poisoning the rows already committed earlier in the same CSV batch (#28).
 */
@Service
public class StudentRowImportService {

	private final PersonRepository personRepository;
	private final StudentService studentService;

	public StudentRowImportService(PersonRepository personRepository, StudentService studentService) {
		this.personRepository = personRepository;
		this.studentService = studentService;
	}

	/** Throws on any row-level failure rather than catching internally - that's what
	 * makes REQUIRES_NEW roll back this row's Person insert too when the later Student
	 * admit fails, instead of committing an orphan Person. StudentImportService is the
	 * one that converts the exception into a StudentImportRowResult. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public StudentImportRowResult importRow(int rowNumber, Map<String, String> row) {
		String firstName = require(row, "firstName");
		String lastName = blankToNull(row.get("lastName"));

		Person person = new Person(firstName, lastName);
		person.setDateOfBirth(parseDate(row.get("dateOfBirth")));
		person.setGender(blankToNull(row.get("gender")));
		person.setPhone(blankToNull(row.get("phone")));
		person.setEmail(blankToNull(row.get("email")));
		person = personRepository.save(person);

		String admissionNumber = blankToNull(row.get("admissionNumber"));
		LocalDate admissionDate = parseDate(row.get("admissionDate"));
		if (admissionDate == null) {
			throw new IllegalArgumentException("admissionDate is required");
		}

		Student student = studentService.admit(person, admissionNumber, admissionDate, blankToNull(row.get("admissionSource")),
				blankToNull(row.get("previousSchool")), blankToNull(row.get("tcNumber")), parseDouble(row.get("entranceScore")),
				blankToNull(row.get("bloodGroup")), blankToNull(row.get("category")), blankToNull(row.get("nationality")),
				blankToNull(row.get("remarks")));

		return new StudentImportRowResult(rowNumber, true, "Created", student.getId());
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
		String trimmed = blankToNull(value);
		return trimmed == null ? null : LocalDate.parse(trimmed);
	}

	private static Double parseDouble(String value) {
		String trimmed = blankToNull(value);
		return trimmed == null ? null : Double.parseDouble(trimmed);
	}
}
