package com.operion.platform.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.organisation.OrganisationService;
import com.operion.organisation.ProvisioningProfile;
import com.operion.organisation.api.CreateOrganisationRequest;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform CRM (milestone 32). @DataJpaTest with the services constructed by hand, same
 * approach as OrganisationProvisioningTest: no ObjectMapper bean or web layer in the slice.
 * OrganisationService is mocked for the convert path - provisioning itself is covered by
 * OrganisationProvisioningTest; here we only care what the CRM hands it.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, CrmDirectory.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformCrmServicesTest {

	@Autowired
	private LeadRepository leadRepository;
	@Autowired
	private ContactRepository contactRepository;
	@Autowired
	private ActivityRepository activityRepository;
	@Autowired
	private CrmTaskRepository crmTaskRepository;
	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private PlatformAdminRepository platformAdminRepository;
	@Autowired
	private CrmDirectory crmDirectory;

	private OrganisationService organisationService;
	private LeadService leadService;
	private ContactService contactService;
	private ActivityService activityService;
	private CrmTaskService crmTaskService;
	private CrmMetricsService crmMetricsService;
	private PlatformAdmin admin;

	@BeforeEach
	void setUp() {
		organisationService = mock(OrganisationService.class);
		leadService = new LeadService(leadRepository, contactRepository, activityRepository, organisationService, crmDirectory);
		contactService = new ContactService(contactRepository, crmDirectory);
		activityService = new ActivityService(activityRepository, crmDirectory);
		crmTaskService = new CrmTaskService(crmTaskRepository, crmDirectory);
		crmMetricsService = new CrmMetricsService(leadRepository, crmTaskRepository);

		admin = platformAdminRepository.save(new PlatformAdmin("Alice", "alice-" + System.nanoTime() + "@operion.platform", "hash"));
		TenantContext.set(null, admin.getId());
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		crmTaskRepository.deleteAll();
		activityRepository.deleteAll();
		contactRepository.deleteAll();
		leadRepository.deleteAll();
	}

	@Test
	void newLeadStartsAtNewStageOwnedByTheCreatingAdmin() {
		Lead lead = leadService.create("  Greenfield Academy ", LeadSource.REFERRAL, 400, null, "  ");

		assertThat(lead.getSchoolName()).isEqualTo("Greenfield Academy");
		assertThat(lead.getStage()).isEqualTo(LeadStage.NEW);
		assertThat(lead.getOwnerId()).isEqualTo(admin.getId());
		assertThat(lead.getNotes()).isNull();
		assertThat(lead.isConverted()).isFalse();
	}

	@Test
	void rejectsAnOwnerThatIsNotAPlatformAdmin() {
		assertThatThrownBy(() -> leadService.create("Greenfield", LeadSource.INBOUND, null, 999_999L, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void updateLeavesNullFieldsUnchangedAndBlankNotesClear() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, 100, null, "first note");

		Lead updated = leadService.update(lead.getId(), null, LeadSource.OUTREACH, null, null, null);
		assertThat(updated.getSchoolName()).isEqualTo("Greenfield");
		assertThat(updated.getSource()).isEqualTo(LeadSource.OUTREACH);
		assertThat(updated.getEstimatedStudentCount()).isEqualTo(100);
		assertThat(updated.getNotes()).isEqualTo("first note");

		assertThat(leadService.update(lead.getId(), null, null, null, null, "").getNotes()).isNull();
	}

	@Test
	void stagesMoveFreelyWithNoTransitionMap() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, null, null, null);

		assertThat(leadService.changeStage(lead.getId(), LeadStage.LOST).getStage()).isEqualTo(LeadStage.LOST);
		assertThat(leadService.changeStage(lead.getId(), LeadStage.NEW).getStage()).isEqualTo(LeadStage.NEW);
	}

	@Test
	void contactOrActivityOrTaskNeedsExactlyOneOwner() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, null, null, null);
		Organisation organisation = organisationRepository.save(new Organisation("Org", "Org Trust", "org-" + System.nanoTime()));

		assertThatThrownBy(() -> contactService.create(null, null, "Nobody", null, null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Exactly one");
		assertThatThrownBy(() -> contactService.create(lead.getId(), organisation.getId(), "Both", null, null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Exactly one");
		assertThatThrownBy(() -> activityService.log(lead.getId(), organisation.getId(), ActivityType.NOTE, "x", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> crmTaskService.create(null, null, "t", Instant.now(), null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> contactService.create(999_999L, null, "Ghost", null, null, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No lead");
	}

	@Test
	void contactsListPerOwnerAndSupportUpdateAndDelete() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, null, null, null);
		Organisation organisation = organisationRepository.save(new Organisation("Org", "Org Trust", "org-" + System.nanoTime()));

		Contact principal = contactService.create(lead.getId(), null, "Priya", "Principal", "priya@school.test", "999");
		contactService.create(null, organisation.getId(), "Omar", "Finance", null, null);

		assertThat(contactService.list(lead.getId(), null)).extracting(Contact::getName).containsExactly("Priya");
		assertThat(contactService.list(null, organisation.getId())).extracting(Contact::getName).containsExactly("Omar");

		Contact updated = contactService.update(principal.getId(), null, "Head", "", null);
		assertThat(updated.getName()).isEqualTo("Priya");
		assertThat(updated.getRole()).isEqualTo("Head");
		assertThat(updated.getEmail()).isNull();
		assertThat(updated.getPhone()).isEqualTo("999");

		contactService.delete(principal.getId());
		assertThat(contactService.list(lead.getId(), null)).isEmpty();
	}

	@Test
	void activitiesAreAuthoredByTheCallerAndListNewestFirst() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, null, null, null);
		Instant earlier = Instant.now().minus(2, ChronoUnit.DAYS);

		activityService.log(lead.getId(), null, ActivityType.CALL, "Intro call", earlier);
		activityService.log(lead.getId(), null, ActivityType.NOTE, "Follow-up note", null);

		List<Activity> timeline = activityService.list(lead.getId(), null);
		assertThat(timeline).extracting(Activity::getBody).containsExactly("Follow-up note", "Intro call");
		assertThat(timeline).allSatisfy(activity -> assertThat(activity.getAuthorId()).isEqualTo(admin.getId()));
	}

	@Test
	void tasksDefaultToTheCallerAndCompleteIsIdempotent() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, null, null, null);
		CrmTask soon = crmTaskService.create(lead.getId(), null, "Send proposal", Instant.now().plus(1, ChronoUnit.DAYS), null);
		crmTaskService.create(lead.getId(), null, "Call back", Instant.now().plus(3, ChronoUnit.DAYS), null);

		assertThat(soon.getAssigneeId()).isEqualTo(admin.getId());
		assertThat(crmTaskService.list(lead.getId(), null, null, true)).extracting(CrmTask::getTitle)
				.containsExactly("Send proposal", "Call back");

		CrmTask done = crmTaskService.complete(soon.getId());
		Instant firstCompletion = done.getCompletedAt();
		assertThat(firstCompletion).isNotNull();
		assertThat(crmTaskService.complete(soon.getId()).getCompletedAt()).isEqualTo(firstCompletion);

		assertThat(crmTaskService.list(lead.getId(), null, null, true)).extracting(CrmTask::getTitle).containsExactly("Call back");
		assertThat(crmTaskService.list(lead.getId(), null, null, false)).hasSize(2);
		assertThat(crmTaskService.list(null, null, 999_999L, false)).isEmpty();
	}

	@Test
	void metricsCountLeadsByStageWinRateAndOverdueTasks() {
		Lead won = leadService.create("A", LeadSource.INBOUND, null, null, null);
		Lead lost = leadService.create("B", LeadSource.INBOUND, null, null, null);
		Lead open = leadService.create("C", LeadSource.INBOUND, null, null, null);
		leadService.changeStage(won.getId(), LeadStage.WON);
		leadService.changeStage(lost.getId(), LeadStage.LOST);
		leadService.changeStage(open.getId(), LeadStage.PROPOSAL);
		crmTaskService.create(open.getId(), null, "Overdue", Instant.now().minus(1, ChronoUnit.DAYS), null);
		crmTaskService.create(open.getId(), null, "Later", Instant.now().plus(1, ChronoUnit.DAYS), null);

		CrmMetricsService.Metrics metrics = crmMetricsService.current();

		assertThat(metrics.leadsByStage()).containsEntry(LeadStage.WON, 1L).containsEntry(LeadStage.LOST, 1L)
				.containsEntry(LeadStage.PROPOSAL, 1L).containsEntry(LeadStage.NEW, 0L);
		assertThat(metrics.winRate()).isEqualTo(0.5);
		assertThat(metrics.openTaskCount()).isEqualTo(2);
		assertThat(metrics.overdueTaskCount()).isEqualTo(1);
	}

	@Test
	void winRateIsUndefinedUntilALeadCloses() {
		leadService.create("A", LeadSource.INBOUND, null, null, null);

		assertThat(crmMetricsService.current().winRate()).isNull();
	}

	@Test
	void onlyAWonLeadCanBeConverted() {
		Lead lead = leadService.create("Greenfield", LeadSource.INBOUND, null, null, null);

		assertThatThrownBy(() -> leadService.convert(lead.getId(), request()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("WON");
	}

	@Test
	void convertProvisionsAnOrganisationFromLeadDefaultsAndCannotRunTwice() {
		Lead lead = leadService.create("Greenfield Academy", LeadSource.INBOUND, null, null, null);
		contactService.create(lead.getId(), null, "Priya", "Principal", "priya@school.test", "999");
		leadService.changeStage(lead.getId(), LeadStage.WON);
		when(organisationService.provision(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			// the real provision() clears the thread's context - mimic that so the restore is exercised
			TenantContext.clear();
			return organisationRepository.save(invocation.getArgument(0));
		});

		Lead converted = leadService.convert(lead.getId(), request());

		ArgumentCaptor<Organisation> organisation = ArgumentCaptor.forClass(Organisation.class);
		ArgumentCaptor<ProvisioningProfile> profile = ArgumentCaptor.forClass(ProvisioningProfile.class);
		org.mockito.Mockito.verify(organisationService).provision(organisation.capture(), profile.capture(), any(), any(), any());
		assertThat(organisation.getValue().getName()).isEqualTo("Greenfield Academy");
		assertThat(profile.getValue().primaryContactName()).isEqualTo("Priya");
		assertThat(profile.getValue().primaryContactEmail()).isEqualTo("priya@school.test");

		assertThat(converted.getConvertedOrganisationId()).isNotNull();
		assertThat(leadRepository.findById(lead.getId()).orElseThrow().getConvertedOrganisationId())
				.isEqualTo(converted.getConvertedOrganisationId());
		assertThat(activityService.list(lead.getId(), null)).extracting(Activity::getBody)
				.anyMatch(body -> body.contains("Converted to organisation"));
		assertThat(activityService.list(lead.getId(), null).get(0).getAuthorId()).isEqualTo(admin.getId());

		assertThatThrownBy(() -> leadService.convert(lead.getId(), request()))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("already been converted");
	}

	private CreateOrganisationRequest request() {
		return new CreateOrganisationRequest(null, null, "greenfield-" + System.nanoTime(), "admin@school.test", "pw",
				"Ada", "Admin", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null);
	}
}
