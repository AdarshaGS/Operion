package com.operion.platform.crm;

import java.time.Instant;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.api.CreateOrganisationRequest;
import org.springframework.stereotype.Service;
import com.operion.common.persistence.PlatformTransactional;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

	private final LeadRepository leadRepository;
	private final ContactRepository contactRepository;
	private final ActivityRepository activityRepository;
	private final OrganisationService organisationService;
	private final CrmDirectory crmDirectory;

	public LeadService(LeadRepository leadRepository, ContactRepository contactRepository, ActivityRepository activityRepository,
			OrganisationService organisationService, CrmDirectory crmDirectory) {
		this.leadRepository = leadRepository;
		this.contactRepository = contactRepository;
		this.activityRepository = activityRepository;
		this.organisationService = organisationService;
		this.crmDirectory = crmDirectory;
	}

	public List<Lead> all() {
		return leadRepository.findAllByOrderByCreatedAtDesc();
	}

	public Lead get(Long id) {
		return leadRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No lead with id " + id));
	}

	/** An unspecified owner defaults to the platform admin creating the lead. */
	@PlatformTransactional
	public Lead create(String schoolName, LeadSource source, Integer estimatedStudentCount, Long ownerId, String notes) {
		Long owner = ownerId != null ? ownerId : crmDirectory.currentAdminId();
		crmDirectory.requireAdmin(owner);
		return leadRepository.save(new Lead(schoolName.trim(), source, estimatedStudentCount, owner, blankToNull(notes)));
	}

	/** Partial update: a null field leaves the current value alone; a blank notes string clears it. */
	@PlatformTransactional
	public Lead update(Long id, String schoolName, LeadSource source, Integer estimatedStudentCount, Long ownerId, String notes) {
		Lead lead = get(id);
		crmDirectory.requireAdmin(ownerId);
		lead.update(schoolName != null && !schoolName.isBlank() ? schoolName.trim() : lead.getSchoolName(),
				source != null ? source : lead.getSource(),
				estimatedStudentCount != null ? estimatedStudentCount : lead.getEstimatedStudentCount(),
				ownerId != null ? ownerId : lead.getOwnerId(),
				notes != null ? blankToNull(notes) : lead.getNotes());
		return leadRepository.save(lead);
	}

	@PlatformTransactional
	public Lead changeStage(Long id, LeadStage target) {
		Lead lead = get(id);
		lead.changeStage(target);
		return leadRepository.save(lead);
	}

	// Deliberately not @Transactional - OrganisationService.provision() sets and then clears
	// TenantContext and relies on each of its own saves opening a fresh session (see the
	// comment on provision()), so wrapping it here would re-introduce the wrong-tenant-stamp
	// problem it documents. The lead is only touched after provisioning succeeds.
	/**
	 * Provisions an Organisation from a WON lead. Blank fields on the request fall back to what
	 * the lead already knows: the school name, and its first contact for the primary contact.
	 * A lead can only be converted once.
	 */
	public Lead convert(Long id, CreateOrganisationRequest request) {
		Lead lead = get(id);
		if (lead.getStage() != LeadStage.WON) {
			throw new IllegalStateException("Only a WON lead can be converted to an organisation");
		}
		if (lead.isConverted()) {
			throw new IllegalStateException("This lead has already been converted to an organisation");
		}

		Long actorId = TenantContext.getActorId();
		Contact primary = contactRepository.findByLeadIdOrderByNameAsc(id).stream().findFirst().orElse(null);
		CreateOrganisationRequest merged = withLeadDefaults(request, lead, primary);
		Organisation organisation = organisationService.provision(merged.toOrganisation(), merged.toProfile(),
				merged.toAdminAccount(), merged.toAcademicYearDetails(), merged.toPlanSelection());

		// provision() clears TenantContext; restore the platform actor for auditing the writes below.
		TenantContext.set(null, actorId);
		lead.markConverted(organisation.getId());
		leadRepository.save(lead);
		activityRepository.save(new Activity(new CrmOwner(lead.getId(), null), ActivityType.NOTE,
				"Converted to organisation \"" + organisation.getName() + "\"", Instant.now(), actorId));
		return lead;
	}

	private static CreateOrganisationRequest withLeadDefaults(CreateOrganisationRequest r, Lead lead, Contact primary) {
		return new CreateOrganisationRequest(
				blank(r.name()) ? lead.getSchoolName() : r.name(), r.legalName(), r.slug(),
				r.adminEmail(), r.adminPassword(), r.adminFirstName(), r.adminLastName(),
				r.organisationType(), r.board(), r.schoolCode(),
				blank(r.primaryContactName()) && primary != null ? primary.getName() : r.primaryContactName(),
				blank(r.primaryContactEmail()) && primary != null ? primary.getEmail() : r.primaryContactEmail(),
				blank(r.primaryContactPhone()) && primary != null ? primary.getPhone() : r.primaryContactPhone(),
				r.addressLine1(), r.addressLine2(), r.city(), r.state(), r.country(), r.pincode(),
				r.timezone(), r.academicYearName(), r.academicYearStartDate(), r.academicYearEndDate(),
				r.planId(), r.planStartDate());
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	static String blankToNull(String value) {
		return blank(value) ? null : value.trim();
	}
}
