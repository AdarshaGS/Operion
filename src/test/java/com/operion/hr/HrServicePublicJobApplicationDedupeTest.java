package com.operion.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.operion.attendance.StaffAttendanceRepository;
import com.operion.audit.AuditLogService;
import com.operion.calendar.WorkingDayService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A second public application from the same email is still deduplicated server-side, but is reported to the caller
 * as "not stored" (the controller turns both outcomes into the same response) instead of throwing a 409. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrServicePublicJobApplicationDedupeTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private JobApplicationRepository jobApplicationRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private HrService hrService() {
		return new HrService(mock(StaffProfileRepository.class), mock(LeaveTypeRepository.class), mock(LeaveBalanceRepository.class),
				mock(LeaveRequestRepository.class), mock(StaffDocumentRepository.class), jobApplicationRepository,
				organisationRepository, mock(StaffAssignmentRepository.class), mock(StaffExitRepository.class),
				mock(StaffBankDetailRepository.class), mock(StaffAttendanceRepository.class), mock(WorkingDayService.class),
				mock(AuditLogService.class));
	}

	@Test
	void aRepeatApplicationIsDedupedWithoutErrorAndStoresNoSecondRow() {
		Organisation organisation = organisationRepository.save(new Organisation("School", "School Trust", "dedupe-" + System.nanoTime()));
		HrService service = hrService();

		boolean first = service.submitJobApplication(organisation.getSlug(), "Asha", "asha@example.com", "Maths", 3);
		TenantContext.clear();
		boolean second = service.submitJobApplication(organisation.getSlug(), "Asha Again", "ASHA@example.com", "Maths", 3);

		assertThat(first).isTrue();
		assertThat(second).isFalse();
		TenantContext.set(organisation.getId(), null);
		assertThat(jobApplicationRepository.findAll()).hasSize(1);
	}
}
