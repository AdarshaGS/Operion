package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowResult;
import com.operion.common.imports.ImportRowStatus;
import com.operion.common.imports.ImportRunRepository;
import com.operion.common.imports.ImportRunService;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Department;
import com.operion.organisation.DepartmentRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the validate-then-confirm bulk staff import: a resolvable row creates a
 * Person+StaffProfile (validateOnly=false), a duplicate employeeCode is reported without
 * creating anything, and an unresolvable reference (unknown department) is reported as
 * an error instead of throwing out of the batch - same per-row isolation contract as
 * StudentImportServiceTest, but for HrService.createStaffProfile.
 *
 * HrService/StaffRowImportService/StaffImportService are constructed by hand rather than
 * @Import'd - see StaffLifecycleTest's javadoc: AuditLogService's ObjectMapper param
 * isn't autoconfigured under @DataJpaTest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffRowImportServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private DesignationRepository designationRepository;

	@Autowired
	private DepartmentRepository departmentRepository;

	@Autowired
	private StaffProfileRepository staffProfileRepository;

	@Autowired
	private LeaveTypeRepository leaveTypeRepository;

	@Autowired
	private LeaveBalanceRepository leaveBalanceRepository;

	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	@Autowired
	private StaffDocumentRepository staffDocumentRepository;

	@Autowired
	private JobApplicationRepository jobApplicationRepository;

	@Autowired
	private StaffAssignmentRepository staffAssignmentRepository;

	@Autowired
	private StaffExitRepository staffExitRepository;

	@Autowired
	private StaffBankDetailRepository staffBankDetailRepository;

	@Autowired
	private StaffAttendanceRepository staffAttendanceRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private ImportRunRepository importRunRepository;

	private StaffImportService staffImportService;
	private Campus campus;
	private Designation designation;
	private Department department;

	// Each test picks its own slug rather than @BeforeEach seeding one - NOT_SUPPORTED
	// above means nothing rolls back between tests, so a shared slug would collide on
	// the organisations.slug unique index the second test runs.
	private void setUp(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		designation = designationRepository.save(new Designation("Teacher"));
		department = departmentRepository.save(new Department("Science"));

		HrService hrService = new HrService(staffProfileRepository, leaveTypeRepository, leaveBalanceRepository, leaveRequestRepository,
				staffDocumentRepository, jobApplicationRepository, organisationRepository, staffAssignmentRepository,
				staffExitRepository, staffBankDetailRepository, staffAttendanceRepository,
				new AuditLogService(auditLogRepository, new ObjectMapper()));
		StaffRowImportService staffRowImportService = new StaffRowImportService(
				personRepository, staffProfileRepository, departmentRepository, designationRepository, campusRepository, hrService);
		staffImportService = new StaffImportService(staffRowImportService, new ImportRunService(importRunRepository));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private static MockMultipartFile csv(String... lines) {
		String content = String.join("\n", lines);
		return new MockMultipartFile("file", "staff.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
	}

	private static final String HEADER =
			"firstName,lastName,dateOfBirth,gender,email,phone,employeeCode,dateOfJoining,employmentType,department,designation,campus";

	@Test
	void createsAStaffProfileForAValidRow() {
		setUp("hr-staff-import-create");
		MockMultipartFile file = csv(HEADER,
				"Ravi,Menon,1985-01-01,MALE,ravi@example.com,9876500000,EMP-100,2020-06-01,PERMANENT,Science,Teacher,Main Campus");

		List<ImportRowResult> results = staffImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).row()).isEqualTo(2);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(results.get(0).id()).isNotNull();

		StaffProfile created = staffProfileRepository.findById(results.get(0).id()).orElseThrow();
		assertThat(created.getEmployeeCode()).isEqualTo("EMP-100");
		assertThat(created.getPerson().getFirstName()).isEqualTo("Ravi");
		assertThat(created.getDepartment().getId()).isEqualTo(department.getId());
		assertThat(created.getDesignation().getId()).isEqualTo(designation.getId());
		assertThat(created.getCampus().getId()).isEqualTo(campus.getId());
	}

	@Test
	void validateOnlyResolvesTheRowWithoutPersistingAnything() {
		setUp("hr-staff-import-validate-only");
		MockMultipartFile file = csv(HEADER,
				"Ravi,Menon,1985-01-01,MALE,ravi@example.com,9876500000,EMP-101,2020-06-01,PERMANENT,Science,Teacher,Main Campus");

		List<ImportRowResult> results = staffImportService.importFile(file, true);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.VALID);
		assertThat(results.get(0).id()).isNull();
		assertThat(staffProfileRepository.findAll()).isEmpty();
		assertThat(personRepository.findAll()).isEmpty();
	}

	@Test
	void duplicateEmployeeCodeIsReportedAndCreatesNothing() {
		setUp("hr-staff-import-duplicate");
		Person existingPerson = personRepository.save(new Person("Existing", "Staff"));
		StaffProfile existing = staffProfileRepository.save(
				new StaffProfile(existingPerson, campus, "EMP-102", designation, department, LocalDate.of(2019, 1, 1), EmploymentType.PERMANENT));

		MockMultipartFile file = csv(HEADER,
				"Ravi,Menon,1985-01-01,MALE,ravi@example.com,9876500000,EMP-102,2020-06-01,PERMANENT,Science,Teacher,Main Campus");

		List<ImportRowResult> results = staffImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(results.get(0).id()).isEqualTo(existing.getId());
		assertThat(results.get(0).message()).contains("EMP-102");
		assertThat(staffProfileRepository.findAll()).hasSize(1);
		assertThat(personRepository.findAll()).hasSize(1);
	}

	@Test
	void unknownDepartmentIsReportedAsAnErrorInsteadOfCrashingTheBatch() {
		setUp("hr-staff-import-unknown-department");
		MockMultipartFile file = csv(HEADER,
				"Ravi,Menon,1985-01-01,MALE,ravi@example.com,9876500000,EMP-103,2020-06-01,PERMANENT,Nonexistent,Teacher,Main Campus");

		List<ImportRowResult> results = staffImportService.importFile(file, false);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).status()).isEqualTo(ImportRowStatus.ERROR);
		assertThat(results.get(0).message()).contains("Nonexistent");
		assertThat(staffProfileRepository.findAll()).isEmpty();
		assertThat(personRepository.findAll()).isEmpty();
	}
}
