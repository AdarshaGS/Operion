package com.operion.finance;

import com.operion.common.InvalidRequestException;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.operion.authorization.OrganisationMembership;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.finance.api.FeeApproverPolicy;
import com.operion.identity.User;
import org.junit.jupiter.api.Test;

class FeeApproverPolicyTest {

	private final OrganisationMembershipRepository repository = mock(OrganisationMembershipRepository.class);
	private final FeeApproverPolicy policy = new FeeApproverPolicy(repository);

	private OrganisationMembership membershipFor(long userId) {
		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);
		OrganisationMembership membership = mock(OrganisationMembership.class);
		when(membership.getUser()).thenReturn(user);
		return membership;
	}

	@Test
	void acceptsAnEligibleApproverAndNull() {
		OrganisationMembership approver = membershipFor(7L);
		when(repository.findActiveMembersWithPermission("FEE_STRUCTURE_MANAGE")).thenReturn(List.of(approver));

		assertThatCode(() -> policy.requireEligible(7L)).doesNotThrowAnyException();
		assertThatCode(() -> policy.requireEligible(null)).doesNotThrowAnyException();
	}

	@Test
	void rejectsAUserOutsideTheEligibleSet() {
		OrganisationMembership approver = membershipFor(7L);
		when(repository.findActiveMembersWithPermission("FEE_STRUCTURE_MANAGE")).thenReturn(List.of(approver));

		assertThatThrownBy(() -> policy.requireEligible(99L))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("not an eligible fee approver");
	}
}
