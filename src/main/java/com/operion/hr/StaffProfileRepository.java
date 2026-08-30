package com.operion.hr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

	List<StaffProfile> findByStatus(StaffProfileStatus status);

	List<StaffProfile> findByStatusIn(List<StaffProfileStatus> statuses);

	List<StaffProfile> findByCampusId(Long campusId);

	Optional<StaffProfile> findByPersonId(Long personId);

	long countByStatus(StaffProfileStatus status);
}
