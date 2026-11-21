package com.operion.organisation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.operion.platform.PlatformAdmin;
import com.operion.platform.PlatformAdminRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Organisation creation is a platform-admin-only action. Goes through the real filter/interceptor chain (like
 * ClaimInvitePublicAccessTest) because the guarantee lives in the two auth interceptors, not in the controllers:
 * no anonymous route creates an organisation, an org-plane token cannot reach the platform route, and a
 * platform-plane token can.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationCreationAccessTest {

	private static final String ADMIN_PASSWORD = "platform-admin-pass-1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PlatformAdminRepository platformAdminRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private static String body(String slug) {
		return "{\"name\":\"School " + slug + "\",\"legalName\":\"School " + slug + " Trust\",\"slug\":\"" + slug
				+ "\",\"adminEmail\":\"owner@" + slug + ".test\",\"adminPassword\":\"LongEnough1!\","
				+ "\"adminFirstName\":\"Owner\",\"adminLastName\":\"Test\"}";
	}

	private String platformToken() throws Exception {
		String email = "admin-" + UUID.randomUUID() + "@operion.platform";
		platformAdminRepository.save(new PlatformAdmin("Test Admin", email, passwordEncoder.encode(ADMIN_PASSWORD)));
		String response = mockMvc.perform(post("/api/v1/platform/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.token");
	}

	private String createViaPlatform(String platformToken, String slug) throws Exception {
		String response = mockMvc.perform(post("/api/v1/platform/organisations")
						.header("Authorization", "Bearer " + platformToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(slug)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(slug))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.slug");
	}

	private String orgAdminToken(String slug) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"organisationSlug\":\"" + slug + "\",\"email\":\"owner@" + slug + ".test\",\"password\":\"LongEnough1!\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.token");
	}

	@Test
	void anonymousPostToTheOldPublicEndpointDoesNotCreateAnOrganisation() throws Exception {
		String slug = "anon-" + UUID.randomUUID().toString().substring(0, 8);

		int status = mockMvc.perform(post("/api/v1/organisations").contentType(MediaType.APPLICATION_JSON).content(body(slug)))
				.andReturn().getResponse().getStatus();

		// No POST mapping is left on this path; the interceptor still answers anonymous callers with 401.
		assertThat(status).isEqualTo(401);
		// And nothing was created: that slug cannot log in.
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"organisationSlug\":\"" + slug + "\",\"email\":\"owner@" + slug + ".test\",\"password\":\"LongEnough1!\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void aPlatformTokenCanCreateAnOrganisationAndItsAdminCanLogIn() throws Exception {
		String slug = "plat-" + UUID.randomUUID().toString().substring(0, 8);

		createViaPlatform(platformToken(), slug);

		assertThat(orgAdminToken(slug)).isNotBlank();
	}

	@Test
	void platformCreateWithoutAnyTokenIsRejected() throws Exception {
		String slug = "none-" + UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(post("/api/v1/platform/organisations").contentType(MediaType.APPLICATION_JSON).content(body(slug)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anOrgPlaneTokenIsRejectedOnPlatformCreate() throws Exception {
		String existing = createViaPlatform(platformToken(), "org-" + UUID.randomUUID().toString().substring(0, 8));
		String orgToken = orgAdminToken(existing);
		String newSlug = "evil-" + UUID.randomUUID().toString().substring(0, 8);

		// A perfectly valid org-plane JWT (an organisation's own admin) must not open the platform plane.
		mockMvc.perform(post("/api/v1/platform/organisations")
						.header("Authorization", "Bearer " + orgToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(newSlug)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"organisationSlug\":\"" + newSlug + "\",\"email\":\"owner@" + newSlug + ".test\",\"password\":\"LongEnough1!\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anOrgPlaneTokenCannotCreateAnOrganisationOnTheOldEndpointEither() throws Exception {
		String existing = createViaPlatform(platformToken(), "org2-" + UUID.randomUUID().toString().substring(0, 8));
		String orgToken = orgAdminToken(existing);
		String newSlug = "evil2-" + UUID.randomUUID().toString().substring(0, 8);

		int status = mockMvc.perform(post("/api/v1/organisations")
						.header("Authorization", "Bearer " + orgToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(newSlug)))
				.andReturn().getResponse().getStatus();

		assertThat(status).isIn(401, 403, 405);
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"organisationSlug\":\"" + newSlug + "\",\"email\":\"owner@" + newSlug + ".test\",\"password\":\"LongEnough1!\"}"))
				.andExpect(status().isUnauthorized());
	}
}
