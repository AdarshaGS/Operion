package com.operion.sales;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SaleReceiptCounterRepository extends JpaRepository<SaleReceiptCounter, Long> {

	/** Locked so two concurrent sale creations in the same organisation never hand out the same receipt number.
	 * At most one row per organisation - @TenantId already scopes this to the caller's own. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from SaleReceiptCounter c")
	Optional<SaleReceiptCounter> findForUpdate();
}
