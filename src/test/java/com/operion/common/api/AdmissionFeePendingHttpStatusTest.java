package com.operion.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.operion.student.AdmissionFeePendingException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP contract of the admission-fee gate: an AdmissionFeePendingException reaches the client as a 409 with
 * the message, the stable code ADMISSION_FEE_PENDING and the offending enrollment ids. Standalone MockMvc around
 * the real ApiExceptionHandler - no Spring context, no database.
 */
class AdmissionFeePendingHttpStatusTest {

	@RestController
	static class PendingStub {
		@GetMapping("/stub/admission-fee-pending")
		String write() {
			throw new AdmissionFeePendingException("enter marks", List.of(11L, 12L));
		}
	}

	@Test
	void aPendingAdmissionFeeIsAConflictWithACodeAndTheEnrollmentIds() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PendingStub()).setControllerAdvice(new ApiExceptionHandler()).build();

		mockMvc.perform(get("/stub/admission-fee-pending"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ADMISSION_FEE_PENDING"))
				.andExpect(jsonPath("$.enrollmentIds[0]").value(11))
				.andExpect(jsonPath("$.enrollmentIds[1]").value(12))
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("enter marks")));
	}
}
