package com.operion.certificate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.operion.certificate.CertificateService;
import com.operion.common.ClientIpResolver;
import com.operion.common.TenantContext;
import com.operion.common.TooManyRequestsException;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicCertificateVerificationRateLimitTest {

	private OrganisationRepository organisationRepository;
	private CertificateService certificateService;
	private PublicCertificateVerificationController controller;

	@BeforeEach
	void setUp() {
		organisationRepository = mock(OrganisationRepository.class);
		certificateService = mock(CertificateService.class);
		controller = new PublicCertificateVerificationController(organisationRepository, certificateService, new ClientIpResolver(""));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private static MockHttpServletRequest from(String address) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(address);
		return request;
	}

	@Test
	void unknownSchoolAndUnknownTokenGetTheSameError() {
		when(organisationRepository.findBySlug("no-such-school")).thenReturn(Optional.empty());
		Organisation organisation = mock(Organisation.class);
		when(organisation.getId()).thenReturn(1L);
		when(organisationRepository.findBySlug("real-school")).thenReturn(Optional.of(organisation));
		when(certificateService.verify(anyString())).thenReturn(Optional.empty());

		Throwable unknownSchool = org.assertj.core.api.Assertions.catchThrowable(() -> controller.verify("no-such-school", "tok", from("1.1.1.1")));
		Throwable unknownToken = org.assertj.core.api.Assertions.catchThrowable(() -> controller.verify("real-school", "tok", from("1.1.1.1")));

		assertThat(unknownSchool).isInstanceOf(IllegalArgumentException.class);
		assertThat(unknownToken).isInstanceOf(IllegalArgumentException.class);
		assertThat(unknownSchool.getMessage()).isEqualTo(unknownToken.getMessage());
	}

	@Test
	void isRateLimitedPerAddressAfterTheThreshold() {
		when(organisationRepository.findBySlug(anyString())).thenReturn(Optional.empty());

		for (int i = 0; i < 60; i++) {
			assertThatThrownBy(() -> controller.verify("s", "t", from("2.2.2.2"))).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> controller.verify("s", "t", from("2.2.2.2"))).isInstanceOf(TooManyRequestsException.class);
		assertThatThrownBy(() -> controller.verify("s", "t", from("9.9.9.9"))).isInstanceOf(IllegalArgumentException.class);
	}
}
