package com.operion.finance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.operion.common.ClientIpResolver;
import com.operion.common.TooManyRequestsException;
import com.operion.finance.FeePaymentGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** The public payment-link endpoints give one identical answer for an unknown school and an unknown link, and are
 * rate-limited per address and per address+link. */
class PaymentLinkControllerPublicTest {

	private FeePaymentGatewayService gatewayService;
	private PaymentLinkController controller;

	@BeforeEach
	void setUp() {
		gatewayService = mock(FeePaymentGatewayService.class);
		controller = new PaymentLinkController(gatewayService, new ClientIpResolver(""));
	}

	private static MockHttpServletRequest from(String address) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(address);
		return request;
	}

	@Test
	void unknownSchoolAndUnknownLinkGiveTheSameStatusErrorOnStatus() {
		when(gatewayService.getLinkStatus("no-such-school", "tok")).thenThrow(new IllegalArgumentException("No organisation with slug no-such-school"));
		when(gatewayService.getLinkStatus("real-school", "no-such-token")).thenThrow(new IllegalArgumentException("No payment link with this token"));

		Throwable unknownSchool = org.assertj.core.api.Assertions.catchThrowable(() -> controller.status("no-such-school", "tok", from("1.1.1.1")));
		Throwable unknownLink = org.assertj.core.api.Assertions.catchThrowable(() -> controller.status("real-school", "no-such-token", from("1.1.1.1")));

		assertThat(unknownSchool).isInstanceOf(IllegalArgumentException.class).hasMessage(PaymentLinkController.UNKNOWN_LINK_MESSAGE);
		assertThat(unknownLink).isInstanceOf(IllegalArgumentException.class).hasMessage(PaymentLinkController.UNKNOWN_LINK_MESSAGE);
		assertThat(unknownSchool.getClass()).isEqualTo(unknownLink.getClass());
		assertThat(unknownSchool.getMessage()).isEqualTo(unknownLink.getMessage());
	}

	@Test
	void unknownSchoolAndUnknownLinkGiveTheSameErrorOnCheckout() {
		when(gatewayService.initiateCheckout("no-such-school", "tok")).thenThrow(new IllegalArgumentException("No organisation with slug no-such-school"));
		when(gatewayService.initiateCheckout("real-school", "no-such-token")).thenThrow(new IllegalArgumentException("No payment link with this token"));

		assertThatThrownBy(() -> controller.checkout("no-such-school", "tok", from("1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(PaymentLinkController.UNKNOWN_LINK_MESSAGE);
		assertThatThrownBy(() -> controller.checkout("real-school", "no-such-token", from("1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(PaymentLinkController.UNKNOWN_LINK_MESSAGE);
	}

	@Test
	void anOversizedSlugOrTokenGetsTheSameGenericErrorWithoutReachingTheService() {
		assertThatThrownBy(() -> controller.status("s", "t".repeat(500), from("1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(PaymentLinkController.UNKNOWN_LINK_MESSAGE);
		assertThatThrownBy(() -> controller.checkout("s".repeat(500), "t", from("1.1.1.1")))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(PaymentLinkController.UNKNOWN_LINK_MESSAGE);
	}

	@Test
	void statusIsLimitedPerAddressAndLink() {
		when(gatewayService.getLinkStatus(anyString(), anyString())).thenThrow(new IllegalArgumentException("x"));

		for (int i = 0; i < 30; i++) {
			assertThatThrownBy(() -> controller.status("school", "tok", from("2.2.2.2"))).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> controller.status("school", "tok", from("2.2.2.2"))).isInstanceOf(TooManyRequestsException.class);
		// A different address is unaffected.
		assertThatThrownBy(() -> controller.status("school", "tok", from("3.3.3.3"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void checkoutIsLimitedTighterThanStatus() {
		when(gatewayService.initiateCheckout(anyString(), anyString())).thenThrow(new IllegalArgumentException("x"));

		for (int i = 0; i < 10; i++) {
			assertThatThrownBy(() -> controller.checkout("school", "tok", from("4.4.4.4"))).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> controller.checkout("school", "tok", from("4.4.4.4"))).isInstanceOf(TooManyRequestsException.class);
	}

	@Test
	void sprayingManyTokensFromOneAddressIsStoppedByThePerAddressLimit() {
		when(gatewayService.getLinkStatus(anyString(), anyString())).thenThrow(new IllegalArgumentException("x"));

		for (int i = 0; i < 120; i++) {
			int n = i;
			assertThatThrownBy(() -> controller.status("school", "guess-" + n, from("5.5.5.5"))).isInstanceOf(IllegalArgumentException.class);
		}
		assertThatThrownBy(() -> controller.status("school", "guess-next", from("5.5.5.5"))).isInstanceOf(TooManyRequestsException.class);
	}
}
