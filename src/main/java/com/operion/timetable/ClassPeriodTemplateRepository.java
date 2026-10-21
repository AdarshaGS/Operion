package com.operion.timetable;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassPeriodTemplateRepository extends JpaRepository<ClassPeriodTemplate, Long> {

	Optional<ClassPeriodTemplate> findBySchoolClassId(Long schoolClassId);

	List<ClassPeriodTemplate> findBySchoolClassAcademicYearId(Long academicYearId);

	boolean existsByTemplateId(Long templateId);

	long countByTemplateId(Long templateId);
}
