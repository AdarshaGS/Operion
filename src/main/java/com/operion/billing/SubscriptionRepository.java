package com.operion.billing;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

	List<Subscription> findByOrganisationIdOrderByStartDateDesc(Long organisationId);

	Optional<Subscription> findByOrganisationIdAndStatus(Long organisationId, SubscriptionStatus status);

	List<Subscription> findByStatus(SubscriptionStatus status);
}
