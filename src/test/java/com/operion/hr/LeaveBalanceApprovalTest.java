package com.operion.hr;

import com.operion.academic.WorkingCalendarEntry;
import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.academic.WorkingCalendarEntryType;
import com.operion.common.InvalidRequestException;
import com.operion.calendar.WorkingDayService;
import com.operion.organisation.OrganisationConfigurationRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.operion.attendance.AttendanceStatus;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
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
 * Proves HrService's leave approval rules: approving within the allocated balance
 * succeeds and reduces the live-computed remaining balance, approving beyond it (or
 * with no LeaveBalance row at all) is rejected, and approve/reject/cancel are one-way
 * transitions off PENDING.
 *
 * HrService is constructed by hand rather than @Import'd - see StaffAttendanceTest for why.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeaveBalanceApprovalTest {

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
	private AcademicYearRepository academicYearRepository;

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

	private record Fixture(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear) {
	}

	private Fixture setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Campus campus = campusRepository.save(new Campus("Main Campus", "MAIN"));
		AcademicYear academicYear = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		Person person = personRepository.save(new Person("Ravi", "Menon"));
		Designation designation = designationRepository.save(new Designation("Teacher"));
		Department department = departmentRepository.save(new Department("Science"));
		StaffProfile staffProfile = hrService.createStaffProfile(
				person, campus, "EMP-001", designation, department, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT);
		LeaveType leaveType = hrService.createLeaveType("CASUAL", "Casual Leave", 12.0);

		return new Fixture(staffProfile, leaveType, academicYear);
	}

	@Test
	void updatingLeaveTypePersistsNameAndDaysButKeepsCode() {
		Fixture fixture = setUpFixture("hr-update-leave-type");

		hrService.updateLeaveType(fixture.leaveType(), "  Casual Leave (CL) ", 10.0);

		LeaveType reloaded = leaveTypeRepository.findById(fixture.leaveType().getId()).orElseThrow();
		assertThat(reloaded.getName()).isEqualTo("Casual Leave (CL)");
		assertThat(reloaded.getDefaultAnnualDays()).isEqualTo(10.0);
		assertThat(reloaded.getCode()).isEqualTo("CASUAL");
		assertThatThrownBy(() -> hrService.updateLeaveType(reloaded, " ", 5.0)).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> hrService.updateLeaveType(reloaded, "X", -1.0)).isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void approvingWithinBalanceSucceedsAndReducesRemaining() {
		Fixture fixture = setUpFixture("hr-approve-within-balance");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 3.0, "Family event");

		LeaveRequest approved = hrService.approve(request, 99L);

		assertThat(approved.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
		assertThat(approved.getApprovedBy()).isEqualTo(99L);
		assertThat(leaveRequestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
		assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear())).isEqualTo(9.0);
	}

	@Test
	void approvingWritesLeaveAttendanceForEveryCoveredDate() {
		Fixture fixture = setUpFixture("hr-approve-writes-attendance");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 3.0, null);

		hrService.approve(request, 99L);

		Long personId = fixture.staffProfile().getPerson().getId();
		for (LocalDate date : List.of(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7))) {
			assertThat(staffAttendanceRepository.findByPersonIdAndAttendanceDate(personId, date))
					.hasValueSatisfying(attendance -> assertThat(attendance.getAttendanceStatus()).isEqualTo(AttendanceStatus.LEAVE));
		}
	}

	@Test
	void approvingBeyondBalanceIsRejected() {
		Fixture fixture = setUpFixture("hr-approve-over-balance");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 2.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 8), 4.0, null);

		assertThatThrownBy(() -> hrService.approve(request, 99L)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void approvingWithNoAllocatedBalanceIsRejected() {
		Fixture fixture = setUpFixture("hr-approve-no-balance");
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);

		assertThatThrownBy(() -> hrService.approve(request, 99L)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void approvingAnAlreadyDecidedRequestIsRejected() {
		Fixture fixture = setUpFixture("hr-approve-twice");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);
		hrService.approve(request, 99L);

		// The caller's copy still says PENDING; approve re-reads, so the answer is "not pending", not a balance error
		// (the 1-day request would otherwise be checked against the 11 remaining days and the stale copy let through).
		assertThatThrownBy(() -> hrService.approve(request, 99L))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Only a pending leave request");
	}

	@Test
	void reApprovingWithNoBalanceLeftStillReportsNotPendingRatherThanBalance() {
		Fixture fixture = setUpFixture("hr-approve-twice-exhausted");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 1.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);
		hrService.approve(request, 99L); // remaining is now 0, so a balance-first check would say "exceeds remaining"

		assertThatThrownBy(() -> hrService.approve(request, 99L))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Only a pending leave request");
	}

	@Test
	void cancellingAPendingRequestSucceedsButNotAfterDecision() {
		Fixture fixture = setUpFixture("hr-cancel");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest pending = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);
		hrService.cancel(pending);
		assertThat(pending.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);

		LeaveRequest approved = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 2, 5), LocalDate.of(2026, 2, 5), 1.0, null);
		LeaveRequest decided = hrService.approve(approved, 99L);
		assertThatThrownBy(() -> hrService.cancel(decided)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectingSetsStatusAndDecidedBy() {
		Fixture fixture = setUpFixture("hr-reject");
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);

		hrService.reject(request, 42L);

		assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
		assertThat(request.getApprovedBy()).isEqualTo(42L);
	}

	private void holiday(Fixture fixture, LocalDate date) {
		workingCalendarEntryRepository.save(
				new WorkingCalendarEntry(fixture.academicYear(), null, date, "Test holiday", WorkingCalendarEntryType.HOLIDAY));
	}

	@Test
	void holidayInsideTheRangeIsNotLeaveAndGetsNoLeaveAttendance() {
		Fixture fixture = setUpFixture("hr-leave-holiday-attendance");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		holiday(fixture, LocalDate.of(2026, 1, 6));
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 2.0, null);

		hrService.approve(request, 99L);

		Long personId = fixture.staffProfile().getPerson().getId();
		assertThat(staffAttendanceRepository.findByPersonIdAndAttendanceDate(personId, LocalDate.of(2026, 1, 5))).isPresent();
		assertThat(staffAttendanceRepository.findByPersonIdAndAttendanceDate(personId, LocalDate.of(2026, 1, 6))).isEmpty();
		assertThat(staffAttendanceRepository.findByPersonIdAndAttendanceDate(personId, LocalDate.of(2026, 1, 7))).isPresent();
	}

	@Test
	void leaveRequestDaysMustBeAPositiveMultipleOfAHalf() {
		Fixture fixture = setUpFixture("hr-leave-granularity");
		LocalDate monday = LocalDate.of(2026, 1, 5);
		LocalDate wednesday = LocalDate.of(2026, 1, 7);

		for (double bad : new double[] {0.0001, 0.3, 0.75, 1.25, 2.9999, Double.NaN}) {
			assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
					monday, wednesday, bad, null))
					.as("numberOfDays=%s", bad).isInstanceOf(InvalidRequestException.class).hasMessageContaining("0.5");
		}
		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				monday, wednesday, 0.0, null)).isInstanceOf(InvalidRequestException.class);
		assertThat(leaveRequestRepository.findByStaffProfileId(fixture.staffProfile().getId())).isEmpty();

		// 0.5, 1 and 1.5 all work (separate non-overlapping days, since overlapping requests are refused).
		assertThat(hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				monday, monday, 0.5, null).getNumberOfDays()).isEqualTo(0.5);
		assertThat(hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 6), 1.0, null).getNumberOfDays()).isEqualTo(1.0);
		assertThat(hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				wednesday, LocalDate.of(2026, 1, 8), 1.5, null).getNumberOfDays()).isEqualTo(1.5);
	}

	@Test
	void allocatedDaysMustBeAMultipleOfAHalf() {
		Fixture fixture = setUpFixture("hr-allocate-granularity");

		for (double bad : new double[] {0.0001, 0.3, 11.75, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), bad))
					.as("allocatedDays=%s", bad).isInstanceOf(InvalidRequestException.class);
		}
		assertThat(leaveBalanceRepository.findByStaffProfileIdAndAcademicYearId(fixture.staffProfile().getId(), fixture.academicYear().getId())).isEmpty();

		for (double good : new double[] {0.0, 0.5, 1.0, 12.5, 366.0}) {
			hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), good);
			assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear())).isEqualTo(good);
		}
	}

	@Test
	void askingForMoreDaysThanTheRangeHasWorkingDaysIsRejected() {
		Fixture fixture = setUpFixture("hr-leave-holiday-too-many");
		holiday(fixture, LocalDate.of(2026, 1, 6));

		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 3.0, null))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("2 working day");
	}

	@Test
	void aWeekendInsideTheRangeIsNotCounted() {
		Fixture fixture = setUpFixture("hr-leave-weekend");
		// Fri 9 and Sat 10 are working under the default Mon-Sat pattern, Sun 11 is the weekly off, Mon 12 works.
		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 12), 4.0, null))
				.isInstanceOf(InvalidRequestException.class);

		LeaveRequest ok = hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 9), LocalDate.of(2026, 1, 12), 3.0, null);
		assertThat(ok.getNumberOfDays()).isEqualTo(3.0);
	}

	@Test
	void aRangeThatIsEntirelyDaysOffIsRejected() {
		Fixture fixture = setUpFixture("hr-leave-all-off");

		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 11), 1.0, null)) // a Sunday
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("holiday or weekly off");
	}

	@Test
	void leaveOfAnInactiveLeaveTypeCannotBeRequested() {
		Fixture fixture = setUpFixture("hr-leave-inactive-type");
		LeaveType inactive = leaveTypeRepository.findById(fixture.leaveType().getId()).orElseThrow();
		org.springframework.test.util.ReflectionTestUtils.setField(inactive, "status", LeaveTypeStatus.INACTIVE);
		leaveTypeRepository.save(inactive);

		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), inactive, fixture.academicYear(),
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("not active");
	}

	@Test
	void leaveOutsideTheAcademicYearIsRejected() {
		Fixture fixture = setUpFixture("hr-leave-outside-year");

		// Year runs 2025-06-01 .. 2026-04-30.
		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2025, 5, 28), LocalDate.of(2025, 6, 2), 2.0, null))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("academic year");
		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 4, 29), LocalDate.of(2026, 5, 2), 2.0, null))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("academic year");
		// The boundary days themselves are inside.
		assertThat(hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 4, 29), LocalDate.of(2026, 4, 30), 2.0, null).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
	}

	@Test
	void leaveCannotBeRequestedForStaffWhoHaveLeft() {
		Fixture fixture = setUpFixture("hr-leave-exited-staff");
		hrService.recordExit(fixture.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2025, 12, 1), "Relocating", 5L);

		assertThatThrownBy(() -> hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("has left");
	}

	@Test
	void allocationCannotBeCutBelowDaysAlreadyApproved() {
		Fixture fixture = setUpFixture("hr-allocate-below-used");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 3.0, null);
		hrService.approve(request, 99L);

		assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 2.0))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("already approved");
		assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), -1.0))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear())).isEqualTo(9.0);

		// Exactly the used amount is allowed.
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 3.0);
		assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear())).isEqualTo(0.0);
	}

	@Test
	void recordingAnExitEndsTheOpenAssignmentAndCancelsOnlyLaterPendingLeave() {
		Fixture fixture = setUpFixture("hr-exit-cleanup");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest before = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);
		LeaveRequest after = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), 2.0, null);
		LeaveRequest approvedLater = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), 1.0, null);
		hrService.approve(approvedLater, 99L);

		hrService.recordExit(fixture.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2026, 2, 1), "Relocating", 5L);

		List<StaffAssignment> history = staffAssignmentRepository.findByStaffProfileIdOrderByStartDateDesc(fixture.staffProfile().getId());
		assertThat(history).hasSize(1);
		assertThat(history.get(0).getStatus()).isEqualTo(StaffAssignmentStatus.ENDED);
		assertThat(history.get(0).getEndDate()).isEqualTo(LocalDate.of(2026, 2, 1));
		assertThat(leaveRequestRepository.findById(before.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		assertThat(leaveRequestRepository.findById(after.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
		// Cancelling approved leave is a product decision, deliberately left alone.
		assertThat(leaveRequestRepository.findById(approvedLater.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
	}

	@Test
	void allocationIsCappedAtADaysInAYear() {
		Fixture fixture = setUpFixture("hr-allocate-cap");

		assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 366.5))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("366");
		assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 1_000_000))
				.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), Double.POSITIVE_INFINITY))
				.isInstanceOf(InvalidRequestException.class);
		assertThat(leaveBalanceRepository.findByStaffProfileIdAndAcademicYearId(fixture.staffProfile().getId(), fixture.academicYear().getId())).isEmpty();

		// The bound itself is allowed.
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 366.0);
		assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear())).isEqualTo(366.0);
	}

	@Test
	void leaveCannotBeAllocatedToStaffWhoHaveLeft() {
		Fixture fixture = setUpFixture("hr-allocate-exited-staff");
		hrService.recordExit(fixture.staffProfile(), StaffExitType.TERMINATION, LocalDate.of(2025, 12, 1), "Policy", 5L);

		assertThatThrownBy(() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 5.0))
				.isInstanceOf(InvalidRequestException.class).hasMessageContaining("has left");
		assertThat(leaveBalanceRepository.findByStaffProfileIdAndAcademicYearId(fixture.staffProfile().getId(), fixture.academicYear().getId())).isEmpty();
	}

	@Test
	void anExitWithdrawsPendingLeaveThatStartsBeforeItButRunsPastIt() {
		Fixture fixture = setUpFixture("hr-exit-spanning-leave");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest earlier = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);
		// Thu 8 Jan - Wed 14 Jan straddles the exit on Tue 13 Jan.
		LeaveRequest spanning = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 14), 2.0, null);

		hrService.recordExit(fixture.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2026, 1, 13), "Relocating", 5L);

		assertThat(leaveRequestRepository.findById(spanning.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
		assertThat(leaveRequestRepository.findById(earlier.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
	}

	@Test
	void anExitWithdrawsPendingLeaveThatEndsOnTheExitDate() {
		Fixture fixture = setUpFixture("hr-exit-leave-ends-on-exit");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest endsOnExit = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 9), 2.0, null);
		LeaveRequest endedBefore = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), 2.0, null);

		hrService.recordExit(fixture.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2026, 1, 9), "Relocating", 5L);

		assertThat(leaveRequestRepository.findById(endsOnExit.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
		assertThat(leaveRequestRepository.findById(endedBefore.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
	}

	@Test
	void aLeftoverPendingRequestOfStaffWhoHaveLeftCannotBeApproved() {
		Fixture fixture = setUpFixture("hr-approve-exited-staff");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		// Ends before the exit date, so the exit does not withdraw it and it stays PENDING.
		LeaveRequest leftover = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5), 1.0, null);
		hrService.recordExit(fixture.staffProfile(), StaffExitType.RESIGNATION, LocalDate.of(2026, 1, 13), "Relocating", 5L);
		assertThat(leaveRequestRepository.findById(leftover.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);

		assertThatThrownBy(() -> hrService.approve(leftover, 99L))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("has left");

		assertThat(leaveRequestRepository.findById(leftover.getId()).orElseThrow().getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
		assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear())).isEqualTo(12.0);
	}
}
