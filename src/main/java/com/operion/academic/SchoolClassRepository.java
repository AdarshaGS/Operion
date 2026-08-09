package com.operion.academic;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

	List<SchoolClass> findByAcademicYearId(Long academicYearId);
}
