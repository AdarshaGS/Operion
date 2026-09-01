package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeStructureGroupRepository extends JpaRepository<FeeStructureGroup, Long> {

	List<FeeStructureGroup> findByAcademicYearIdAndSchoolClassId(Long academicYearId, Long schoolClassId);
}
