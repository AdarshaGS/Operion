package com.operion.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

	List<Item> findByStatus(ItemStatus status);

	List<Item> findByCategoryId(Long categoryId);

	long countByStatus(ItemStatus status);

	Optional<Item> findByCodeIgnoreCase(String code);
}
