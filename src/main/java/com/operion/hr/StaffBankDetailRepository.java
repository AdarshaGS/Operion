package com.operion.hr;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffBankDetailRepository extends JpaRepository<StaffBankDetail, Long> {

	Optional<StaffBankDetail> findByStaffProfileId(Long staffProfileId);
}
