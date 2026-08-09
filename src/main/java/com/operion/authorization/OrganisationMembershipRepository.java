package com.operion.authorization;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationMembershipRepository extends JpaRepository<OrganisationMembership, Long> {

	List<OrganisationMembership> findByUserId(Long userId);
}
