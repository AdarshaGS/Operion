package com.operion.finance.api;

import java.math.BigDecimal;

import com.operion.finance.StudentFeeAssignment;

public record StudentFeeAssignmentResponse(Long id, Long studentEnrollmentId, Long feeStructureId, BigDecimal baseAmount,
		BigDecimal discountAmount, BigDecimal effectiveAmount, String discountReason, Long approvedBy, String status) {

	static StudentFeeAssignmentResponse from(StudentFeeAssignment assignment) {
		return new StudentFeeAssignmentResponse(assignment.getId(), assignment.getStudentEnrollment().getId(),
				assignment.getFeeStructure().getId(), assignment.getBaseAmount(), assignment.getDiscountAmount(),
				assignment.getEffectiveAmount(), assignment.getDiscountReason(), assignment.getApprovedBy(), assignment.getStatus().name());
	}
}
