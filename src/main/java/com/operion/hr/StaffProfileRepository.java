package com.operion.hr;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

	/** Row-locked read (SELECT ... FOR UPDATE) that serialises everything which must not interleave for one staff member:
	 * leave approval, balance allocation, exit, transfer and status changes. A lock needs a transaction: this joins the
	 * caller's (HrService holds it to commit) and only opens its own when called bare. */
	@Transactional
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM StaffProfile s WHERE s.id = :id")
	Optional<StaffProfile> findWithLockById(@Param("id") Long id);

	List<StaffProfile> findByStatus(StaffProfileStatus status);

	List<StaffProfile> findByStatusIn(List<StaffProfileStatus> statuses);

	List<StaffProfile> findByCampusId(Long campusId);

	List<StaffProfile> findByCampusIdAndStatusIn(Long campusId, List<StaffProfileStatus> statuses);

	Optional<StaffProfile> findByPersonId(Long personId);

	boolean existsByPersonId(Long personId);

	/** Every person with a staff profile (any status), in one query - the Members list's "has an HR profile" flag. */
	@Query("SELECT s.person.id FROM StaffProfile s")
	List<Long> findAllPersonIds();

	Optional<StaffProfile> findByEmployeeCode(String employeeCode);

	long countByStatus(StaffProfileStatus status);
}
