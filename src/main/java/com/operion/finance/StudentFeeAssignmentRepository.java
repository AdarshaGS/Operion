package com.operion.finance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentFeeAssignmentRepository extends JpaRepository<StudentFeeAssignment, Long> {

	List<StudentFeeAssignment> findByStudentEnrollmentId(Long studentEnrollmentId);

	List<StudentFeeAssignment> findByStudentEnrollmentIdAndStatus(Long studentEnrollmentId, StudentFeeAssignmentStatus status);

	List<StudentFeeAssignment> findByFeeStructure_FeeCategory_Id(Long feeCategoryId);

	/** Candidates for bulk invoicing: every ACTIVE assignment in the year, optionally narrowed to one class. */
	@Query("""
			select a from StudentFeeAssignment a
			where a.status = com.operion.finance.StudentFeeAssignmentStatus.ACTIVE
			  and a.studentEnrollment.academicYear.id = :academicYearId
			  and (:schoolClassId is null or a.feeStructure.feeStructureGroup.schoolClass.id = :schoolClassId)
			order by a.id
			""")
	List<StudentFeeAssignment> findActiveForBulkInvoicing(@Param("academicYearId") Long academicYearId,
			@Param("schoolClassId") Long schoolClassId);
}
