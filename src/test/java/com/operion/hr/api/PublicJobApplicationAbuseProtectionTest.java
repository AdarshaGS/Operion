package com.operion.hr.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.operion.common.ClientIpResolver;
import com.operion.common.TooManyRequestsException;
import com.operion.hr.HrService;
import com.operion.organisation.OrganisationRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicJobApplicationAbuseProtectionTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private HrService hrService;
	private OrganisationRepository organisationRepository;
	private JobApplicationController controller;

	@BeforeEach
	void setUp() {
		hrService = mock(HrService.class);
		organisationRepository = mock(OrganisationRepository.class);
		controller = new JobApplicationController(hrService, null, organisationRepository, null, null, null, null, null,
				new ClientIpResolver(""));
	}

	private static MockHttpServletRequest from(String address) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(address);
		return request;
	}

	private static SubmitJobApplicationRequest application(String email) {
		return new SubmitJobApplicationRequest("school", "Asha Teacher", email, "Maths", 3);
	}

	private static Set<String> invalidFields(SubmitJobApplicationRequest request) {
		return VALIDATOR.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
				.collect(Collectors.toSet());
	}

	@Test
	void aNewApplicationAndADuplicateGetTheIdenticalResponse() {
		when(hrService.submitJobApplication(anyString(), anyString(), anyString(), any(), any())).thenReturn(true, false);

		PublicJobApplicationResponse fresh = controller.submit(application("asha@example.com"), from("1.1.1.1"));
		PublicJobApplicationResponse duplicate = controller.submit(application("asha@example.com"), from("1.1.1.1"));

		assertThat(duplicate).isEqualTo(fresh);
	}

	@Test
	void submitIsRefusedWith429AfterTheThresholdPerAddress() {
		when(hrService.submitJobApplication(anyString(), anyString(), anyString(), any(), any())).thenReturn(true);

		for (int i = 0; i < 30; i++) {
			controller.submit(application("a" + i + "@example.com"), from("2.2.2.2"));
		}
		assertThatThrownBy(() -> controller.submit(application("again@example.com"), from("2.2.2.2")))
				.isInstanceOf(TooManyRequestsException.class);
		assertThat(controller.submit(application("other@example.com"), from("3.3.3.3"))).isNotNull();
	}

	@Test
	void theSubjectListIsRateLimitedAndUsesTheGenericUnknownSchoolError() {
		when(organisationRepository.findBySlug(anyString())).thenReturn(Optional.empty());

		for (int i = 0; i < 30; i++) {
			assertThatThrownBy(() -> controller.subjectsForOrganisation("nope", from("4.4.4.4")))
					.isInstanceOf(IllegalArgumentException.class).hasMessage("No such organisation");
		}
		assertThatThrownBy(() -> controller.subjectsForOrganisation("nope", from("4.4.4.4"))).isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void sizeAndFormatLimitsAreEnforced() {
		assertThat(invalidFields(application("asha@example.com"))).isEmpty();
		assertThat(invalidFields(application("not-an-email"))).containsExactly("email");
		assertThat(invalidFields(application("a".repeat(200) + "@example.com"))).containsExactly("email");
		assertThat(invalidFields(new SubmitJobApplicationRequest("school", "n".repeat(201), "a@b.test", null, null)))
				.containsExactly("applicantName");
		assertThat(invalidFields(new SubmitJobApplicationRequest("school", "Asha", "a@b.test", "s".repeat(256), null)))
				.containsExactly("specialization");
		assertThat(invalidFields(new SubmitJobApplicationRequest("school", "Asha", "a@b.test", null, -1)))
				.containsExactly("yearsExperience");
		assertThat(invalidFields(new SubmitJobApplicationRequest("school", "Asha", "a@b.test", null, 500)))
				.containsExactly("yearsExperience");
		assertThat(invalidFields(new SubmitJobApplicationRequest("", "Asha", "a@b.test", null, null)))
				.contains("organisationSlug");
	}
}
