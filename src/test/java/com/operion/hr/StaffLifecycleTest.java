package com.operion.hr;

import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.calendar.WorkingDayService;
import com.operion.organisation.OrganisationConfigurationRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLog;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.InvalidRequestException;
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
	private WorkingCalendarEntryRepository workingCalendarEntryRepository;

	@Autowired
	private OrganisationConfigurationRepository organisationConfigurationRepository;

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
				new WorkingDayService(workingCalendarEntryRepository, organisationConfigurationRepository),
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
	void aMaskedAccountNumberOrTaxIdentifierPostedBackKeepsTheStoredValues() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-bank-mask-keep");
		hrService.upsertBankDetails(staffProfile, "Ravi Menon", "1234567890", "Test Bank", "TEST0001", "ABCDE1234F");

		// What a non-manager's edit form echoes back, with another field genuinely edited.
		hrService.upsertBankDetails(staffProfile, "Ravi K Menon", "******7890", "New Bank", "TEST0002", "******234F");

		StaffBankDetail detail = staffBankDetailRepository.findByStaffProfileId(staffProfile.getId()).orElseThrow();
		assertThat(detail.getBankAccountNumber()).isEqualTo("1234567890");
		assertThat(detail.getTaxIdentifier()).isEqualTo("ABCDE1234F");
		assertThat(detail.getBankAccountHolderName()).isEqualTo("Ravi K Menon");
		assertThat(detail.getBankName()).isEqualTo("New Bank");
		assertThat(detail.getBankBranchCode()).isEqualTo("TEST0002");
	}

	@Test
	void aMaskedValueIsRefusedWhenThereIsNothingStoredToKeep() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-bank-mask-new");

		assertThatThrownBy(() -> hrService.upsertBankDetails(staffProfile, "Ravi Menon", "******7890", "Test Bank", "TEST0001", null))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> hrService.upsertBankDetails(staffProfile, "Ravi Menon", "1234567890", "Test Bank", "TEST0001", "******234F"))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(staffBankDetailRepository.findAll()).isEmpty();
	}

	@Test
	void aRealNewAccountNumberStillReplacesTheStoredOne() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-bank-mask-replace");
		hrService.upsertBankDetails(staffProfile, "Ravi Menon", "1234567890", "Test Bank", "TEST0001", "ABCDE1234F");

		hrService.upsertBankDetails(staffProfile, "Ravi Menon", "5555666677", "Test Bank", "TEST0001", "ZZZZZ9999Z");

		StaffBankDetail detail = staffBankDetailRepository.findByStaffProfileId(staffProfile.getId()).orElseThrow();
		assertThat(detail.getBankAccountNumber()).isEqualTo("5555666677");
		assertThat(detail.getTaxIdentifier()).isEqualTo("ZZZZZ9999Z");
	}

	@Test
	void aStaffProfileCanBeCreatedAtBothLimitsOfTheJoiningDateWindow() {
		StaffProfile earliest = setUpStaffProfile("hr-lifecycle-joining-limits");
		Person second = personRepository.save(new Person("Asha", "Nair"));
		Person third = personRepository.save(new Person("Kiran", "Rao"));
		Designation designation = designationRepository.save(new Designation("Clerk"));

		StaffProfile atFloor = hrService.createStaffProfile(second, null, "EMP-OLD", designation, null, LocalDate.of(1950, 1, 1), EmploymentType.PERMANENT);
		StaffProfile atCeiling = hrService.createStaffProfile(third, null, "EMP-NEW", designation, null, LocalDate.now().plusDays(365), EmploymentType.PERMANENT);

		assertThat(atFloor.getDateOfJoining()).isEqualTo(LocalDate.of(1950, 1, 1));
		assertThat(atCeiling.getDateOfJoining()).isEqualTo(LocalDate.now().plusDays(365));
		assertThat(earliest.getId()).isNotNull();
	}

	@Test
	void aJoiningDateOneDayBeyondEitherLimitIsRejectedAndWritesNothing() {
		StaffProfile existing = setUpStaffProfile("hr-lifecycle-joining-beyond");
		Person second = personRepository.save(new Person("Asha", "Nair"));
		Designation designation = designationRepository.save(new Designation("Clerk"));

		assertThatThrownBy(() -> hrService.createStaffProfile(second, null, "EMP-OLD", designation, null, LocalDate.of(1949, 12, 31), EmploymentType.PERMANENT))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("1950-01-01");
		assertThatThrownBy(() -> hrService.createStaffProfile(second, null, "EMP-NEW", designation, null, LocalDate.now().plusDays(366), EmploymentType.PERMANENT))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("365");
		assertThat(staffProfileRepository.findAll()).extracting(StaffProfile::getId).containsExactly(existing.getId());
	}

	@Test
	void changingStatusWritesAudit() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-status-audit");

		hrService.changeStaffStatus(staffProfile, StaffProfileStatus.ON_LEAVE);

		assertThat(auditLogRepository.findByOrganisationId(staffProfile.getOrganisationId(), Pageable.unpaged()).stream()
				.map(AuditLog::getAction))
				.contains("STATUS_CHANGE");
	}

	@Test
	void theStatusEndpointCannotSetAnExitStatusAndPointsToTheExitFlow() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-status-no-exit");

		assertThatThrownBy(() -> hrService.changeStaffStatus(staffProfile, StaffProfileStatus.RESIGNED))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("exit");
		assertThatThrownBy(() -> hrService.changeStaffStatus(staffProfile, StaffProfileStatus.TERMINATED))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("exit");

		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.ACTIVE);
		assertThat(staffExitRepository.findByStaffProfileId(staffProfile.getId())).isEmpty();
	}

	@Test
	void activeAndOnLeaveStillSwitchFreely() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-status-active-on-leave");

		hrService.changeStaffStatus(staffProfile, StaffProfileStatus.ON_LEAVE);
		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.ON_LEAVE);
		hrService.changeStaffStatus(staffProfile, StaffProfileStatus.ACTIVE);
		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.ACTIVE);
	}

	@Test
	void aStaffMemberWhoHasLeftCannotBeReactivatedThroughTheStatusEndpoint() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-status-reactivate");
		hrService.recordExit(staffProfile, StaffExitType.RESIGNATION, LocalDate.of(2026, 3, 1), "Relocating", 5L);

		assertThatThrownBy(() -> hrService.changeStaffStatus(staffProfile, StaffProfileStatus.ACTIVE))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already left");
		assertThatThrownBy(() -> hrService.changeStaffStatus(staffProfile, StaffProfileStatus.ON_LEAVE))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already left");

		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.RESIGNED);
		assertThat(staffAssignmentRepository.findByStaffProfileIdAndStatus(staffProfile.getId(), StaffAssignmentStatus.ACTIVE)).isEmpty();
	}

	@Test
	void theExitFlowStillSetsTheExitStatusThroughItsOwnPath() {
		StaffProfile resigning = setUpStaffProfile("hr-lifecycle-exit-path-resign");
		hrService.recordExit(resigning, StaffExitType.RETIREMENT, LocalDate.of(2026, 3, 1), null, 5L);
		assertThat(staffProfileRepository.findById(resigning.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.RESIGNED);

		StaffProfile terminated = setUpStaffProfile("hr-lifecycle-exit-path-terminate");
		hrService.recordExit(terminated, StaffExitType.TERMINATION, LocalDate.of(2026, 3, 1), "Policy violation", 5L);
		assertThat(staffProfileRepository.findById(terminated.getId()).orElseThrow().getStatus()).isEqualTo(StaffProfileStatus.TERMINATED);
	}

	@Test
	void aStaffMemberWhoHasLeftCannotBeTransferred() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-transfer-exited");
		hrService.recordExit(staffProfile, StaffExitType.RESIGNATION, LocalDate.of(2026, 3, 1), "Relocating", 5L);
		Designation newDesignation = designationRepository.save(new Designation("Head Teacher"));

		assertThatThrownBy(() -> hrService.transfer(staffProfile, null, null, newDesignation, LocalDate.of(2026, 4, 1)))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("has left");

		List<StaffAssignment> history = staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(staffProfile.getId());
		assertThat(history).hasSize(1);
		assertThat(history.get(0).getStatus()).isEqualTo(StaffAssignmentStatus.ENDED);
	}

	@Test
	void aTransferCannotTakeEffectBeforeTheDateOfJoining() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-transfer-before-joining"); // joined 2020-06-01
		Designation newDesignation = designationRepository.save(new Designation("Head Teacher"));

		assertThatThrownBy(() -> hrService.transfer(staffProfile, null, null, newDesignation, LocalDate.of(2020, 5, 31)))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("date of joining");

		assertThat(staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(staffProfile.getId())).hasSize(1);
		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getDesignation().getId())
				.isEqualTo(staffProfile.getDesignation().getId());
	}

	@Test
	void aTransferCannotTakeEffectBeforeTheCurrentAssignmentStarted() {
		StaffProfile staffProfile = setUpStaffProfile("hr-lifecycle-transfer-before-current");
		Designation head = designationRepository.save(new Designation("Head Teacher"));
		Designation principal = designationRepository.save(new Designation("Principal"));
		hrService.transfer(staffProfile, null, null, head, LocalDate.of(2026, 2, 1));

		assertThatThrownBy(() -> hrService.transfer(staffProfile, null, null, principal, LocalDate.of(2026, 1, 15)))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("current assignment started");

		List<StaffAssignment> history = staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(staffProfile.getId());
		assertThat(history).hasSize(2);
		assertThat(history.get(0).getStatus()).isEqualTo(StaffAssignmentStatus.ACTIVE);
		assertThat(history.get(1).getEndDate()).isEqualTo(LocalDate.of(2026, 2, 1)); // not inverted
		// The start day itself is fine.
		hrService.transfer(staffProfile, null, null, principal, LocalDate.of(2026, 2, 1));
		assertThat(staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(staffProfile.getId())).hasSize(3);
	}
}
