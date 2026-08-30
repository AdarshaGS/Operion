package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Campus;
import com.operion.organisation.CampusRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.Designation;
import com.operion.organisation.DesignationRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves StaffLeaveStatusScheduler flips ACTIVE -> ON_LEAVE when an approved
 * LeaveRequest covers today, and back ACTIVE once it no longer does - never touching
 * RESIGNED/TERMINATED. Uses LeaveRequest directly (bypassing HrService.approve's own
 * StaffAttendance side effect) since only the status-derivation logic is under test here.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaffLeaveStatusSchedulerTest {

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
	private StaffProfileRepository staffProfileRepository;

	@Autowired
	private LeaveTypeRepository leaveTypeRepository;

	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

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
		return staffProfileRepository.save(
				new StaffProfile(person, campus, "EMP-001", designation, null, LocalDate.of(2020, 6, 1), EmploymentType.PERMANENT));
	}

	private StaffLeaveStatusScheduler scheduler() {
		return new StaffLeaveStatusScheduler(organisationRepository, staffProfileRepository, leaveRequestRepository);
	}

	@Test
	void flipsActiveToOnLeaveWhenApprovedLeaveCoversToday() {
		StaffProfile staffProfile = setUpStaffProfile("hr-scheduler-flip-on");
		LeaveType leaveType = leaveTypeRepository.save(new LeaveType("CASUAL", "Casual Leave", 12.0));
		AcademicYear academicYear = academicYearRepository.save(new AcademicYear("2025-2026", LocalDate.of(2025, 6, 1), LocalDate.of(2026, 4, 30)));
		LeaveRequest request = leaveRequestRepository.save(new LeaveRequest(
				staffProfile, leaveType, academicYear, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 3.0, null));
		request.approve(99L);
		leaveRequestRepository.save(request);

		scheduler().syncLeaveStatuses();
		TenantContext.set(staffProfile.getOrganisationId(), null);

		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus())
				.isEqualTo(StaffProfileStatus.ON_LEAVE);
	}

	@Test
	void flipsOnLeaveBackToActiveOnceLeaveNoLongerCoversToday() {
		StaffProfile staffProfile = setUpStaffProfile("hr-scheduler-flip-off");
		staffProfile.changeStatus(StaffProfileStatus.ON_LEAVE);
		staffProfileRepository.save(staffProfile);

		scheduler().syncLeaveStatuses();
		TenantContext.set(staffProfile.getOrganisationId(), null);

		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus())
				.isEqualTo(StaffProfileStatus.ACTIVE);
	}

	@Test
	void neverTouchesResignedOrTerminated() {
		StaffProfile staffProfile = setUpStaffProfile("hr-scheduler-skip-exited");
		staffProfile.changeStatus(StaffProfileStatus.RESIGNED);
		staffProfileRepository.save(staffProfile);

		scheduler().syncLeaveStatuses();
		TenantContext.set(staffProfile.getOrganisationId(), null);

		assertThat(staffProfileRepository.findById(staffProfile.getId()).orElseThrow().getStatus())
				.isEqualTo(StaffProfileStatus.RESIGNED);
	}
}
