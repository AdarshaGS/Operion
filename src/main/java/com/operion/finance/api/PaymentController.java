package com.operion.finance.api;

import java.util.List;

import com.operion.finance.FeeService;
import com.operion.finance.FeeService.AllocationInput;
import com.operion.finance.Payment;
import com.operion.finance.PaymentMethod;
import com.operion.finance.PaymentRepository;
import com.operion.organisation.AcademicYear;
import com.operion.organisation.AcademicYearRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fees/payments")
public class PaymentController {

	private final FeeService feeService;
	private final PaymentRepository paymentRepository;
	private final AcademicYearRepository academicYearRepository;

	public PaymentController(FeeService feeService, PaymentRepository paymentRepository, AcademicYearRepository academicYearRepository) {
		this.feeService = feeService;
		this.paymentRepository = paymentRepository;
		this.academicYearRepository = academicYearRepository;
	}

	@PostMapping
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
	public PaymentResponse bounce(@PathVariable Long paymentId) {
		Payment payment = findPayment(paymentId);
		return PaymentResponse.from(feeService.bouncePayment(payment));
	}

	@GetMapping("/{paymentId}")
	public PaymentResponse get(@PathVariable Long paymentId) {
		return PaymentResponse.from(findPayment(paymentId));
	}

	private Payment findPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new IllegalArgumentException("No payment with id " + paymentId));
	}
}
