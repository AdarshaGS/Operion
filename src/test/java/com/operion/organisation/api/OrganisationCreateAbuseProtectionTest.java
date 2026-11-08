package com.operion.organisation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.operion.common.ClientIpResolver;
import com.operion.common.TooManyRequestsException;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationService;
import com.operion.platform.settings.PlatformSetting;
import com.operion.platform.settings.PlatformSettingsService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** SEC-M4: public organisation sign-up is rate-limited per address and its fields are size/format bounded. */
class OrganisationCreateAbuseProtectionTest {

	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private static CreateOrganisationRequest request(String name, String slug, String email, String password) {
		return new CreateOrganisationRequest(name, null, slug, email, password, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null);
	}

	private static Set<String> invalidFields(CreateOrganisationRequest request) {
		return VALIDATOR.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
				.collect(java.util.stream.Collectors.toSet());
	}

	@Test
	void aNormalRequestIsValid() {
		assertThat(invalidFields(request("Sunrise School", "sunrise-school", "admin@sunrise.test", "LongEnough1!"))).isEmpty();
	}

	@Test
	void oversizedOrMalformedFieldsAreRejected() {
		assertThat(invalidFields(request("n".repeat(201), "ok-slug", "a@b.test", "LongEnough1!"))).containsExactly("name");
		assertThat(invalidFields(request("School", "s".repeat(64), "a@b.test", "LongEnough1!"))).contains("slug");
		assertThat(invalidFields(request("School", "Bad Slug!", "a@b.test", "LongEnough1!"))).containsExactly("slug");
		assertThat(invalidFields(request("School", "ok-slug", "not-an-email", "LongEnough1!"))).containsExactly("adminEmail");
		assertThat(invalidFields(request("School", "ok-slug", "a@b.test", "short"))).containsExactly("adminPassword");
		assertThat(invalidFields(request("School", "ok-slug", "a@b.test", "p".repeat(73)))).containsExactly("adminPassword");
	}

	@Test
	void creatingIsRefusedWith429AfterTheThresholdPerAddress() {
		OrganisationService organisationService = mock(OrganisationService.class);
		PlatformSettingsService settingsService = mock(PlatformSettingsService.class);
		PlatformSetting settings = mock(PlatformSetting.class);
		when(settingsService.current()).thenReturn(settings);
		Organisation organisation = new Organisation("School", "School", "school");
		org.springframework.test.util.ReflectionTestUtils.setField(organisation, "createdAt", java.time.Instant.now());
		when(organisationService.provision(any(), any(), any(), any(), any())).thenReturn(organisation);
		OrganisationController controller = new OrganisationController(organisationService, settingsService, new ClientIpResolver(""), 3);
		CreateOrganisationRequest request = request("School", "school", "a@b.test", "LongEnough1!");
		MockHttpServletRequest from = new MockHttpServletRequest();
		from.setRemoteAddr("8.8.8.8");

		for (int i = 0; i < 3; i++) {
			controller.create(request, from);
		}
		assertThatThrownBy(() -> controller.create(request, from)).isInstanceOf(TooManyRequestsException.class);

		MockHttpServletRequest other = new MockHttpServletRequest();
		other.setRemoteAddr("9.9.9.9");
		assertThat(controller.create(request, other)).isNotNull();
	}
}
