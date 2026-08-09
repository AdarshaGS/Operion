package com.operion.organisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrganisationStatusTransitionTest {

	@Test
	void allowsTrialToActive() {
		Organisation organisation = new Organisation("Test School", "Test School Trust", "test-school");

		organisation.changeStatus(OrganisationStatus.ACTIVE);

		assertThat(organisation.getStatus()).isEqualTo(OrganisationStatus.ACTIVE);
	}

	@Test
	void rejectsArchivedToTrial() {
		Organisation organisation = new Organisation("Test School", "Test School Trust", "test-school");
		organisation.changeStatus(OrganisationStatus.ARCHIVED);

		assertThatThrownBy(() -> organisation.changeStatus(OrganisationStatus.TRIAL))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void archivedIsTerminal() {
		Organisation organisation = new Organisation("Test School", "Test School Trust", "test-school");
		organisation.changeStatus(OrganisationStatus.ARCHIVED);

		assertThat(organisation.getStatus().canTransitionTo(OrganisationStatus.ACTIVE)).isFalse();
		assertThat(organisation.getStatus().canTransitionTo(OrganisationStatus.SUSPENDED)).isFalse();
	}
}
