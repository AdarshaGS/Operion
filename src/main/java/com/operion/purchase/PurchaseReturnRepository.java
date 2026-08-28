package com.operion.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

	List<PurchaseReturn> findByPurchaseOrderLineId(Long purchaseOrderLineId);

	@Query("SELECT COALESCE(SUM(r.quantity), 0) FROM PurchaseReturn r WHERE r.purchaseOrderLine.id = :purchaseOrderLineId")
	int sumQuantityByPurchaseOrderLineId(@Param("purchaseOrderLineId") Long purchaseOrderLineId);
}
