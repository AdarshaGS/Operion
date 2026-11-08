package com.operion.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.InvalidRequestException;
import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Structure Setup wizard progress is stored per school: idempotent, validated, and never visible across tenants.
 * Runs the real repository (H2, tenant filter active). */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, SetupStepService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SetupStepServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private SetupStepService service;

	private Long newTenant() {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", "setup-steps-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		return organisation.getId();
	}

	@BeforeEach
	void tenant() {
		newTenant();
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	@Test
	void nothingIsCompletedForAFreshSchool() {
		assertThat(service.completedStepKeys()).isEmpty();
	}

	@Test
	void completingAStepRecordsIt() {
		assertThat(service.complete("campuses")).containsExactly("campuses");
		assertThat(service.completedStepKeys()).containsExactly("campuses");
	}

	@Test
	void completingTheSameStepTwiceIsIdempotent() {
		service.complete("profile");
		assertThat(service.complete("profile")).containsExactly("profile");
		assertThat(service.completedStepKeys()).containsExactly("profile");
	}

	@Test
	void stepsAccumulateInTheOrderTheyWereCompleted() {
		service.complete("profile");
		service.complete("business");
		assertThat(service.complete("campuses")).containsExactly("profile", "business", "campuses");
	}

	@Test
	void anUnknownStepIsRejectedAndNothingIsRecorded() {
		assertThatThrownBy(() -> service.complete("review")).isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.complete("PROFILE")).isInstanceOf(InvalidRequestException.class);
		assertThat(service.completedStepKeys()).isEmpty();
	}

	@Test
	void progressIsPerSchool() {
		service.complete("profile");
		service.complete("campuses");

		newTenant();
		assertThat(service.completedStepKeys()).isEmpty();
		// the other school completing the same step does not collide with the first school's row
		assertThat(service.complete("profile")).containsExactly("profile");
	}
}
