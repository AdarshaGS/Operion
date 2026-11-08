package com.operion.onboarding;

import java.util.List;

import com.operion.common.InvalidRequestException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Records which Structure Setup wizard steps the school has completed. Tenant scoping comes from the entity's
 * {@code @TenantId}, so every call is implicitly for the caller's own organisation. Deliberately not
 * {@code @Transactional}: if two requests race to insert the same step, the unique-constraint failure is
 * swallowed (the step is complete either way) instead of poisoning a surrounding transaction. */
@Service
public class SetupStepService {

	private final SetupStepCompletionRepository repository;

	public SetupStepService(SetupStepCompletionRepository repository) {
		this.repository = repository;
	}

	public List<String> completedStepKeys() {
		return repository.findAllByOrderByIdAsc().stream().map(SetupStepCompletion::getStepKey).toList();
	}

	/** Idempotent: completing an already-completed step changes nothing. Returns the completed keys afterwards. */
	public List<String> complete(String stepKey) {
		SetupStep step = SetupStep.fromKey(stepKey).orElseThrow(() -> new InvalidRequestException("Unknown setup step '" + stepKey + "'"));
		if (repository.findByStepKey(step.key()).isEmpty()) {
			try {
				repository.saveAndFlush(new SetupStepCompletion(step));
			} catch (DataIntegrityViolationException alreadyCompletedByAnotherRequest) {
				// unique (organisation_id, step_key) - nothing to do
			}
		}
		return completedStepKeys();
	}
}
