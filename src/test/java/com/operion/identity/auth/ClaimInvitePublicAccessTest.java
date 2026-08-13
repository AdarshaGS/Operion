package com.operion.identity.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression test for a real bug caught only by an end-to-end smoke test, not the unit
 * suite: JwtAuthenticationInterceptor.isPublic() has its own hardcoded allowlist,
 * completely separate from PortalInviteService's own logic - forgetting to add
 * claim-invite to it made every request die as a silent, bodyless 401 before ever
 * reaching AuthController, invisible to PortalInviteLifecycleTest since that test
 * calls the service directly and never goes through the interceptor layer (this
 * codebase has no other MockMvc precedent besides OpenApiSpecTest, see its own note on
 * why - PermissionInterceptorTest tests the interceptor's logic at the unit level
 * instead, but nothing had exercised the allowlist itself through the real filter chain
 * until now).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimInvitePublicAccessTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void claimInviteIsReachableWithoutABearerToken() throws Exception {
		// A bogus token still proves the point: reaching PortalInviteService.claim() and
		// getting its real 401 ("Invalid or expired invite") is the opposite of being
		// rejected by the interceptor before the controller is ever invoked.
		mockMvc.perform(post("/api/v1/auth/claim-invite")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"organisationSlug\":\"no-such-org\",\"token\":\"bogus\",\"password\":\"whatever123\"}"))
				.andExpect(status().isUnauthorized());
	}
}
