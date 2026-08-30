package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLog;
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
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the workforce-lifecycle actions added for GitHub milestone #24: transfer
 * closes the old StaffAssignment and opens a new one (mirroring TeacherAssignment's
 * insert-only reassignment) while updating StaffProfile's current-state columns,
 * recording an exit flips the master status, bank details upsert in place, and each of
 * these plus a plain status change writes to the shared AuditLog (GitHub #183).
 *
 * HrService is constructed by hand rather than @Import'd - see StaffAttendanceTest for why.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffLifecycleTest {

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
	void creatingAStaffProfileOpensTheFirstAssignmentHistoryRow() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-create");

		List<StaffAssignment> history = staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(staffProfile.getId());
		assertThat(history).hasSize(1);
		assertThat(history.get(0).getStatus()).isEqualTo(StaffAssignmentStatus.ACTIVE);
		assertThat(history.get(0).getDesignation().getId()).isEqualTo(staffProfile.getDesignation().getId());
	}

	@Test
	void transferClosesThePriorAssignmentAndOpensANewOneAndUpdatesTheCurrentSnapshot() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-transfer");
		Campus newCampus = campusRepository.save(new Campus("North Campus", "NORTH"));
		Designation newDesignation = designationRepository.save(new Designation("Head Teacher"));

		hrService.transfer(staffProfile, newCampus, null, newDesignation, LocalDate.of(2026, 2, 1));

		assertThat(staffProfile.getCampus().getId()).isEqualTo(newCampus.getId());
		assertThat(staffProfile.getDepartment()).isNull();
		assertThat(staffProfile.getDesignation().getId()).isEqualTo(newDesignation.getId());

		List<StaffAssignment> history = staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(staffProfile.getId());
		assertThat(history).hasSize(2);
		assertThat(history.get(0).getStatus()).isEqualTo(StaffAssignmentStatus.ACTIVE);
		assertThat(history.get(0).getCampus().getId()).isEqualTo(newCampus.getId());
		assertThat(history.get(1).getStatus()).isEqualTo(StaffAssignmentStatus.ENDED);
		assertThat(history.get(1).getEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));

		assertThat(auditLogRepository.findByOrganisationId(staffProfile.getOrganisationId(), Pageable.unpaged()).stream()
				.map(AuditLog::getAction))
				.contains("TRANSFER");
	}

	@Test
	void recordingAnExitFlipsStatusAndWritesAudit() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-exit");

		StaffExit exit = hrService.recordExit(staffProfile, StaffExitType.RESIGNATION, LocalDate.of(2026, 3, 1), "Relocating", 5L);

		assertThat(exit.getExitType()).isEqualTo(StaffExitType.RESIGNATION);
		assertThat(staffProfile.getStatus()).isEqualTo(StaffProfileStatus.RESIGNED);
		assertThat(staffExitRepository.findByStaffProfileId(staffProfile.getId())).hasSize(1);
		assertThat(auditLogRepository.findByOrganisationId(staffProfile.getOrganisationId(), Pageable.unpaged()).stream()
				.map(AuditLog::getAction))
				.contains("EXIT");
	}

	@Test
	void terminationExitMapsToTerminatedStatus() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-terminate");

		hrService.recordExit(staffProfile, StaffExitType.TERMINATION, LocalDate.of(2026, 3, 1), "Policy violation", 5L);

		assertThat(staffProfile.getStatus()).isEqualTo(StaffProfileStatus.TERMINATED);
	}

	@Test
	void bankDetailsUpsertInPlace() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-bank");

		hrService.upsertBankDetails(staffProfile, "Ravi Menon", "1234567890", "Test Bank", "TEST0001", "PAN1234X");
		hrService.upsertBankDetails(staffProfile, "Ravi Menon", "0987654321", "Test Bank", "TEST0001", "PAN1234X");

		assertThat(staffBankDetailRepository.findAll()).hasSize(1);
		StaffBankDetail detail = staffBankDetailRepository.findByStaffProfileId(staffProfile.getId()).orElseThrow();
		assertThat(detail.getBankAccountNumber()).isEqualTo("0987654321");
	}

	@Test
	void changingStatusWritesAudit() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-status-audit");

		hrService.changeStaffStatus(staffProfile, StaffProfileStatus.TERMINATED);

		assertThat(auditLogRepository.findByOrganisationId(staffProfile.getOrganisationId(), Pageable.unpaged()).stream()
				.map(AuditLog::getAction))
				.contains("STATUS_CHANGE");
	}
}
