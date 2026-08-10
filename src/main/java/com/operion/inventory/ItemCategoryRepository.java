package com.operion.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

	List<ItemCategory> findByStatus(ItemCategoryStatus status);
}
