package com.operion.platform.crm;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Insert-only - there is deliberately no update or delete, it's an audit trail. */
@Service
public class ActivityService {

	private final ActivityRepository activityRepository;
	private final CrmDirectory crmDirectory;

	public ActivityService(ActivityRepository activityRepository, CrmDirectory crmDirectory) {
		this.activityRepository = activityRepository;
		this.crmDirectory = crmDirectory;
	}

	public List<Activity> list(Long leadId, Long organisationId) {
		CrmOwner owner = crmDirectory.owner(leadId, organisationId);
		return owner.leadId() != null ? activityRepository.findByLeadIdOrderByOccurredAtDesc(owner.leadId())
				: activityRepository.findByOrganisationIdOrderByOccurredAtDesc(owner.organisationId());
	}

	/** The author is always the platform admin making the request; occurredAt defaults to now. */
	@Transactional
	public Activity log(Long leadId, Long organisationId, ActivityType type, String body, Instant occurredAt) {
		CrmOwner owner = crmDirectory.owner(leadId, organisationId);
		return activityRepository.save(new Activity(owner, type, body.trim(),
				occurredAt != null ? occurredAt : Instant.now(), crmDirectory.currentAdminId()));
	}
}
