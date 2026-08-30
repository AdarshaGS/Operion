package com.operion.student;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentApplicationRepository extends JpaRepository<StudentApplication, Long> {

	List<StudentApplication> findByStatus(StudentApplicationStatus status);
}
