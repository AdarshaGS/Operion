package com.operion.parent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.student.Student;
import com.operion.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - same REQUIRES_NEW isolation as StudentRowImportService (see
 * its javadoc): a bad row rolls back its own Person/Guardian insert without poisoning
 * rows already committed earlier in the same batch.
 */
@Service
public class GuardianRowImportService {

	private final StudentRepository studentRepository;
	private final PersonRepository personRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final ParentService parentService;

	public GuardianRowImportService(StudentRepository studentRepository, PersonRepository personRepository,
			StudentGuardianRepository studentGuardianRepository, ParentService parentService) {
		this.studentRepository = studentRepository;
		this.personRepository = personRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.parentService = parentService;
	}

	/** Throws on any row-level failure rather than catching internally - GuardianImportService
	 * is the one that converts the exception into an ImportRowResult. Duplicate links are
	 * returned as a result (ImportRowStatus.DUPLICATE), never thrown, in either mode. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String admissionNumber = require(row, "studentAdmissionNumber");
		Student student = studentRepository.findByAdmissionNumber(admissionNumber)
				.orElseThrow(() -> new IllegalArgumentException("No student with admission number '" + admissionNumber + "'"));

		String firstName = require(row, "firstName");
		String lastName = blankToNull(row.get("lastName"));
		String email = blankToNull(row.get("email"));
		String phone = blankToNull(row.get("phone"));
		String occupation = blankToNull(row.get("occupation"));

		GuardianRelationshipType relationshipType = parseRelationshipType(require(row, "relationshipType"));
		boolean isPrimary = parseBoolean(row.get("isPrimary"));
		boolean isEmergencyContact = parseBoolean(row.get("isEmergencyContact"));
		boolean canPickup = parseBoolean(row.get("canPickup"));
		boolean canReceiveCommunication = parseBoolean(row.get("canReceiveCommunication"));
		int contactPriority = parseContactPriority(row.get("contactPriority"));

		Optional<StudentGuardian> existingLink = findExistingLink(student.getId(), email, phone);
		if (existingLink.isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE,
					"Guardian already linked to student '" + admissionNumber + "'", existingLink.get().getGuardian().getId());
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Valid", null);
		}

		Person person = new Person(firstName, lastName);
		person.setEmail(email);
		person.setPhone(phone);
		person = personRepository.save(person);

		Guardian guardian = parentService.getOrCreateGuardian(person, occupation);
		parentService.linkGuardian(student, guardian, relationshipType, isPrimary, isEmergencyContact, canPickup,
				canReceiveCommunication, contactPriority);

		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Imported", guardian.getId());
	}

	private Optional<StudentGuardian> findExistingLink(Long studentId, String email, String phone) {
		if (email != null) {
			return studentGuardianRepository.findByStudentIdAndGuardian_Person_Email(studentId, email);
		}
		if (phone != null) {
			return studentGuardianRepository.findByStudentIdAndGuardian_Person_Phone(studentId, phone);
		}
		return Optional.empty();
	}

	private static GuardianRelationshipType parseRelationshipType(String value) {
		try {
			return GuardianRelationshipType.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Invalid relationshipType '" + value + "' - expected one of " + Arrays.toString(GuardianRelationshipType.values()));
		}
	}

	private static boolean parseBoolean(String value) {
		String trimmed = blankToNull(value);
		return trimmed != null && (trimmed.equalsIgnoreCase("true") || trimmed.equals("1"));
	}

	private static int parseContactPriority(String value) {
		String trimmed = blankToNull(value);
		return trimmed == null ? 1 : Integer.parseInt(trimmed);
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
}
