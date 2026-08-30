package com.operion.hr;

import java.time.LocalDate;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Derives StaffProfileStatus.ON_LEAVE day-by-day from approved leave requests, rather
 * than a manually-toggled status an admin could forget to revert - flips
 * ACTIVE -> ON_LEAVE when an approved LeaveRequest covers today, and ON_LEAVE -> ACTIVE
 * when none does. Never touches RESIGNED/TERMINATED. Runs once per tenant under that
 * tenant's TenantContext, same per-organisation poll shape as ScheduledAnnouncementPublisher.
 */
@Component
public class StaffLeaveStatusScheduler {

	private static final long POLL_INTERVAL_MILLIS = 24 * 60 * 60 * 1000;

	private final OrganisationRepository organisationRepository;
	private final StaffProfileRepository staffProfileRepository;
	private final LeaveRequestRepository leaveRequestRepository;

	public StaffLeaveStatusScheduler(OrganisationRepository organisationRepository, StaffProfileRepository staffProfileRepository,
			LeaveRequestRepository leaveRequestRepository) {
		this.organisationRepository = organisationRepository;
		this.staffProfileRepository = staffProfileRepository;
		this.leaveRequestRepository = leaveRequestRepository;
	}

	@Scheduled(fixedDelay = POLL_INTERVAL_MILLIS)
	public void syncLeaveStatuses() {
		LocalDate today = LocalDate.now();
		for (Organisation organisation : organisationRepository.findAll()) {
			TenantContext.set(organisation.getId(), null);
			try {
				List<StaffProfile> candidates = staffProfileRepository.findByStatusIn(
						List.of(StaffProfileStatus.ACTIVE, StaffProfileStatus.ON_LEAVE));
				for (StaffProfile staffProfile : candidates) {
					boolean onApprovedLeaveToday = leaveRequestRepository
							.existsByStaffProfileIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
									staffProfile.getId(), LeaveRequestStatus.APPROVED, today, today);
					if (onApprovedLeaveToday && staffProfile.getStatus() == StaffProfileStatus.ACTIVE) {
						staffProfile.changeStatus(StaffProfileStatus.ON_LEAVE);
						staffProfileRepository.save(staffProfile);
					} else if (!onApprovedLeaveToday && staffProfile.getStatus() == StaffProfileStatus.ON_LEAVE) {
						staffProfile.changeStatus(StaffProfileStatus.ACTIVE);
						staffProfileRepository.save(staffProfile);
					}
				}
			} finally {
				TenantContext.clear();
			}
		}
	}
}
