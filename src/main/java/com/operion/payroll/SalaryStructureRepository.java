package com.operion.payroll;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {

	List<SalaryStructure> findByStaffProfileIdOrderByEffectiveFromDesc(Long staffProfileId);

	Optional<SalaryStructure> findFirstByStaffProfileIdOrderByEffectiveFromDesc(Long staffProfileId);

	Optional<SalaryStructure> findFirstByStaffProfileIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
			Long staffProfileId, LocalDate date);
}
