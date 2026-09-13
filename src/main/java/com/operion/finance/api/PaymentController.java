package com.operion.finance.api;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.authorization.RequirePermission;
import com.operion.finance.FeeService;
import com.operion.finance.FeeService.AllocationInput;
import com.operion.finance.Payment;
import com.operion.finance.PaymentAllocation;
import com.operion.finance.PaymentAllocationRepository;
import com.operion.finance.PaymentMethod;
import com.operion.finance.PaymentRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/payments")
@RequirePermission("FEE_VIEW")
public class PaymentController {

	private final FeeService feeService;
	private final PaymentRepository paymentRepository;
	private final PaymentAllocationRepository paymentAllocationRepository;
	private final AcademicYearRepository academicYearRepository;

	public PaymentController(FeeService feeService, PaymentRepository paymentRepository,
			PaymentAllocationRepository paymentAllocationRepository, AcademicYearRepository academicYearRepository) {
		this.feeService = feeService;
		this.paymentRepository = paymentRepository;
		this.paymentAllocationRepository = paymentAllocationRepository;
		this.academicYearRepository = academicYearRepository;
	}

	@PostMapping
	@RequirePermission("FEE_COLLECT")
	public PaymentResponse record(@RequestBody RecordPaymentRequest request) {
		AcademicYear academicYear = academicYearRepository.findById(request.academicYearId())
				.orElseThrow(() -> new IllegalArgumentException("No academic year with id " + request.academicYearId()));

		List<AllocationInput> allocations = request.allocations().stream()
				.map(entry -> new AllocationInput(entry.invoiceId(), entry.amount()))
				.toList();

		Payment payment = feeService.recordPayment(academicYear, request.amount(), PaymentMethod.valueOf(request.paymentMethod()),
				request.paymentDate(), request.remarks(), allocations);
		return PaymentResponse.from(payment);
	}

	@PostMapping("/{paymentId}/bounce")
	@RequirePermission("FEE_COLLECT")
	public PaymentResponse bounce(@PathVariable Long paymentId) {
		Payment payment = findPayment(paymentId);
		return PaymentResponse.from(feeService.bouncePayment(payment));
	}

	@GetMapping("/{paymentId}")
	public PaymentResponse get(@PathVariable Long paymentId) {
		return PaymentResponse.from(findPayment(paymentId));
	}

	/** A student's payment/receipt history (#133) - deduplicated across allocations, since
	 * a single payment can cover several of the student's invoices. */
	@GetMapping
	public List<PaymentResponse> list(@RequestParam Long studentEnrollmentId) {
		Map<Long, Payment> byId = new LinkedHashMap<>();
		for (PaymentAllocation allocation : paymentAllocationRepository.findByInvoice_StudentFeeAssignment_StudentEnrollmentId(studentEnrollmentId)) {
			byId.putIfAbsent(allocation.getPayment().getId(), allocation.getPayment());
		}
		return byId.values().stream()
				.sorted(Comparator.comparing(Payment::getPaymentDate).reversed())
				.map(PaymentResponse::from)
				.toList();
	}

	private Payment findPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("No payment with id " + paymentId));
	}
}
