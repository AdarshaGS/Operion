package com.operion.authorization;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganisationMembershipRepository extends JpaRepository<OrganisationMembership, Long> {

	List<OrganisationMembership> findByUserId(Long userId);

	List<OrganisationMembership> findByStatus(MembershipStatus status);

	List<OrganisationMembership> findByCampusIdAndStatus(Long campusId, MembershipStatus status);

	/**
	 * Union of permission codes across every ACTIVE membership (a user can hold more than
	 * one role at once, e.g. parent + teacher) with an ACTIVE role, scoped to the current
	 * tenant automatically via {@code @TenantId} like every other query in this codebase -
	 * no explicit organisation_id predicate needed. Backs {@link PermissionInterceptor},
	 * so this runs on every permission-gated request in the app - @Param is explicit here
	 * (unlike other custom @Query methods in this codebase, which lean on the
	 * -parameters compiler flag instead) precisely because this one is that load-bearing:
	 * a build lacking that flag (e.g. compiled via an IDE's own JDT rather than Gradle)
	 * would otherwise 500 every permission-gated endpoint in the entire app.
	 */
	@Query("""
			SELECT DISTINCT p.code FROM OrganisationMembership m
			JOIN m.role r
			JOIN r.permissions p
			WHERE m.user.id = :userId AND m.status = com.operion.authorization.MembershipStatus.ACTIVE
			  AND r.status = com.operion.authorization.RoleStatus.ACTIVE
			""")
	Set<String> findActivePermissionCodesForUser(@Param("userId") Long userId);
}
