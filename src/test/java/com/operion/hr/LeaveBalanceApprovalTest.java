package com.operion.hr;

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
	void approvingWithinBalanceSucceedsAndReducesRemaining() {
		Fixture fixture = setUpFixture("hr-approve-within-balance");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 12.0);
		LeaveRequest request = hrService.raiseLeaveRequest(
				fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), 3.0, "Family event");

		hrService.approve(request, 99L);

		assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
		assertThat(request.getApprovedBy()).isEqualTo(99L);
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

		assertThatThrownBy(() -> hrService.approve(request, 99L)).isInstanceOf(IllegalStateException.class);
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
		hrService.approve(approved, 99L);
		assertThatThrownBy(() -> hrService.cancel(approved)).isInstanceOf(IllegalStateException.class);
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
}
