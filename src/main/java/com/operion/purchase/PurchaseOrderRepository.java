package com.operion.purchase;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	long countByStatusNotIn(Collection<PurchaseOrderStatus> statuses);
}
