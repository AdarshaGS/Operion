package com.operion.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.identity.UserStatus;
import org.junit.jupiter.api.Test;

class MemberStatusTest {

	@Test
	void pendingUserIsInvitedRegardlessOfMembershipStatus() {
		assertThat(MemberStatus.of(UserStatus.PENDING, MembershipStatus.ACTIVE)).isEqualTo(MemberStatus.INVITED);
		assertThat(MemberStatus.of(UserStatus.PENDING, MembershipStatus.INACTIVE)).isEqualTo(MemberStatus.INVITED);
	}

	@Test
	void activeUserWithActiveMembershipIsActive() {
		assertThat(MemberStatus.of(UserStatus.ACTIVE, MembershipStatus.ACTIVE)).isEqualTo(MemberStatus.ACTIVE);
	}

	@Test
	void revokedMembershipIsInactiveEvenForAnActiveUser() {
		assertThat(MemberStatus.of(UserStatus.ACTIVE, MembershipStatus.INACTIVE)).isEqualTo(MemberStatus.INACTIVE);
	}

	@Test
	void lockedOrDisabledUserIsInactiveEvenWithAnActiveMembership() {
		assertThat(MemberStatus.of(UserStatus.LOCKED, MembershipStatus.ACTIVE)).isEqualTo(MemberStatus.INACTIVE);
		assertThat(MemberStatus.of(UserStatus.DISABLED, MembershipStatus.ACTIVE)).isEqualTo(MemberStatus.INACTIVE);
	}
}
