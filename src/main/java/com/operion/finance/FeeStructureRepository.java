package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, Long> {

	List<FeeStructure> findByAcademicYearIdAndSchoolClassId(Long academicYearId, Long schoolClassId);
}
