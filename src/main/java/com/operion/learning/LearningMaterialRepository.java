package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, Long> {

	List<LearningMaterial> findBySchoolClassIdAndStatusOrderByIdDesc(Long schoolClassId, MaterialStatus status);

	List<LearningMaterial> findBySchoolClassIdInOrderByIdDesc(Collection<Long> schoolClassIds);

	List<LearningMaterial> findAllByOrderByIdDesc();
}
