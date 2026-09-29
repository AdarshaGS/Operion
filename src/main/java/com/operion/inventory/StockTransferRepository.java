package com.operion.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

	@Query("SELECT t FROM StockTransfer t WHERE t.item.id = :itemId AND (t.fromCampus.id = :campusId OR t.toCampus.id = :campusId)")
	List<StockTransfer> findByItemIdAndEitherCampusId(@Param("itemId") Long itemId, @Param("campusId") Long campusId);
}
