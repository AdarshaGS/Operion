package com.operion.purchase;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	/** Row lock for the receive/return paths, so two requests for the same order take turns instead of both reading the old quantities. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from PurchaseOrder o where o.id = :id")
	Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

	long countByStatusNotIn(Collection<PurchaseOrderStatus> statuses);
}
