package com.operion.common.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.operion.organisation.AcademicYearRepository;
import com.operion.organisation.AcademicYearService;
import com.operion.organisation.ClosedAcademicYearException;
import com.operion.organisation.api.AcademicYearController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the HTTP contract of the closed-year rule: a ClosedAcademicYearException reaches the client
 * as a 409 carrying its message, and so does the "students still enrolled" refusal to close. Standalone
 * MockMvc around the real ApiExceptionHandler - no Spring context, no database.
 */
class ClosedAcademicYearHttpStatusTest {

	@RestController
	static class ClosedYearStub {
		@GetMapping("/stub/closed-year")
		String write() {
			throw new ClosedAcademicYearException("2025-2026", "adding a section");
		}
	}

	@Test
	void aClosedYearWriteIsAConflictWithTheMessage() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ClosedYearStub()).setControllerAdvice(new ApiExceptionHandler()).build();

		mockMvc.perform(get("/stub/closed-year"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Academic year 2025-2026 is closed; adding a section is not allowed"));
	}

	@Test
	void refusingToCloseAYearWithEnrolledStudentsIsAConflictWithTheMessage() throws Exception {
		AcademicYearService service = mock(AcademicYearService.class);
		when(service.close(7L)).thenThrow(new IllegalStateException(
				"12 students are still enrolled in 2025-2026; promote or exit them before closing"));
		MockMvc mockMvc = MockMvcBuilders
				.standaloneSetup(new AcademicYearController(mock(AcademicYearRepository.class), service))
				.setControllerAdvice(new ApiExceptionHandler()).build();

		mockMvc.perform(post("/api/v1/academic-years/7/close"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("12 students are still enrolled in 2025-2026; promote or exit them before closing"));
	}
}
