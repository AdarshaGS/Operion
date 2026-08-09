package com.operion.parent;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

	Optional<Guardian> findByPersonId(Long personId);
}
