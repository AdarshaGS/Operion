package com.operion.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.Section;
import com.operion.academic.SectionRepository;
import com.operion.academic.SchoolClass;
import com.operion.academic.SchoolClassRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.imports.ImportRowStatus;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.Student;
import com.operion.student.StudentDocumentRepository;
import com.operion.student.StudentEnrollmentRepository;
import com.operion.student.StudentExitRepository;
import com.operion.student.StudentIdGenerator;
import com.operion.student.StudentRepository;
import com.operion.student.StudentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, FeeService.class, StudentIdGenerator.class, OpeningBalanceRowImportService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OpeningBalanceRowImportServiceTest {

	@Autowired
	private OpeningBalanceRowImportService rowImportService;

	@Autowired
	private FeeService feeService;

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private AcademicYearRepository academicYearRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private GradeLevelRepository gradeLevelRepository;

	@Autowired
	private SchoolClassRepository schoolClassRepository;

	@Autowired
	private SectionRepository sectionRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private StudentEnrollmentRepository studentEnrollmentRepository;

	@Autowired
	private StudentDocumentRepository studentDocumentRepository;

	@Autowired
	private StudentExitRepository studentExitRepository;

	@Autowired
	private StudentIdGenerator studentIdGenerator;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private StudentFeeAssignmentRepository studentFeeAssignmentRepository;

	@Autowired
	private InvoiceRepository invoiceRepository;

	private StudentService studentService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private String setUpFixture(String orgSlug, String admissionNumber, boolean withFeeStructureGroup) {
		studentService = new StudentService(studentRepository, studentEnrollmentRepository, studentDocumentRepository, studentExitRepository,
				null, null, studentIdGenerator, new AuditLogService(auditLogRepository, new ObjectMapper()));

		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", orgSlug));
		TenantContext.set(organisation.getId(), null);

		AcademicYear academicYear =
				academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		GradeLevel grade5 = gradeLevelRepository.save(new GradeLevel("Grade 5", 5, null));
		SchoolClass schoolClass = schoolClassRepository.save(new SchoolClass(academicYear, campus, grade5, "Grade 5"));
		Section section = sectionRepository.save(new Section(schoolClass, "A", 40, null));
		Person person = personRepository.save(new Person("Meera", "Nair"));

		Student student = studentService.admit(
				person, admissionNumber, LocalDate.of(2025, 5, 1), null, null, null, null, null, null, "Indian", null, null, null, null);
		studentService.enroll(student, academicYear, section, 12, LocalDate.of(2025, 6, 1));

		if (withFeeStructureGroup) {
			feeService.createFeeStructureGroup("Grade 5 Annual Fees 2025-26", academicYear, schoolClass);
		}
		return admissionNumber;
	}

	private Map<String, String> row(String admissionNumber) {
		Map<String, String> row = new HashMap<>();
		row.put("studentAdmissionNumber", admissionNumber);
		row.put("amount", "5000.00");
		row.put("asOfDate", "2026-04-01");
		return row;
	}

	@Test
	void happyPathCreatesAnAssignmentAndInvoice() {
		String admissionNumber = setUpFixture("opening-balance-happy", "ADM-500", true);

		var result = rowImportService.importRow(2, row(admissionNumber), false);

		assertThat(result.status()).isEqualTo(ImportRowStatus.IMPORTED);
		assertThat(studentFeeAssignmentRepository.findAll()).hasSize(1);
		assertThat(invoiceRepository.findAll()).hasSize(1);
		assertThat(invoiceRepository.findAll().get(0).getTotalAmount()).isEqualByComparingTo("5000.00");
	}

	@Test
	void validateOnlyDoesNotPersistAnything() {
		String admissionNumber = setUpFixture("opening-balance-validate", "ADM-501", true);

		var result = rowImportService.importRow(2, row(admissionNumber), true);

		assertThat(result.status()).isEqualTo(ImportRowStatus.VALID);
		assertThat(studentFeeAssignmentRepository.findAll()).isEmpty();
	}

	@Test
	void reimportingTheSameStudentReportsADuplicateAndCreatesNothing() {
		String admissionNumber = setUpFixture("opening-balance-duplicate", "ADM-502", true);
		rowImportService.importRow(2, row(admissionNumber), false);

		var result = rowImportService.importRow(3, row(admissionNumber), false);

		assertThat(result.status()).isEqualTo(ImportRowStatus.DUPLICATE);
		assertThat(studentFeeAssignmentRepository.findAll()).hasSize(1);
	}

	@Test
	void missingFeeStructureGroupFailsWithAClearMessage() {
		String admissionNumber = setUpFixture("opening-balance-no-group", "ADM-503", false);

		assertThatThrownBy(() -> rowImportService.importRow(2, row(admissionNumber), false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("set up Fee Structures first");
	}
}
