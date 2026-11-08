package com.operion.onboarding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SetupStepCompletionRepository extends JpaRepository<SetupStepCompletion, Long> {

	Optional<SetupStepCompletion> findByStepKey(String stepKey);

	List<SetupStepCompletion> findAllByOrderByIdAsc();
}
