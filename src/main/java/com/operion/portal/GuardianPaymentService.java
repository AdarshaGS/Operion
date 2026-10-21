package com.operion.portal;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.finance.Payment;
import com.operion.finance.PaymentAllocation;
import com.operion.finance.PaymentAllocationRepository;

/**
 * A guardian's view of their child's payments and receipts. A single payment can cover
 * siblings, so a receipt is only shown when every student on it is one of the caller's
 * own linked children - otherwise it would reveal another family's child.
 */
@Service
public class GuardianPaymentService {

	private final GuardianPortalService guardianPortalService;
	private final PaymentAllocationRepository paymentAllocationRepository;

	public GuardianPaymentService(GuardianPortalService guardianPortalService, PaymentAllocationRepository paymentAllocationRepository) {
		this.guardianPortalService = guardianPortalService;
		this.paymentAllocationRepository = paymentAllocationRepository;
	}

	public List<GuardianPaymentSummary> paymentsFor(Long userId, Long studentId) {
		Set<Long> linked = guardianPortalService.linkedStudentIds(userId);
		guardianPortalService.requireOwnChild(userId, studentId);
		Map<Long, Payment> payments = new LinkedHashMap<>();
		for (PaymentAllocation allocation : paymentAllocationRepository.findByInvoice_StudentFeeAssignment_StudentEnrollment_Student_Id(studentId)) {
			payments.putIfAbsent(allocation.getPayment().getId(), allocation.getPayment());
		}
		return payments.values().stream()
				.filter(payment -> coversOnly(payment.getId(), linked))
				.sorted(Comparator.comparing(Payment::getPaymentDate).reversed())
				.map(GuardianPaymentSummary::from)
				.toList();
	}

	/** Throws unless the payment covers {@code studentId} and only the caller's own children. */
	public void requireReceiptAccess(Long userId, Long studentId, Long paymentId) {
		Set<Long> linked = guardianPortalService.linkedStudentIds(userId);
		guardianPortalService.requireOwnChild(userId, studentId);
		List<Long> students = studentsOn(paymentId);
		if (!students.contains(studentId) || !linked.containsAll(students)) {
			throw new AuthorizationDeniedException("Receipt " + paymentId + " is not available to this guardian");
		}
	}

	private boolean coversOnly(Long paymentId, Set<Long> linked) {
		return linked.containsAll(studentsOn(paymentId));
	}

	private List<Long> studentsOn(Long paymentId) {
		return paymentAllocationRepository.findByPaymentId(paymentId).stream()
				.map(a -> a.getInvoice().getStudentFeeAssignment().getStudentEnrollment().getStudent().getId())
				.distinct()
				.toList();
	}
}
