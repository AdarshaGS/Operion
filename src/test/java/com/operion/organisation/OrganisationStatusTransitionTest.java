package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** No restricted state machine (issues#21) - any status can move to any other, anytime. */
class OrganisationStatusTransitionTest {

	@Test
	void allowsTrialToActive() {
		Organisation organisation = new Organisation("Test School", "Test School Trust", "test-school");

		organisation.changeStatus(OrganisationStatus.ACTIVE);

		assertThat(organisation.getStatus()).isEqualTo(OrganisationStatus.ACTIVE);
	}

	@Test
	void allowsArchivedBackToTrial() {
		Organisation organisation = new Organisation("Test School", "Test School Trust", "test-school");
		organisation.changeStatus(OrganisationStatus.ARCHIVED);

		organisation.changeStatus(OrganisationStatus.TRIAL);

		assertThat(organisation.getStatus()).isEqualTo(OrganisationStatus.TRIAL);
	}
}
