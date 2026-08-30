package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves StaffDocument re-upload supersedes the prior ACTIVE document of the same
 * type (same pattern as StudentDocument), and verify records the decision.
 *
 * HrService is constructed by hand rather than @Import'd - see StaffAttendanceTest for why.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffDocumentTest {

	private HrService hrService;

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

	@BeforeEach
	void setUpHrService() {
		hrService = new HrService(staffProfileRepository, leaveTypeRepository, leaveBalanceRepository, leaveRequestRepository,
				staffDocumentRepository, jobApplicationRepository, organisationRepository, staffAssignmentRepository,
				staffExitRepository, staffBankDetailRepository, staffAttendanceRepository,
				new AuditLogService(auditLogRepository, new ObjectMapper()));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private StaffProfile setUpStaffProfile(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		Person person = personRepository.save(new Person("Ravi", "Menon"));
		Designation designation = designationRepository.save(new Designation("Teacher"));
		Department department = departmentRepository.save(new Department("Science"));
		return hrService.createStaffProfile(person, campus, "EMP-001", designation, department, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT);
	}

	@Test
	void reuploadSupersedesThePriorActiveDocumentOfTheSameType() {
		StaffProfile staffProfile = setUpStaffProfile("hr-document-reupload");

		StaffDocument first = hrService.addDocument(staffProfile, "ID_PROOF", "s3://ref-1", "aadhaar-v1.pdf", "application/pdf", null);
		StaffDocument second = hrService.addDocument(staffProfile, "ID_PROOF", "s3://ref-2", "aadhaar-v2.pdf", "application/pdf", null);

		// Each addDocument call commits its own transaction (NOT_SUPPORTED test setup), so
		// re-fetch `first` rather than trust the detached reference for the mutation done
		// during the second call.
		assertThat(staffDocumentRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(StaffDocumentStatus.SUPERSEDED);
		assertThat(second.getStatus()).isEqualTo(StaffDocumentStatus.ACTIVE);

		List<StaffDocument> active = staffDocumentRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), StaffDocumentStatus.ACTIVE);
		assertThat(active).hasSize(1);
		assertThat(active.get(0).getId()).isEqualTo(second.getId());
	}

	@Test
	void differentDocumentTypesDoNotSupersedeEachOther() {
		StaffProfile staffProfile = setUpStaffProfile("hr-document-different-types");

		hrService.addDocument(staffProfile, "ID_PROOF", "s3://ref-1", "aadhaar.pdf", "application/pdf", null);
		hrService.addDocument(staffProfile, "RESUME", "s3://ref-2", "resume.pdf", "application/pdf", null);

		List<StaffDocument> active = staffDocumentRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), StaffDocumentStatus.ACTIVE);
		assertThat(active).hasSize(2);
	}

	@Test
	void verifyingRecordsTheDecision() {
		StaffProfile staffProfile = setUpStaffProfile("hr-document-verify");
		StaffDocument document = hrService.addDocument(staffProfile, "ID_PROOF", "s3://ref-1", "aadhaar.pdf", "application/pdf", null);

		hrService.verifyDocument(document, DocumentVerificationStatus.VERIFIED, 7L);

		assertThat(document.getVerificationStatus()).isEqualTo(DocumentVerificationStatus.VERIFIED);
		assertThat(document.getVerifiedBy()).isEqualTo(7L);
		assertThat(document.getVerifiedAt()).isNotNull();
	}
}
