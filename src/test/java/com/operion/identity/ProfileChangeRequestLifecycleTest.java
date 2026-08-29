package com.operion.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves ProfileChangeService: at least one field required, approve() applies only the
 * non-null requested fields onto Person (leaving the others untouched) and is one-way
 * off PENDING, reject() leaves Person untouched entirely - same shape as
 * LeaveBalanceApprovalTest for LeaveRequest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class, ProfileChangeService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfileChangeRequestLifecycleTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private ProfileChangeService profileChangeService;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Person setUpFixture(String slug) {
		Organisation organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", slug));
		TenantContext.set(organisation.getId(), null);

		Person person = new Person("Priya", "Shah");
		person.setPhone("9990001111");
		person.setEmail("priya.old@example.com");
		return personRepository.save(person);
	}

	@Test
	void submittingWithNoFieldsIsRejected() {
		Person person = setUpFixture("profile-change-empty");
		assertThatThrownBy(() -> profileChangeService.submit(person, null, null, null, 1L)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void approvingAppliesOnlyTheRequestedFieldsOntoPerson() {
		Person person = setUpFixture("profile-change-approve");
		ProfileChangeRequest request = profileChangeService.submit(person, "8880002222", null, null, 5L);

		profileChangeService.approve(request, 99L);

		Person reloaded = personRepository.findById(person.getId()).orElseThrow();
		assertThat(reloaded.getPhone()).isEqualTo("8880002222");
		assertThat(reloaded.getEmail()).isEqualTo("priya.old@example.com");
		assertThat(request.getStatus()).isEqualTo(ProfileChangeRequestStatus.APPROVED);
		assertThat(request.getReviewedBy()).isEqualTo(99L);
	}

	@Test
	void rejectingLeavesPersonUntouched() {
		Person person = setUpFixture("profile-change-reject");
		ProfileChangeRequest request = profileChangeService.submit(person, "8880002222", "new@example.com", null, 5L);

		profileChangeService.reject(request, 42L);

		Person reloaded = personRepository.findById(person.getId()).orElseThrow();
		assertThat(reloaded.getPhone()).isEqualTo("9990001111");
		assertThat(reloaded.getEmail()).isEqualTo("priya.old@example.com");
		assertThat(request.getStatus()).isEqualTo(ProfileChangeRequestStatus.REJECTED);
	}

	@Test
	void decidingAnAlreadyDecidedRequestIsRejected() {
		Person person = setUpFixture("profile-change-twice");
		ProfileChangeRequest request = profileChangeService.submit(person, "8880002222", null, null, 5L);
		profileChangeService.approve(request, 99L);

		assertThatThrownBy(() -> profileChangeService.reject(request, 99L)).isInstanceOf(IllegalStateException.class);
	}
}
