package com.operion.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockIssueRepository extends JpaRepository<StockIssue, Long> {

	List<StockIssue> findByItemIdAndCampusId(Long itemId, Long campusId);

	@Query("SELECT COALESCE(SUM(i.quantity), 0) FROM StockIssue i WHERE i.item.id = :itemId AND i.campus.id = :campusId")
	int sumQuantityByItemIdAndCampusId(@Param("itemId") Long itemId, @Param("campusId") Long campusId);
}
