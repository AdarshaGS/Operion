package com.operion.inventory;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

	List<Item> findByStatus(ItemStatus status);

	List<Item> findByCategoryId(Long categoryId);

	long countByStatus(ItemStatus status);

	Optional<Item> findByCodeIgnoreCase(String code);

	/** Serialises stock movements for one item: balance is computed (read) then a row is added (write),
	 * so two concurrent issues would both pass the check without this lock. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Item i where i.id = :id")
	Optional<Item> findByIdForUpdate(@Param("id") Long id);
}
