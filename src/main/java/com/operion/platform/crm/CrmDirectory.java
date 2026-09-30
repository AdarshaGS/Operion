package com.operion.platform.crm;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.operion.common.TenantContext;
import com.operion.organisation.OrganisationRepository;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import org.springframework.stereotype.Component;

/** Shared lookups for the CRM services and controllers: resolving which record a
 * contact/activity/task belongs to, and platform-admin ids to display names. */
@Component
public class CrmDirectory {

	private final LeadRepository leadRepository;
	private final OrganisationRepository organisationRepository;
	private final PlatformAdminRepository platformAdminRepository;

	public CrmDirectory(LeadRepository leadRepository, OrganisationRepository organisationRepository,
			PlatformAdminRepository platformAdminRepository) {
		this.leadRepository = leadRepository;
		this.organisationRepository = organisationRepository;
		this.platformAdminRepository = platformAdminRepository;
	}

	/** Validates that exactly one of the two ids is given and that the record it names exists. */
	public CrmOwner owner(Long leadId, Long organisationId) {
		CrmOwner owner = new CrmOwner(leadId, organisationId);
		if (leadId != null && !leadRepository.existsById(leadId)) {
			throw new IllegalArgumentException("No lead with id " + leadId);
		}
		if (organisationId != null && !organisationRepository.existsById(organisationId)) {
			throw new IllegalArgumentException("No organisation with id " + organisationId);
		}
		return owner;
	}

	public void requireAdmin(Long platformAdminId) {
		if (platformAdminId != null && !platformAdminRepository.existsById(platformAdminId)) {
			throw new IllegalArgumentException("No platform admin with id " + platformAdminId);
		}
	}

	/** The platform admin making the current request (populated by PlatformAuthenticationInterceptor). */
	public Long currentAdminId() {
		return TenantContext.getActorId();
	}

	public List<PlatformAdmin> admins() {
		return platformAdminRepository.findAll();
	}

	public Map<Long, String> namesOf(Collection<Long> adminIds) {
		List<Long> ids = adminIds.stream().filter(Objects::nonNull).distinct().toList();
		if (ids.isEmpty()) {
			return new HashMap<>();
		}
		return platformAdminRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(PlatformAdmin::getId, PlatformAdmin::getName));
	}
}
