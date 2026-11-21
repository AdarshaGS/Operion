package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.operion.academic.WorkingCalendarEntryRepository;
import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.calendar.WorkingDayService;
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
import com.operion.organisation.OrganisationConfigurationRepository;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-thread regression tests for the per-staff lock in HrService: approvals of DIFFERENT leave requests for one person
 * must not overdraw the balance, and parallel first-time allocations must leave exactly one balance row.
 *
 * What this proves: with the service behind a real transaction proxy, N threads released together against one
 * database serialise on the staff profile row (H2, MySQL mode, READ_COMMITTED). What it does not prove: MySQL's
 * REPEATABLE READ snapshot behaviour or InnoDB lock timing - H2 is the only engine the test suite runs on - and
 * thread scheduling makes the unlocked failure probabilistic (it was seen to fail with the lock removed, but a green
 * run alone is not a proof of absence of the race).
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrLeaveConcurrencyTest {

	private static final int THREADS = 4;

	@Autowired private WorkingCalendarEntryRepository workingCalendarEntryRepository;
	@Autowired private OrganisationConfigurationRepository organisationConfigurationRepository;
	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private AcademicYearRepository academicYearRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private DesignationRepository designationRepository;
	@Autowired private DepartmentRepository departmentRepository;
	@Autowired private StaffProfileRepository staffProfileRepository;
	@Autowired private LeaveTypeRepository leaveTypeRepository;
	@Autowired private LeaveBalanceRepository leaveBalanceRepository;
	@Autowired private LeaveRequestRepository leaveRequestRepository;
	@Autowired private StaffDocumentRepository staffDocumentRepository;
	@Autowired private JobApplicationRepository jobApplicationRepository;
	@Autowired private StaffAssignmentRepository staffAssignmentRepository;
	@Autowired private StaffExitRepository staffExitRepository;
	@Autowired private StaffBankDetailRepository staffBankDetailRepository;
	@Autowired private StaffAttendanceRepository staffAttendanceRepository;
	@Autowired private AuditLogRepository auditLogRepository;
	@Autowired private PlatformTransactionManager transactionManager;

	private HrService hrService;
	private Long organisationId;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private record Fixture(StaffProfile staffProfile, LeaveType leaveType, AcademicYear academicYear) {
	}

	private Fixture setUpFixture(String slug) {
		HrService target = new HrService(staffProfileRepository, leaveTypeRepository, leaveBalanceRepository, leaveRequestRepository,
				staffDocumentRepository, jobApplicationRepository, organisationRepository, staffAssignmentRepository,
				staffExitRepository, staffBankDetailRepository, staffAttendanceRepository,
				new WorkingDayService(workingCalendarEntryRepository, organisationConfigurationRepository),
				new AuditLogService(auditLogRepository, new ObjectMapper()));
		// The staff lock is a PESSIMISTIC_WRITE read and needs a real transaction, as it has in the running application.
		ProxyFactory proxyFactory = new ProxyFactory(target);
		proxyFactory.setProxyTargetClass(true);
		proxyFactory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
		hrService = (HrService) proxyFactory.getProxy();

		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		organisationId = organisation.getId();
		TenantContext.set(organisationId, null);
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

	/** Runs every task on its own thread, all released at once, and returns each outcome (null = success, else the exception). */
	private List<Throwable> runTogether(List<Callable<Object>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<Throwable>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(pool.submit(() -> {
					TenantContext.set(organisationId, null);
					try {
						ready.countDown();
						go.await();
						task.call();
						return null;
					} catch (Throwable ex) {
						return ex;
					} finally {
						TenantContext.clear();
					}
				}));
			}
			assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
			go.countDown();
			List<Throwable> outcomes = new ArrayList<>();
			for (Future<Throwable> future : futures) {
				outcomes.add(future.get(60, TimeUnit.SECONDS));
			}
			return outcomes;
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void concurrentApprovalsOfDifferentRequestsCannotOverdrawTheBalance() throws Exception {
		Fixture fixture = setUpFixture("hr-concurrent-approve");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 3.0);
		// Four non-overlapping 2-day requests (Mon-Tue of four weeks): only one fits into 3 days.
		List<LeaveRequest> requests = new ArrayList<>();
		for (int week = 0; week < THREADS; week++) {
			LocalDate monday = LocalDate.of(2026, 1, 5).plusWeeks(week);
			requests.add(hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
					monday, monday.plusDays(1), 2.0, null));
		}

		List<Throwable> outcomes = runTogether(requests.stream()
				.<Callable<Object>>map(request -> () -> hrService.approve(request, 99L))
				.toList());

		long approved = leaveRequestRepository.findByStaffProfileId(fixture.staffProfile().getId()).stream()
				.filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED).count();
		assertThat(approved).as("approved requests (outcomes: %s)", outcomes).isEqualTo(1);
		assertThat(outcomes.stream().filter(o -> o == null).count()).isEqualTo(1);
		assertThat(outcomes.stream().filter(o -> o != null)).allSatisfy(o -> assertThat(o)
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("exceeds remaining balance"));
		assertThat(hrService.getRemainingBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear()))
				.isEqualTo(1.0);
	}

	@Test
	void concurrentFirstAllocationsLeaveExactlyOneBalanceRow() throws Exception {
		Fixture fixture = setUpFixture("hr-concurrent-allocate");

		List<Throwable> outcomes = runTogether(java.util.stream.IntStream.range(0, THREADS)
				.<Callable<Object>>mapToObj(i -> () -> hrService.allocateBalance(
						fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 5.0 + i))
				.toList());

		assertThat(outcomes).as("every allocation succeeds, none fails on the unique key").containsOnlyNulls();
		List<LeaveBalance> rows = leaveBalanceRepository.findByStaffProfileIdAndAcademicYearId(
				fixture.staffProfile().getId(), fixture.academicYear().getId());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getAllocatedDays()).isBetween(5.0, 8.0);
	}

	@Test
	void anAllocationRacingApprovalsNeverDropsBelowWhatWasApproved() throws Exception {
		Fixture fixture = setUpFixture("hr-concurrent-allocate-vs-approve");
		hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 4.0);
		LeaveRequest request = hrService.raiseLeaveRequest(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(),
				LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), 2.0, null);

		List<Callable<Object>> tasks = List.of(
				() -> hrService.approve(request, 99L),
				() -> hrService.allocateBalance(fixture.staffProfile(), fixture.leaveType(), fixture.academicYear(), 1.0));
		List<Throwable> outcomes = runTogether(tasks);

		// Either order is legal; what is not is an approval that survives next to an allocation smaller than it.
		double allocated = leaveBalanceRepository.findByStaffProfileIdAndLeaveTypeIdAndAcademicYearId(
				fixture.staffProfile().getId(), fixture.leaveType().getId(), fixture.academicYear().getId()).orElseThrow().getAllocatedDays();
		boolean approved = leaveRequestRepository.findById(request.getId()).orElseThrow().getStatus() == LeaveRequestStatus.APPROVED;
		assertThat(!approved || allocated >= 2.0).as("approved=%s allocated=%s outcomes=%s", approved, allocated, outcomes).isTrue();
	}
}
