package com.operion.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

	List<StockAdjustment> findByItemIdAndCampusId(Long itemId, Long campusId);

	@Query("SELECT COALESCE(SUM(a.quantityDelta), 0) FROM StockAdjustment a WHERE a.item.id = :itemId AND a.campus.id = :campusId")
	int sumQuantityDeltaByItemIdAndCampusId(@Param("itemId") Long itemId, @Param("campusId") Long campusId);
}
