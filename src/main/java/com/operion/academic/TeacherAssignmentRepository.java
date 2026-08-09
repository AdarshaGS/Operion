package com.operion.academic;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, Long> {

	List<TeacherAssignment> findBySectionId(Long sectionId);

	List<TeacherAssignment> findByTeacherPersonId(Long teacherPersonId);

	Optional<TeacherAssignment> findBySectionIdAndAssignmentTypeAndStatus(
			Long sectionId, TeacherAssignmentType assignmentType, TeacherAssignmentStatus status);

	Optional<TeacherAssignment> findBySectionIdAndSubjectIdAndAssignmentTypeAndStatus(
			Long sectionId, Long subjectId, TeacherAssignmentType assignmentType, TeacherAssignmentStatus status);
}
