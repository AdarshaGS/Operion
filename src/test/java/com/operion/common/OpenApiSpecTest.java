package com.operion.common;

import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for springdoc-openapi actually booting under this project's stack
 * (Spring Boot 4.1 / Jackson 3.x's `tools.jackson` rebrand) - the frontend's
 * generated-types.ts (see web/package.json's generate:api-types script) is generated
 * straight from this endpoint, so a silent autoconfiguration break here would only ever
 * surface as a confusing frontend codegen failure otherwise.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsEndpointServesAValidLookingOpenApiSpec() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"openapi\"")))
				.andExpect(content().string(containsString("/api/v1/auth/login")));
	}
}
