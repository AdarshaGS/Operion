package com.operion.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	private static MockHttpServletRequest request(String remoteAddr) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		return request;
	}

	@Test
	void byDefaultOnlyTheSocketAddressIsUsedAndForwardedHeadersAreIgnored() {
		MockHttpServletRequest request = request("10.0.0.7");
		request.addHeader("X-Forwarded-For", "6.6.6.6");
		request.addHeader("X-Real-IP", "7.7.7.7");

		assertThat(new ClientIpResolver("").resolve(request)).isEqualTo("10.0.0.7");
	}

	@Test
	void aConfiguredProxyHeaderIsUsedAndTheRightMostEntryWins() {
		MockHttpServletRequest request = request("10.0.0.7");
		request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.9");

		assertThat(new ClientIpResolver("X-Forwarded-For").resolve(request)).isEqualTo("203.0.113.9");
	}

	@Test
	void aConfiguredHeaderThatIsMissingFallsBackToTheSocketAddress() {
		assertThat(new ClientIpResolver("X-Forwarded-For").resolve(request("10.0.0.7"))).isEqualTo("10.0.0.7");
	}

	@Test
	void anOversizedHeaderValueIsIgnored() {
		MockHttpServletRequest request = request("10.0.0.7");
		request.addHeader("X-Forwarded-For", "x".repeat(500));

		assertThat(new ClientIpResolver("X-Forwarded-For").resolve(request)).isEqualTo("10.0.0.7");
	}

	@Test
	void anUnconfiguredNullPropertyBehavesLikeTheDefault() {
		MockHttpServletRequest request = request("10.0.0.7");
		request.addHeader("X-Forwarded-For", "6.6.6.6");

		assertThat(new ClientIpResolver(null).resolve(request)).isEqualTo("10.0.0.7");
	}
}
