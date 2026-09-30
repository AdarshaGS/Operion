package com.operion.privacy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentPurposeRepository extends JpaRepository<ConsentPurpose, Long> {

	Optional<ConsentPurpose> findByCode(String code);

	List<ConsentPurpose> findByStatus(ConsentPurposeStatus status);
}
