package com.operion.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.privacy.ConsentService.ConsentState;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Proves #234: consent is insert-only history with the latest row as current state, and a data
 * subject request can only be completed - or have data released - after identity verification. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConsentAndDsrTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private ConsentPurposeRepository purposeRepository;

	@Autowired
	private ConsentRecordRepository recordRepository;

	@Autowired
	private DataSubjectRequestRepository requestRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	private ConsentService consentService;
	private DataSubjectRequestService requestService;
	private Person person;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void setUp(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), 5L);
		AuditLogService auditLogService = new AuditLogService(auditLogRepository, new ObjectMapper());
		consentService = new ConsentService(purposeRepository, recordRepository, auditLogService);
		requestService = new DataSubjectRequestService(requestRepository, consentService, auditLogService);
		person = personRepository.save(new Person("Anaya", "Rao"));
	}

	@Test
	void nothingIsSeededAndDuplicatePurposeCodesAreRejected() {
		setUp("privacy-purposes");
		assertThat(purposeRepository.findAll()).isEmpty();
		consentService.createPurpose("PHOTOS", "Photo publication", null);

		assertThatThrownBy(() -> consentService.createPurpose("PHOTOS", "Again", null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void latestRecordIsTheCurrentStateAndHistoryIsKept() {
		setUp("privacy-consent");
		ConsentPurpose photos = consentService.createPurpose("PHOTOS", "Photo publication", null);
		ConsentPurpose trips = consentService.createPurpose("TRIPS", "Field trips", null);

		assertThat(consentService.current(person.getId())).extracting(c -> c.state())
				.containsOnly(ConsentState.NOT_RECORDED);

		consentService.record(person, photos, true, ConsentSource.PAPER_FORM, "Signed form 12");
		consentService.record(person, photos, false, ConsentSource.VERBAL, "Withdrew at meeting");

		Map<String, ConsentState> states = new java.util.HashMap<>();
		consentService.current(person.getId()).forEach(c -> states.put(c.purpose().getCode(), c.state()));
		assertThat(states).containsEntry("PHOTOS", ConsentState.WITHDRAWN).containsEntry("TRIPS", ConsentState.NOT_RECORDED);
		assertThat(consentService.history(person.getId())).hasSize(2);
		assertThat(consentService.history(person.getId()).get(0).isGranted()).isFalse();
		assertThat(trips.getStatus()).isEqualTo(ConsentPurposeStatus.ACTIVE);
	}

	@Test
	void inactivePurposesTakeNoNewRecordsButKeepHistory() {
		setUp("privacy-inactive");
		ConsentPurpose photos = consentService.createPurpose("PHOTOS", "Photo publication", null);
		consentService.record(person, photos, true, ConsentSource.ADMIN_ENTRY, null);
		consentService.changePurposeStatus(photos, ConsentPurposeStatus.INACTIVE);

		assertThatThrownBy(() -> consentService.record(person, photos, false, ConsentSource.ADMIN_ENTRY, null))
				.isInstanceOf(IllegalStateException.class);
		// still visible for the person who has a record, hidden for one who doesn't
		assertThat(consentService.current(person.getId())).hasSize(1);
		Person other = personRepository.save(new Person("Bilal", "Khan"));
		assertThat(consentService.current(other.getId())).isEmpty();
	}

	@Test
	void consentDecisionsAreAudited() {
		setUp("privacy-audit-consent");
		ConsentPurpose photos = consentService.createPurpose("PHOTOS", "Photo publication", null);
		consentService.record(person, photos, true, ConsentSource.ADMIN_ENTRY, null);
		consentService.record(person, photos, false, ConsentSource.ADMIN_ENTRY, null);

		assertThat(auditLogRepository.findAll()).filteredOn(l -> "CONSENT_RECORD".equals(l.getEntityType()) && TenantContext.getOrganisationId().equals(l.getOrganisationId()))
				.extracting(l -> l.getAction()).containsExactly("GRANTED", "WITHDRAWN");
	}

	@Test
	void requestsDefaultToAThirtyDayDeadlineAndRejectAnEarlierDueDate() {
		setUp("privacy-due");
		DataSubjectRequest request = requestService.open(person, DataSubjectRequestType.ACCESS, null, LocalDate.of(2026, 1, 1), null);

		assertThat(request.getDueDate()).isEqualTo(LocalDate.of(2026, 1, 31));
		assertThat(request.getStatus()).isEqualTo(DataSubjectRequestStatus.RECEIVED);
		assertThatThrownBy(() -> requestService.open(person, DataSubjectRequestType.ACCESS, null, LocalDate.of(2026, 1, 10),
				LocalDate.of(2026, 1, 5))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void completionRequiresVerifiedIdentityAndANote() {
		setUp("privacy-complete");
		DataSubjectRequest request = requestService.open(person, DataSubjectRequestType.CORRECTION, null, null, null);

		assertThatThrownBy(() -> requestService.complete(request, "Fixed")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> requestService.verifyIdentity(request, " ")).isInstanceOf(IllegalArgumentException.class);

		requestService.verifyIdentity(request, "Photo ID sighted");
		assertThatThrownBy(() -> requestService.complete(request, " ")).isInstanceOf(IllegalArgumentException.class);
		requestService.complete(request, "Address corrected");

		DataSubjectRequest reloaded = requestRepository.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(DataSubjectRequestStatus.COMPLETED);
		assertThat(reloaded.getIdentityVerifiedBy()).isEqualTo(5L);
		assertThat(reloaded.getResolvedBy()).isEqualTo(5L);
		assertThat(reloaded.getResolvedAt()).isNotNull();
		assertThatThrownBy(() -> requestService.reject(reloaded, "Too late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anUnverifiedRequestCanBeRejectedButNotVerifiedTwice() {
		setUp("privacy-reject");
		DataSubjectRequest request = requestService.open(person, DataSubjectRequestType.DELETION, "Please delete", null, null);
		requestService.verifyIdentity(request, "Called back on registered number");
		assertThatThrownBy(() -> requestService.verifyIdentity(request, "again")).isInstanceOf(IllegalStateException.class);

		requestService.reject(request, "Records must be retained for fee audit");

		assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(DataSubjectRequestStatus.REJECTED);
		assertThat(requestRepository.findByStatusOrderByDueDate(DataSubjectRequestStatus.REJECTED)).hasSize(1);
	}

	@Test
	void dataExportIsRefusedUntilIdentityIsVerifiedAndOnlyForAccessOrExport() {
		setUp("privacy-export");
		ConsentPurpose photos = consentService.createPurpose("PHOTOS", "Photo publication", null);
		consentService.record(person, photos, true, ConsentSource.ADMIN_ENTRY, null);
		DataSubjectRequest access = requestService.open(person, DataSubjectRequestType.ACCESS, null, null, null);
		DataSubjectRequest deletion = requestService.open(person, DataSubjectRequestType.DELETION, null, null, null);

		assertThatThrownBy(() -> requestService.exportSubjectData(access)).isInstanceOf(IllegalStateException.class);
		requestService.verifyIdentity(access, "Photo ID");
		requestService.verifyIdentity(deletion, "Photo ID");
		assertThatThrownBy(() -> requestService.exportSubjectData(deletion)).isInstanceOf(IllegalStateException.class);

		Map<String, Object> export = requestService.exportSubjectData(access);

		assertThat(((Map<?, ?>) export.get("person")).get("firstName")).isEqualTo("Anaya");
		assertThat((List<?>) export.get("consents")).hasSize(1);
		assertThat(auditLogRepository.findAll()).anyMatch(l -> "DATA_EXPORTED".equals(l.getAction()));
	}

	@Test
	void everyRequestTransitionIsAuditedWithBeforeAndAfterStatus() {
		setUp("privacy-audit-dsr");
		DataSubjectRequest request = requestService.open(person, DataSubjectRequestType.ACCESS, null, null, null);
		requestService.verifyIdentity(request, "Photo ID");
		requestService.complete(request, "Sent");

		assertThat(auditLogRepository.findAll()).filteredOn(l -> "DATA_SUBJECT_REQUEST".equals(l.getEntityType()) && TenantContext.getOrganisationId().equals(l.getOrganisationId()))
				.extracting(l -> l.getAction()).containsExactly("RECEIVED", "IDENTITY_VERIFIED", "COMPLETED");
	}
}
