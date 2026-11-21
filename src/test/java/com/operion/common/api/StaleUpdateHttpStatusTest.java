package com.operion.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.operion.common.StaleUpdateException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A lost update - whether Hibernate's version check or a service's explicit comparison caught it - is a 409, never a 500. */
class StaleUpdateHttpStatusTest {

	@RestController
	static class StaleStub {
		@GetMapping("/stub/optimistic-lock")
		String optimistic() {
			throw new ObjectOptimisticLockingFailureException("com.operion.student.Student", 7L);
		}

		@GetMapping("/stub/stale-update")
		String stale() {
			throw new StaleUpdateException("This record was changed by someone else — reload and try again");
		}
	}

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StaleStub()).setControllerAdvice(new ApiExceptionHandler()).build();

	@Test
	void anOptimisticLockFailureIsAConflictWithAReloadMessageAndNoEntityName() throws Exception {
		mockMvc.perform(get("/stub/optimistic-lock"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("This record was changed by someone else — reload and try again"));
	}

	@Test
	void aStaleUpdateExceptionIsAConflictCarryingItsMessage() throws Exception {
		mockMvc.perform(get("/stub/stale-update"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("This record was changed by someone else — reload and try again"));
	}
}
