package com.operion.hr;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One row, one transaction - same shape as StudentRowImportService, and for the same
 * reason: REQUIRES_NEW only takes effect on a separate bean (self-invocation makes
 * Spring's proxy-based @Transactional a no-op), so a bad row's Person insert rolls back
 * with it instead of leaving an orphan.
 *
 * validateOnly drives the same "resolve everything, then decide" code path for both the
 * dry-run and the real import - a resolvable row only ever differs by whether the final
 * Person/StaffProfile insert actually happens.
 */
@Service
public class StaffRowImportService {

	private final PersonRepository personRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final DepartmentRepository departmentRepository;
	private final DesignationRepository designationRepository;
	private final CampusRepository campusRepository;
	private final HrService hrService;

	public StaffRowImportService(PersonRepository personRepository, StaffProfileRepository staffProfileRepository,
			DepartmentRepository departmentRepository, DesignationRepository designationRepository,
			CampusRepository campusRepository, HrService hrService) {
		this.personRepository = personRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.departmentRepository = departmentRepository;
		this.designationRepository = designationRepository;
		this.campusRepository = campusRepository;
		this.hrService = hrService;
	}

	/** Throws on any row-level failure rather than catching internally - StaffImportService
	 * is the one that converts the exception into an ImportRowResult(ERROR). The explicit
	 * duplicate check is the one exception to that: a duplicate employeeCode is reported,
	 * not thrown, since it isn't a malformed row. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ImportRowResult importRow(int rowNumber, Map<String, String> row, boolean validateOnly) {
		String firstName = require(row, "firstName");
		String lastName = blankToNull(row.get("lastName"));
		LocalDate dateOfBirth = parseDate(row.get("dateOfBirth"));
		String gender = blankToNull(row.get("gender"));
		String email = blankToNull(row.get("email"));
		String phone = blankToNull(row.get("phone"));

		String employeeCode = require(row, "employeeCode");

		LocalDate dateOfJoining = parseDate(row.get("dateOfJoining"));
		if (dateOfJoining == null) {
			throw new IllegalArgumentException("dateOfJoining is required");
		}

		EmploymentType employmentType = parseEmploymentType(require(row, "employmentType"));

		String departmentName = require(row, "department");
		Department department = departmentRepository.findByNameIgnoreCase(departmentName)
				.orElseThrow(() -> new IllegalArgumentException("Unknown department '" + departmentName + "'"));

		String designationName = require(row, "designation");
		Designation designation = designationRepository.findByNameIgnoreCase(designationName)
				.orElseThrow(() -> new IllegalArgumentException("Unknown designation '" + designationName + "'"));

		String campusName = require(row, "campus");
		Campus campus = campusRepository.findByNameIgnoreCase(campusName)
				.orElseThrow(() -> new IllegalArgumentException("Unknown campus '" + campusName + "'"));

		Optional<StaffProfile> existing = staffProfileRepository.findByEmployeeCode(employeeCode);
		if (existing.isPresent()) {
			return new ImportRowResult(rowNumber, ImportRowStatus.DUPLICATE,
					"Staff with employee code '" + employeeCode + "' already exists", existing.get().getId());
		}

		if (validateOnly) {
			return new ImportRowResult(rowNumber, ImportRowStatus.VALID, "Row is valid", null);
		}

		Person person = new Person(firstName, lastName);
		person.setDateOfBirth(dateOfBirth);
		person.setGender(gender);
		person.setPhone(phone);
		person.setEmail(email);
		person = personRepository.save(person);

		StaffProfile staffProfile = hrService.createStaffProfile(
				person, campus, employeeCode, designation, department, dateOfJoining, employmentType);

		return new ImportRowResult(rowNumber, ImportRowStatus.IMPORTED, "Created", staffProfile.getId());
	}

	private static EmploymentType parseEmploymentType(String value) {
		try {
			return EmploymentType.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown employmentType '" + value + "'");
		}
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
}
