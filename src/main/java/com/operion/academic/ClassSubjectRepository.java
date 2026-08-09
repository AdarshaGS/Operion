package com.operion.academic;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

	List<ClassSubject> findBySchoolClassId(Long schoolClassId);
}
