package com.operion.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

	List<ItemCategory> findByStatus(ItemCategoryStatus status);

	Optional<ItemCategory> findByNameIgnoreCase(String name);
}
