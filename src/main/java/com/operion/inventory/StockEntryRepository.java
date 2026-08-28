package com.operion.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockEntryRepository extends JpaRepository<StockEntry, Long> {

	List<StockEntry> findByItemIdAndCampusId(Long itemId, Long campusId);

	@Query("SELECT COALESCE(SUM(e.quantity), 0) FROM StockEntry e WHERE e.item.id = :itemId AND e.campus.id = :campusId")
	int sumQuantityByItemIdAndCampusId(@Param("itemId") Long itemId, @Param("campusId") Long campusId);

	@Query("SELECT e.item.id, COALESCE(SUM(e.quantity), 0) FROM StockEntry e WHERE e.campus.id = :campusId GROUP BY e.item.id")
	List<Object[]> sumQuantityByCampusIdGroupedByItem(@Param("campusId") Long campusId);
}
