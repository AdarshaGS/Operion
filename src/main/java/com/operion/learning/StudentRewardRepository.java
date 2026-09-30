package com.operion.learning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRewardRepository extends JpaRepository<StudentReward, Long> {

	List<StudentReward> findByStudentIdOrderByGrantedAtDescIdDesc(Long studentId);

	List<StudentReward> findByStudentIdInOrderByGrantedAtDescIdDesc(Collection<Long> studentIds);
}
