package com.operion.timetable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherUnavailabilityRepository extends JpaRepository<TeacherUnavailability, Long> {

	List<TeacherUnavailability> findByTeacherPersonId(Long teacherPersonId);
}
