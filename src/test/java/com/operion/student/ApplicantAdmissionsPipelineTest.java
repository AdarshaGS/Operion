package com.operion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;

import com.operion.common.InvalidRequestException;
import com.operion.academic.GradeLevel;
import com.operion.academic.GradeLevelRepository;
import com.operion.academic.GradeLevelStatus;
import com.operion.audit.AuditLogRepository;
import com.operion.audit.AuditLogService;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.TooManyRequestsException;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.student.api.PublicAdmissionInquiryController;
import com.operion.student.api.SubmitAdmissionInquiryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** #217-#219: applicant review pipeline transitions, public inquiry submission, status counts. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicantAdmissionsPipelineTest {

	@Autowired
	private OrganisationRepository organisationRepository;
	@Autowired
	private PersonRepository personRepository;
	@Autowired
	private ApplicantRepository applicantRepository;
	@Autowired
	private ApplicantDocumentRepository applicantDocumentRepository;
	@Autowired
	private GradeLevelRepository gradeLevelRepository;
	@Autowired
	private AuditLogRepository auditLogRepository;

	private ApplicantService applicantService;
	private ApplicantInquiryService inquiryService;
	private PublicAdmissionInquiryController controller;

	@BeforeEach
	void setUp() {
		applicantService = new ApplicantService(applicantRepository, null, applicantDocumentRepository,
				new AuditLogService(auditLogRepository, new ObjectMapper()));
		inquiryService = new ApplicantInquiryService(personRepository, applicantRepository, gradeLevelRepository);
		controller = new PublicAdmissionInquiryController(organisationRepository, gradeLevelRepository, inquiryService,
				new InquiryRateLimiter());
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Organisation org(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("School", "School Trust", slug));
		TenantContext.set(organisation.getId(), null);
		return organisation;
	}

	private Applicant newApplicant() {
		return inquiryService.submit("Asha", "Rao", LocalDate.of(2018, 4, 2), "F", null, "Mr Rao", "9999999999", null, null);
	}

	private ApplicantStatus statusOf(Applicant applicant) {
		return applicantRepository.findById(applicant.getId()).orElseThrow().getStatus();
	}

	@Test
	void legalTransitionsPersist() {
		org("pipe-legal");
		Applicant a = newApplicant();
		applicantService.startReview(a);
		assertThat(statusOf(a)).isEqualTo(ApplicantStatus.UNDER_REVIEW);
		applicantService.reject(a, "No seats");
		Applicant reloaded = applicantRepository.findById(a.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ApplicantStatus.REJECTED);
		assertThat(reloaded.getRejectionReason()).isEqualTo("No seats");
		assertThat(reloaded.getReviewedAt()).isNotNull();

		Applicant direct = newApplicant();
		applicantService.reject(direct, "Out of catchment");
		assertThat(statusOf(direct)).isEqualTo(ApplicantStatus.REJECTED);
	}

	@Test
	void illegalTransitionsAreRejected() {
		org("pipe-illegal");
		Applicant reviewing = newApplicant();
		applicantService.startReview(reviewing);
		assertThatThrownBy(() -> applicantService.startReview(reviewing)).isInstanceOf(IllegalStateException.class);
		assertThat(statusOf(reviewing)).isEqualTo(ApplicantStatus.UNDER_REVIEW);

		Applicant rejected = newApplicant();
		assertThatThrownBy(() -> applicantService.reject(rejected, " ")).isInstanceOf(IllegalStateException.class);
		assertThat(statusOf(rejected)).isEqualTo(ApplicantStatus.INQUIRY);
		applicantService.reject(rejected, "reason");
		assertThatThrownBy(() -> applicantService.startReview(rejected)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> applicantService.reject(rejected, "again")).isInstanceOf(IllegalStateException.class);
		assertThat(statusOf(rejected)).isEqualTo(ApplicantStatus.REJECTED);
	}

	@Test
	void publicSubmitCreatesTenantScopedApplicantAndHoneypotStoresNothing() {
		Organisation a = org("pipe-public-a");
		GradeLevel grade = gradeLevelRepository.save(new GradeLevel("Grade 1", 1, null));
		SubmitAdmissionInquiryRequest request = new SubmitAdmissionInquiryRequest("Asha", "Rao", LocalDate.of(2018, 4, 2), "F",
				grade.getId(), "Mr Rao", "9999999999", "rao@example.com", "hi", null);
		TenantContext.clear();

		Map<String, Long> created = controller.submit("pipe-public-a", request, new MockHttpServletRequest());
		assertThat(created.get("id")).isNotNull();

		SubmitAdmissionInquiryRequest bot = new SubmitAdmissionInquiryRequest("Bot", "Bot", LocalDate.of(2018, 4, 2), null, null,
				"B", "1", null, null, "http://spam");
		controller.submit("pipe-public-a", bot, new MockHttpServletRequest());

		TenantContext.set(a.getId(), null);
		assertThat(applicantRepository.count()).isEqualTo(1);
		Applicant stored = applicantRepository.findById(created.get("id")).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(ApplicantStatus.INQUIRY);
		assertThat(stored.getOrganisationId()).isEqualTo(a.getId());
		assertThat(stored.getDesiredGrade().getName()).isEqualTo("Grade 1");
		assertThat(stored.getGuardianName()).isEqualTo("Mr Rao");

		Organisation b = org("pipe-public-b");
		assertThat(applicantRepository.count()).isZero();
		assertThat(b.getId()).isNotEqualTo(a.getId());
	}

	@Test
	void unknownSlugIsNotFoundAndFormOptionsListOnlyActiveGrades() {
		assertThatThrownBy(() -> controller.formOptions("nope-slug")).isInstanceOf(IllegalArgumentException.class);

		org("pipe-options");
		gradeLevelRepository.save(new GradeLevel("Grade 1", 1, null));
		GradeLevel inactive = gradeLevelRepository.save(new GradeLevel("Grade 2", 2, null));
		inactive.changeStatus(GradeLevelStatus.INACTIVE);
		gradeLevelRepository.save(inactive);
		TenantContext.clear();

		var options = controller.formOptions("pipe-options");
		assertThat(options.organisationName()).isEqualTo("School");
		assertThat(options.grades()).extracting(g -> g.name()).containsExactly("Grade 1");
	}

	@Test
	void rateLimitKicksInAfterTenSubmissions() {
		org("pipe-rate");
		TenantContext.clear();
		SubmitAdmissionInquiryRequest request = new SubmitAdmissionInquiryRequest("A", "B", LocalDate.of(2018, 4, 2), null, null,
				"G", "1", null, null, null);
		for (int i = 0; i < 10; i++) {
			controller.submit("pipe-rate", request, new MockHttpServletRequest());
		}
		assertThatThrownBy(() -> controller.submit("pipe-rate", request, new MockHttpServletRequest()))
				.isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void statusCountsAndDocumentChecklist() {
		org("pipe-counts");
		Applicant one = newApplicant();
		Applicant two = newApplicant();
		newApplicant();
		applicantService.startReview(one);
		applicantService.reject(two, "x");

		assertThat(applicantService.statusCounts()).containsEntry("ALL", 3L).containsEntry("INQUIRY", 1L)
				.containsEntry("UNDER_REVIEW", 1L).containsEntry("CONVERTED", 0L).containsEntry("REJECTED", 1L);

		assertThat(applicantService.documents(one.getId())).hasSize(5).containsEntry("PHOTO", false);
		applicantService.updateDocuments(one, Map.of("PHOTO", true, "ADDRESS_PROOF", true));
		applicantService.updateDocuments(one, Map.of("PHOTO", false));
		Map<String, Boolean> docs = applicantService.documents(one.getId());
		assertThat(docs).containsEntry("PHOTO", false).containsEntry("ADDRESS_PROOF", true);
		assertThat(applicantDocumentRepository.findByApplicantId(one.getId())).hasSize(2);
		assertThatThrownBy(() -> applicantService.updateDocuments(one, Map.of("BOGUS", true)))
				.isInstanceOf(InvalidRequestException.class);
	}
}
