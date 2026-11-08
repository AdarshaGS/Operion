package com.operion.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** application-prod.properties must switch the public OpenAPI spec and Swagger UI off (it is imported on top of the test config). */
@SpringBootTest(properties = "spring.config.import=classpath:application-prod.properties")
@AutoConfigureMockMvc
class OpenApiDisabledInProdTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsAreNotServed() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUiIsNotServed() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is4xxClientError());
	}
}
