package com.operion.purchase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

	List<PurchaseOrderLine> findByPurchaseOrderId(Long purchaseOrderId);
}
