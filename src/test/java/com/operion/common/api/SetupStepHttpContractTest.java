package com.operion.common.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import com.operion.onboarding.SetupStep;
import com.operion.onboarding.SetupStepCompletion;
import com.operion.onboarding.SetupStepCompletionRepository;
import com.operion.onboarding.SetupStepService;
import com.operion.onboarding.api.SetupStepController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP contract of the wizard-progress endpoints: the real controller, service and exception handler around a
 * stubbed repository (no Spring context). Permission enforcement (@RequirePermission) is not exercised here. */
class SetupStepHttpContractTest {

	private final SetupStepCompletionRepository repository = mock(SetupStepCompletionRepository.class);

	private MockMvc mockMvc() {
		return MockMvcBuilders.standaloneSetup(new SetupStepController(new SetupStepService(repository)))
				.setControllerAdvice(new ApiExceptionHandler()).build();
	}

	@Test
	void listsTheCompletedStepKeys() throws Exception {
		when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(new SetupStepCompletion(SetupStep.PROFILE)));

		mockMvc().perform(get("/api/v1/setup/structure/steps"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedSteps[0]").value("profile"));
	}

	@Test
	void anUnknownStepIsABadRequest() throws Exception {
		mockMvc().perform(post("/api/v1/setup/structure/steps/review/complete"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Unknown setup step 'review'"));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void completingAKnownStepSavesItOnceAndReturnsTheList() throws Exception {
		when(repository.findByStepKey("campuses")).thenReturn(Optional.empty());
		when(repository.findAllByOrderByIdAsc()).thenReturn(List.of(new SetupStepCompletion(SetupStep.CAMPUSES)));

		mockMvc().perform(post("/api/v1/setup/structure/steps/campuses/complete"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completedSteps[0]").value("campuses"));
		verify(repository).saveAndFlush(any(SetupStepCompletion.class));
	}
}
