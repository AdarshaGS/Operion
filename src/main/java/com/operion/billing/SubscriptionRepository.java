package com.operion.billing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

	List<Subscription> findByOrganisationIdOrderByStartDateDesc(Long organisationId);

	Optional<Subscription> findByOrganisationIdAndStatus(Long organisationId, SubscriptionStatus status);
}
