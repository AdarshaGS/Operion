package com.operion.student;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

	Optional<Student> findByPersonId(Long personId);

	Optional<Student> findByAdmissionNumber(String admissionNumber);
}
