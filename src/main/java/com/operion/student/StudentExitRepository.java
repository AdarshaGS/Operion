package com.operion.student;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentExitRepository extends JpaRepository<StudentExit, Long> {

	List<StudentExit> findByStudentId(Long studentId);
}
